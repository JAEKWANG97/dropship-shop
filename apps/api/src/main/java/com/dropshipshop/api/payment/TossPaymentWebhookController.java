package com.dropshipshop.api.payment;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/payments/toss")
class TossPaymentWebhookController {

	private static final String TRANSMISSION_ID_HEADER = "TossPayments-Webhook-Transmission-Id";

	private final PaymentWebhookService paymentWebhookService;

	TossPaymentWebhookController(PaymentWebhookService paymentWebhookService) {
		this.paymentWebhookService = paymentWebhookService;
	}

	@PostMapping("/webhook")
	ResponseEntity<Void> receiveWebhook(
		@RequestBody JsonNode payload,
		@RequestHeader HttpHeaders headers
	) {
		paymentWebhookService.handleTossWebhook(payload, headers.getFirst(TRANSMISSION_ID_HEADER));
		return ResponseEntity.accepted().build();
	}
}
