package com.dropshipshop.api.payment.toss;

import java.time.Instant;

import com.dropshipshop.api.payment.domain.PaymentMethod;

public record TossApprovedPayment(
	String paymentKey,
	String orderId,
	long totalAmount,
	PaymentMethod method,
	Instant approvedAt,
	String rawStatus
) {
}
