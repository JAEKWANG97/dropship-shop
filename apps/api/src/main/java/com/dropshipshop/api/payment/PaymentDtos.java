package com.dropshipshop.api.payment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentStatus;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

final class PaymentDtos {

	private PaymentDtos() {
	}

	record TossConfirmRequest(
		@NotBlank String checkoutNumber,
		@NotBlank String paymentKey,
		@Min(0) long amount
	) {
	}

	record PaymentConfirmResponse(
		UUID paymentId,
		String checkoutNumber,
		PaymentStatus paymentStatus,
		PaymentGroupStatus paymentGroupStatus,
		long approvedAmount,
		Instant approvedAt,
		List<OrderStatusResponse> orders
	) {
	}

	record OrderStatusResponse(
		UUID orderId,
		String orderNumber,
		OrderStatus status
	) {
	}
}
