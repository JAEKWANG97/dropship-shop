package com.dropshipshop.api.payment.domain;

public enum PaymentGroupStatus {
	PAYMENT_PENDING,
	APPROVED,
	PARTIALLY_REFUNDED,
	REFUNDED,
	PAYMENT_EXCEPTION,
	EXPIRED,
	CANCELLED,
	CANCEL_FAILED
}
