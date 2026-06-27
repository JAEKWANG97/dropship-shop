package com.dropshipshop.api.payment.toss;

public interface TossPaymentsClient {

	TossApprovedPayment confirm(String paymentKey, String orderId, long amount);
}
