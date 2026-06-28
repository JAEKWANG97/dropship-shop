package com.dropshipshop.api.payment;

import java.time.Instant;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentEvent;
import com.dropshipshop.api.payment.domain.PaymentEventType;
import com.dropshipshop.api.payment.domain.PaymentStatus;
import com.dropshipshop.api.payment.repository.PaymentEventRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.payment.toss.TossPaymentSnapshot;
import com.dropshipshop.api.payment.toss.TossPaymentsClient;
import tools.jackson.databind.JsonNode;

@Service
public class PaymentWebhookService {

	private final PaymentRepository paymentRepository;
	private final PaymentEventRepository paymentEventRepository;
	private final TossPaymentsClient tossPaymentsClient;

	PaymentWebhookService(
		PaymentRepository paymentRepository,
		PaymentEventRepository paymentEventRepository,
		TossPaymentsClient tossPaymentsClient
	) {
		this.paymentRepository = paymentRepository;
		this.paymentEventRepository = paymentEventRepository;
		this.tossPaymentsClient = tossPaymentsClient;
	}

	@Transactional
	public void handleTossWebhook(JsonNode payload, String transmissionId) {
		String paymentKey = paymentKey(payload);
		String eventType = text(payload.path("eventType"));
		String webhookStatus = text(payload.path("data").path("status"));
		String idempotencyKey = idempotencyKey(transmissionId, eventType, paymentKey, text(payload.path("createdAt")));

		if (paymentEventRepository.existsByIdempotencyKey(idempotencyKey)) {
			return;
		}

		TossPaymentSnapshot verifiedPayment = tossPaymentsClient.getPayment(paymentKey);
		if (webhookStatus != null && !webhookStatus.equals(verifiedPayment.rawStatus())) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Toss webhook payload does not match verified payment status"
			);
		}

		paymentRepository.findByProviderPaymentKey(paymentKey)
			.ifPresent(payment -> handleKnownPayment(payment, payload, eventType, verifiedPayment, idempotencyKey));
	}

	private void handleKnownPayment(
		Payment payment,
		JsonNode payload,
		String eventType,
		TossPaymentSnapshot verifiedPayment,
		String idempotencyKey
	) {
		Instant now = Instant.now();
		String rawPayload = payload.toString();
		String resultMessage = "eventType=" + eventType + ", status=" + verifiedPayment.rawStatus();
		paymentEventRepository.save(new PaymentEvent(
			payment,
			payment.getPaymentGroup(),
			payment.getProviderPaymentKey(),
			PaymentEventType.TOSS_WEBHOOK_RECEIVED,
			idempotencyKey,
			rawPayload,
			resultMessage,
			now
		));

		if (conflictsWithLocalState(payment.getStatus(), verifiedPayment.rawStatus())) {
			payment.markReviewRequired(
				"WEBHOOK_STATUS_CONFLICT",
				"Local payment status " + payment.getStatus() + " conflicts with Toss status " + verifiedPayment.rawStatus()
			);
			paymentEventRepository.save(new PaymentEvent(
				payment,
				payment.getPaymentGroup(),
				payment.getProviderPaymentKey(),
				PaymentEventType.PAYMENT_REVIEW_REQUIRED,
				null,
				rawPayload,
				"Webhook status conflict requires admin review",
				now
			));
		}
	}

	private boolean conflictsWithLocalState(PaymentStatus paymentStatus, String tossStatus) {
		String normalized = tossStatus == null ? "" : tossStatus.toUpperCase(Locale.ROOT);
		return switch (normalized) {
			case "DONE" -> paymentStatus != PaymentStatus.APPROVED;
			case "CANCELED" -> paymentStatus != PaymentStatus.CANCELLED && paymentStatus != PaymentStatus.REFUNDED;
			case "PARTIAL_CANCELED" -> paymentStatus != PaymentStatus.PARTIALLY_REFUNDED;
			default -> false;
		};
	}

	private String paymentKey(JsonNode payload) {
		String paymentKey = text(payload.path("data").path("paymentKey"));
		if (paymentKey == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Toss webhook paymentKey is required");
		}
		return paymentKey;
	}

	private String idempotencyKey(String transmissionId, String eventType, String paymentKey, String createdAt) {
		if (transmissionId != null && !transmissionId.isBlank()) {
			return "toss-webhook:" + transmissionId;
		}
		return "toss-webhook:" + eventType + ":" + paymentKey + ":" + createdAt;
	}

	private String text(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		String value = node.asText();
		return value == null || value.isBlank() ? null : value;
	}
}
