package com.dropshipshop.api.procurement;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class DomeggookPurchaseClient {

	private final DomeggookProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;
	private volatile String sessionId;

	@Autowired
	DomeggookPurchaseClient(DomeggookProperties properties, ObjectMapper objectMapper) {
		this(properties, objectMapper, restClient(properties.endpoint()));
	}

	DomeggookPurchaseClient(DomeggookProperties properties, ObjectMapper objectMapper, RestClient restClient) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = restClient;
	}

	ProductQuote quote(String itemNo, String optionCode) {
		properties.requireConfigured();
		JsonNode root = get(form(
			"ver", "4.6",
			"mode", "getItemView",
			"aid", properties.apiKey(),
			"no", itemNo,
			"market", "supply",
			"om", "json"
		), false);
		JsonNode detail = domeggook(root);
		boolean onSale = "판매중".equals(text(detail.path("basis").path("status")));
		long basePrice = number(detail.path("price").path("supply"));
		OptionQuote option = option(detail.path("selectOpt"), optionCode);
		JsonNode quantity = detail.path("qty");
		long orderUnit = number(quantity.path("supplyUnit"));
		long maximumOrderQuantity = number(quantity.path("supplyLoq"));
		long stockQuantity = number(quantity.path("inventory"));
		JsonNode shipping = detail.path("deli").path("supply");
		String shippingText = "%s %s".formatted(text(shipping.path("pay")), text(shipping.path("type")));
		boolean conditionalShipping = shippingText.matches(".*(수량|차등|비례|착불|구매자선택).*");
		long shippingFee = number(shipping.path("fee"));
		return new ProductQuote(
			onSale,
			option.available(),
			basePrice + option.additionalPrice(),
			shippingFee,
			conditionalShipping,
			orderUnit,
			maximumOrderQuantity,
			stockQuantity
		);
	}

	long emoneyBalance() {
		JsonNode data = domeggook(privatePost("1.0", "getMyAsset", new LinkedMultiValueMap<>())).path("data");
		return number(data.path("currEmoney").path("total"));
	}

	OrderResult placeOrder(OrderRequest request) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("receipt", "0");
		form.add("market", "supply");
		form.add("notify", "false");
		form.add("alliance", "CoreableSAF");
		form.add("deliinfo", deliveryInfo(request));
		for (OrderLine line : request.lines()) {
			form.add("item[%s]".formatted(line.itemNo()), orderLine(line, request.orderNumber()));
		}
		JsonNode body;
		try {
			body = privatePost("4.3", "setOrder", form);
		} catch (DomeggookApiException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new DomeggookApiException("REQUEST_FAILED", "Domeggook order request failed", true);
		}
		JsonNode root = domeggook(body);
		if (!"SUCCESS".equalsIgnoreCase(text(root.path("result")))) {
			throw apiError(root, false);
		}
		List<String> orderNumbers = new ArrayList<>();
		for (JsonNode order : array(root.path("order"))) {
			String orderNo = text(order.path("orderNo"));
			if (!orderNo.isBlank()) orderNumbers.add(orderNo);
		}
		if (orderNumbers.isEmpty()) {
			throw new DomeggookApiException("ORDER_NUMBER_MISSING", "Domeggook order response has no order number", true);
		}
		long actualAmount = orderNumbers.stream().mapToLong(this::orderAmount).sum();
		return new OrderResult(List.copyOf(orderNumbers), actualAmount);
	}

	OrderView orderView(String orderNumber) {
		JsonNode items = domeggook(privateGet("4.1", "getOrderView", form(
			"for", "buy",
			"no", orderNumber
		))).path("items");
		JsonNode item = array(items).stream().findFirst()
			.orElseThrow(() -> new DomeggookApiException("ORDER_NOT_FOUND", "Domeggook order response has no item", false));
		JsonNode delivery = item.path("delivery");
		JsonNode product = item.path("item");
		String responseOrderNumber = firstText(item, "orderNo", "no");
		return new OrderView(
			normalizeOrderNumber(responseOrderNumber.isBlank() ? orderNumber : responseOrderNumber),
			text(item.path("status")),
			number(item.path("orderAmtPay")),
			firstText(delivery, "companyName", "company"),
			firstText(delivery, "code", "invoiceNo", "trackingNo"),
			text(item.path("orderMemo")),
			firstText(product, "no", "itemNo")
		);
	}

	String cancel(String orderNumber, String reason) {
		JsonNode root = domeggook(privatePost("1.0", "setOrdDeny", form(
			"type", "buy",
			"no", orderNumber,
			"memo", clean(reason)
		)));
		String result = text(root.path("result"));
		if (!List.of("true", "complete", "req").contains(result)) {
			throw apiError(root, false);
		}
		return result;
	}

	List<PurchaseListItem> recentOrders() {
		JsonNode root = domeggook(privateGet("4.0", "getOrderList", form(
			"for", "buy",
			"day", "1",
			"pg", "1",
			"ic", "100"
		)));
		JsonNode items = root.path("list").path("item");
		if (items.isMissingNode()) items = root.path("items");
		List<PurchaseListItem> result = new ArrayList<>();
		for (JsonNode item : array(items)) {
			result.add(new PurchaseListItem(
				normalizeOrderNumber(firstText(item, "orderNo", "no")),
				firstText(item, "itemNo"),
				firstText(item, "status")
			));
		}
		return List.copyOf(result);
	}

	private long orderAmount(String orderNumber) {
		return orderView(orderNumber).paidAmount();
	}

	private JsonNode privateGet(String version, String mode, MultiValueMap<String, String> values) {
		return withSession(session -> {
			MultiValueMap<String, String> request = privateForm(version, mode, session);
			request.addAll(values);
			return get(request, true);
		});
	}

	private JsonNode privatePost(String version, String mode, MultiValueMap<String, String> values) {
		return withSession(session -> {
			MultiValueMap<String, String> request = privateForm(version, mode, session);
			request.addAll(values);
			return post(request, "setOrder".equals(mode));
		});
	}

	private JsonNode withSession(SessionCall call) {
		properties.requireConfigured();
		String current = session();
		try {
			return call.execute(current);
		} catch (DomeggookApiException exception) {
			if (!"AUTH_FAILED".equals(exception.code())) throw exception;
			synchronized (this) {
				sessionId = null;
				current = session();
			}
			return call.execute(current);
		}
	}

	private String session() {
		if (sessionId != null) return sessionId;
		synchronized (this) {
			if (sessionId != null) return sessionId;
			JsonNode root = domeggook(post(form(
				"ver", "4.1",
				"mode", "setLogin",
				"aid", properties.apiKey(),
				"id", properties.userId(),
				"pw", properties.password(),
				"om", "json",
				"loginKeep", "on",
				"userAgent", "CoreableSAF",
				"ip", properties.clientIp(),
				"device", "Third Party"
			), false));
			String value = text(root.path("sId"));
			if (value.isBlank()) throw apiError(root, false);
			sessionId = value;
			return value;
		}
	}

	private MultiValueMap<String, String> privateForm(String version, String mode, String session) {
		return form(
			"ver", version,
			"mode", mode,
			"aid", properties.apiKey(),
			"id", properties.userId(),
			"sId", session,
			"ie", "utf-8",
			"oe", "utf-8",
			"om", "json"
		);
	}

	private JsonNode get(MultiValueMap<String, String> values, boolean authenticated) {
		try {
			String body = restClient.get()
				.uri(builder -> {
					builder.path("");
					values.forEach((key, entries) -> entries.forEach(value -> builder.queryParam(key, value)));
					return builder.build();
				})
				.retrieve()
				.body(String.class);
			return parse(body, authenticated);
		} catch (RestClientException exception) {
			throw transport(exception, false);
		}
	}

	private JsonNode post(MultiValueMap<String, String> values, boolean outcomeUnknown) {
		try {
			String body = restClient.post()
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(values)
				.retrieve()
				.body(String.class);
			return parse(body, true);
		} catch (RestClientException exception) {
			throw transport(exception, outcomeUnknown);
		}
	}

	private JsonNode parse(String body, boolean authenticated) {
		try {
			JsonNode root = objectMapper.readTree(body);
			JsonNode error = root.path("errors");
			if (error.isMissingNode() || error.isNull()) error = root.path("domeggook").path("error");
			if (!error.isMissingNode() && !error.isNull()) {
				String code = firstText(error, "dcode", "code");
				if (authenticated && code.toLowerCase().contains("login")) code = "AUTH_FAILED";
				throw new DomeggookApiException(code.isBlank() ? "API_ERROR" : code, firstText(error, "dmessage", "message"), false);
			}
			return root;
		} catch (DomeggookApiException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new DomeggookApiException("INVALID_RESPONSE", "Domeggook returned an invalid response", false);
		}
	}

	private DomeggookApiException transport(RestClientException exception, boolean outcomeUnknown) {
		boolean timeout = exception.getCause() instanceof SocketTimeoutException;
		return new DomeggookApiException(
			timeout ? "TIMEOUT" : "TRANSPORT_ERROR",
			timeout ? "Domeggook request timed out" : "Domeggook request failed",
			outcomeUnknown
		);
	}

	private DomeggookApiException apiError(JsonNode root, boolean outcomeUnknown) {
		String code = firstText(root.path("error"), "dcode", "code", "result");
		String message = firstText(root.path("error"), "dmessage", "message", "msg");
		if (code.isBlank()) code = firstText(root, "dcode", "code", "result");
		if (message.isBlank()) message = firstText(root, "dmessage", "message", "msg");
		return new DomeggookApiException(
			code.isBlank() ? "API_ERROR" : code,
			message.isBlank() ? "Domeggook API request failed" : message,
			outcomeUnknown
		);
	}

	private OptionQuote option(JsonNode selectOpt, String optionCode) {
		if (selectOpt.isMissingNode() || selectOpt.isNull() || text(selectOpt).isBlank()) {
			return new OptionQuote(optionCode == null || optionCode.isBlank() || "00".equals(optionCode), 0);
		}
		try {
			JsonNode parsed = selectOpt.isTextual() ? objectMapper.readTree(selectOpt.asText()) : selectOpt;
			JsonNode value = parsed.path("data").path(optionCode == null ? "" : optionCode);
			long stock = number(value.path("qty"));
			boolean available = !value.isMissingNode()
				&& !"0".equals(text(value.path("sup")))
				&& !"2".equals(text(value.path("hid")))
				&& (value.path("qty").isMissingNode() || stock > 0);
			return new OptionQuote(available, number(value.path("supPrice")));
		} catch (Exception exception) {
			throw new DomeggookApiException("OPTION_PARSE_FAILED", "Domeggook option response could not be parsed", false);
		}
	}

	private String deliveryInfo(OrderRequest request) {
		return String.join("|",
			clean(request.recipientName()),
			clean(request.email()),
			clean(request.postalCode()),
			clean(request.address1()),
			clean(request.address2()),
			deliveryPhone(request.recipientPhone()),
			"",
			"코어블SAF"
		);
	}

	private String deliveryPhone(String value) {
		String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
		if (!digits.matches("01[016789][0-9]{7,8}")) {
			throw new DomeggookApiException("ORDER_CONSUMER_MOBILE_ERROR", "Recipient phone number is invalid", false);
		}
		int middleEnd = digits.length() - 4;
		return digits.substring(0, 3) + "-" + digits.substring(3, middleEnd) + "-" + digits.substring(middleEnd);
	}

	private String orderLine(OrderLine line, String orderNumber) {
		String optionCode = line.optionCode() == null || line.optionCode().isBlank() ? "" : line.optionCode();
		return "supply||P||%s|%d||%s||%s".formatted(optionCode, line.quantity(), clean(orderNumber), clean(orderNumber));
	}

	private String clean(String value) {
		return value == null ? "" : value.replace("|", " ").replaceAll("[\\r\\n]+", " ").trim();
	}

	private String normalizeOrderNumber(String value) {
		return value != null && value.startsWith("OR") ? value.substring(2) : value;
	}

	private long number(JsonNode node) {
		String value = text(node).replace(",", "").replaceAll("[^0-9-]", "");
		return value.isBlank() || "-".equals(value) ? 0 : Long.parseLong(value);
	}

	private String text(JsonNode node) {
		return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("").trim();
	}

	private String firstText(JsonNode node, String... names) {
		for (String name : names) {
			String value = text(node.path(name));
			if (!value.isBlank()) return value;
		}
		return "";
	}

	private List<JsonNode> array(JsonNode node) {
		if (node.isArray()) {
			List<JsonNode> values = new ArrayList<>();
			node.forEach(values::add);
			return values;
		}
		return node.isMissingNode() || node.isNull() ? List.of() : List.of(node);
	}

	private JsonNode domeggook(JsonNode root) {
		JsonNode value = root.path("domeggook");
		return value.isMissingNode() ? root : value;
	}

	private static MultiValueMap<String, String> form(String... values) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		for (int index = 0; index < values.length; index += 2) form.add(values[index], values[index + 1]);
		return form;
	}

	private static RestClient restClient(String endpoint) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(15));
		return RestClient.builder().baseUrl(endpoint).requestFactory(factory).build();
	}

	private interface SessionCall {
		JsonNode execute(String session);
	}

	record ProductQuote(
		boolean onSale,
		boolean optionAvailable,
		long sourceUnitPrice,
		long shippingFee,
		boolean conditionalShipping,
		long orderUnit,
		long maximumOrderQuantity,
		long stockQuantity
	) {
		boolean acceptsOrderQuantity(int quantity) {
			return orderUnit > 0
				&& quantity % orderUnit == 0
				&& (maximumOrderQuantity <= 0 || quantity <= maximumOrderQuantity);
		}

		boolean hasStock(int quantity) {
			return stockQuantity >= quantity;
		}
	}

	record OrderLine(String itemNo, String optionCode, int quantity) {
	}

	record OrderRequest(
		String orderNumber,
		String recipientName,
		String recipientPhone,
		String postalCode,
		String address1,
		String address2,
		String email,
		List<OrderLine> lines
	) {
	}

	record OrderResult(List<String> orderNumbers, long actualAmount) {
	}

	record OrderView(
		String orderNumber,
		String status,
		long paidAmount,
		String carrier,
		String trackingNumber,
		String orderMemo,
		String itemNo
	) {
	}

	record PurchaseListItem(String orderNumber, String itemNo, String status) {
	}

	private record OptionQuote(boolean available, long additionalPrice) {
	}
}
