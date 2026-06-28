package com.dropshipshop.api.payment.toss;

public record TossPaymentSnapshot(
	String paymentKey,
	String orderId,
	long totalAmount,
	String rawStatus
) {
}
