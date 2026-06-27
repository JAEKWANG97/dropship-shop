package com.dropshipshop.api.payment.toss;

public record TossCancelledPayment(
	String paymentKey,
	String orderId,
	long totalAmount,
	long balanceAmount,
	String cancelTransactionKey,
	String rawStatus
) {
}
