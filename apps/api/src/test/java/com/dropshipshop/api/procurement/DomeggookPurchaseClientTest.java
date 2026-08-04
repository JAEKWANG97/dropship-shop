package com.dropshipshop.api.procurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.ObjectMapper;

class DomeggookPurchaseClientTest {

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) server.stop(0);
	}

	@Test
	void readsSupplyOrderUnitAndStockFromProductQuote() throws IOException {
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/", exchange -> {
			byte[] body = """
				{"domeggook":{"basis":{"status":"판매중"},"price":{"supply":450},
				"qty":{"inventory":"197035","domeMoq":"12","supplyUnit":1,"supplyLoq":10},
				"selectOpt":"","deli":{"supply":{"type":"고정배송비","fee":"3000"}}}}
				""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();

		String endpoint = "http://localhost:" + server.getAddress().getPort();
		DomeggookProperties properties = new DomeggookProperties(true, false, "key", "user", "password", "127.0.0.1", endpoint);
		DomeggookPurchaseClient client = new DomeggookPurchaseClient(
			properties,
			new ObjectMapper(),
			RestClient.builder().baseUrl(endpoint).build()
		);

		DomeggookPurchaseClient.ProductQuote quote = client.quote("63511465", "01");

		assertThat(quote.orderUnit()).isEqualTo(1);
		assertThat(quote.maximumOrderQuantity()).isEqualTo(10);
		assertThat(quote.stockQuantity()).isEqualTo(197035);
		assertThat(quote.acceptsOrderQuantity(1)).isTrue();
		assertThat(quote.acceptsOrderQuantity(11)).isFalse();
		assertThat(quote.hasStock(197036)).isFalse();
	}

	@Test
	void handlesPrivateApiOrderLifecycleFixtures() throws IOException {
		AtomicReference<Map<String, String>> orderForm = new AtomicReference<>();
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/", exchange -> {
			String input = "POST".equals(exchange.getRequestMethod())
				? new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
				: exchange.getRequestURI().getRawQuery();
			Map<String, String> form = Arrays.stream(input.split("&"))
				.map(value -> value.split("=", 2))
				.collect(Collectors.toMap(
					value -> URLDecoder.decode(value[0], StandardCharsets.UTF_8),
					value -> value.length == 1 ? "" : URLDecoder.decode(value[1], StandardCharsets.UTF_8)
				));
			String mode = form.get("mode");
			String response;
			if ("setLogin".equals(mode)) {
				response = "{\"domeggook\":{\"sId\":\"session\"}}";
			} else if ("getMyAsset".equals(mode)) {
				response = "{\"domeggook\":{\"data\":{\"currEmoney\":{\"total\":\"10000\"}}}}";
			} else if ("setOrder".equals(mode)) {
				orderForm.set(form);
				response = "{\"domeggook\":{\"result\":\"SUCCESS\",\"order\":{\"orderNo\":\"12345\"}}}";
			} else if ("getOrderList".equals(mode)) {
				response = "{\"domeggook\":{\"items\":[{\"orderNo\":\"OR12345\",\"itemNo\":\"63511465\",\"status\":\"배송중\"}]}}";
			} else if ("setOrdDeny".equals(mode)) {
				response = "{\"domeggook\":{\"result\":\"complete\"}}";
			} else {
				response = "{\"domeggook\":{\"items\":[{\"orderNo\":\"OR12345\",\"status\":\"배송중\",\"orderAmtPay\":\"3450\",\"orderMemo\":\"OD-TEST\",\"item\":{\"no\":\"63511465\"},\"delivery\":{\"companyName\":\"테스트택배\",\"code\":\"TRACK-1\"}}]}}";
			}
			byte[] body = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();

		String endpoint = "http://localhost:" + server.getAddress().getPort();
		DomeggookPurchaseClient client = new DomeggookPurchaseClient(
			new DomeggookProperties(true, false, "key", "user", "password", "127.0.0.1", endpoint),
			new ObjectMapper(),
			RestClient.builder().baseUrl(endpoint).build()
		);

		DomeggookPurchaseClient.OrderResult result = client.placeOrder(new DomeggookPurchaseClient.OrderRequest(
			"OD-TEST", "홍길동", "01012345678", "12345", "서울시", "상세주소", "test@example.com",
			java.util.List.of(new DomeggookPurchaseClient.OrderLine("63511465", "01", 1))
		));

		assertThat(result.actualAmount()).isEqualTo(3450);
		assertThat(orderForm.get()).containsEntry("ie", "utf-8").containsEntry("oe", "utf-8")
			.containsEntry("notify", "false").containsEntry("alliance", "CoreableSAF")
			.containsEntry("item[63511465]", "supply||P||01|1||OD-TEST||OD-TEST");
		assertThat(orderForm.get().get("deliinfo")).contains("|010-1234-5678||코어블SAF");
		assertThat(client.emoneyBalance()).isEqualTo(10000);
		assertThat(client.recentOrders()).containsExactly(
			new DomeggookPurchaseClient.PurchaseListItem("12345", "63511465", "배송중")
		);
		assertThat(client.orderView("12345")).isEqualTo(new DomeggookPurchaseClient.OrderView(
			"12345", "배송중", 3450, "테스트택배", "TRACK-1", "OD-TEST", "63511465"
		));
		assertThat(client.cancel("12345", "테스트 취소")).isEqualTo("complete");
	}
}
