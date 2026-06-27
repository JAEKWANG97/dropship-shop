package com.dropshipshop.api.payment.toss;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.dropshipshop.api.payment.domain.PaymentMethod;

@Component
class TossPaymentsRestClient implements TossPaymentsClient {

	private final String secretKey;
	private final RestClient restClient;

	TossPaymentsRestClient(
		@Value("${payments.toss.secret-key:}") String secretKey,
		@Value("${payments.toss.base-url:https://api.tosspayments.com}") String baseUrl
	) {
		this.secretKey = secretKey;
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
	}

	@Override
	public TossApprovedPayment confirm(String paymentKey, String orderId, long amount) {
		if (secretKey == null || secretKey.isBlank()) {
			throw new TossPaymentException("Toss Payments secret key is not configured");
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = restClient.post()
				.uri("/v1/payments/confirm")
				.header(HttpHeaders.AUTHORIZATION, authorizationHeader())
				.body(Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount))
				.retrieve()
				.body(Map.class);
			if (body == null) {
				throw new TossPaymentException("Toss Payments confirm response is empty");
			}
			return new TossApprovedPayment(
				stringValue(body.get("paymentKey")),
				stringValue(body.get("orderId")),
				longValue(body.get("totalAmount")),
				paymentMethod(stringValue(body.get("method"))),
				instantValue(body.get("approvedAt")),
				stringValue(body.get("status"))
			);
		} catch (TossPaymentException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new TossPaymentException("Toss Payments confirm failed: " + exception.getMessage());
		}
	}

	@Override
	public TossCancelledPayment cancel(String paymentKey, String cancelReason, long cancelAmount, String idempotencyKey) {
		if (secretKey == null || secretKey.isBlank()) {
			throw new TossPaymentException("Toss Payments secret key is not configured");
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> body = restClient.post()
				.uri("/v1/payments/{paymentKey}/cancel", paymentKey)
				.header(HttpHeaders.AUTHORIZATION, authorizationHeader())
				.header("Idempotency-Key", idempotencyKey)
				.body(Map.of("cancelReason", cancelReason, "cancelAmount", cancelAmount))
				.retrieve()
				.body(Map.class);
			if (body == null) {
				throw new TossPaymentException("Toss Payments cancel response is empty");
			}
			return new TossCancelledPayment(
				stringValue(body.get("paymentKey")),
				stringValue(body.get("orderId")),
				longValue(body.get("totalAmount")),
				longValue(body.get("balanceAmount")),
				cancelTransactionKey(body.get("cancels")),
				stringValue(body.get("status"))
			);
		} catch (TossPaymentException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw new TossPaymentException("Toss Payments cancel failed: " + exception.getMessage());
		}
	}

	private String authorizationHeader() {
		String credential = secretKey + ":";
		String encoded = Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
		return "Basic " + encoded;
	}

	private String stringValue(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private long longValue(Object value) {
		if (value instanceof Number number) {
			return number.longValue();
		}
		return Long.parseLong(String.valueOf(value));
	}

	private Instant instantValue(Object value) {
		return value == null ? Instant.now() : Instant.parse(String.valueOf(value));
	}

	private PaymentMethod paymentMethod(String value) {
		String normalized = value == null ? "" : value.toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "카드", "CARD" -> PaymentMethod.CARD;
			case "간편결제", "EASY_PAY", "EASYPAY" -> PaymentMethod.EASY_PAY;
			case "계좌이체", "TRANSFER" -> PaymentMethod.TRANSFER;
			default -> throw new TossPaymentException("Unsupported Toss Payments method: " + value);
		};
	}

	@SuppressWarnings("unchecked")
	private String cancelTransactionKey(Object value) {
		if (!(value instanceof Iterable<?> cancels)) {
			return null;
		}
		String transactionKey = null;
		for (Object cancel : cancels) {
			if (cancel instanceof Map<?, ?> cancelMap) {
				transactionKey = stringValue(cancelMap.get("transactionKey"));
			}
		}
		return transactionKey;
	}
}
