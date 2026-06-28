package com.dropshipshop.api.payment.toss;

public interface TossPaymentsClient {

	TossApprovedPayment confirm(String paymentKey, String orderId, long amount);

	TossCancelledPayment cancel(String paymentKey, String cancelReason, long cancelAmount, String idempotencyKey);

	TossPaymentSnapshot getPayment(String paymentKey);
}
