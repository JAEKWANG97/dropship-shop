package com.dropshipshop.api.procurement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

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
}
