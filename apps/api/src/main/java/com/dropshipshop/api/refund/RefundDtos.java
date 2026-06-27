package com.dropshipshop.api.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentStatus;
import com.dropshipshop.api.refund.domain.RefundReason;
import com.dropshipshop.api.refund.domain.RefundScope;
import com.dropshipshop.api.refund.domain.RefundStatus;

public final class RefundDtos {

	private RefundDtos() {
	}

	public record AdminRefundListResponse(
		List<AdminRefundResponse> refunds
	) {
	}

	public record AdminRefundResponse(
		UUID refundId,
		UUID orderId,
		String orderNumber,
		OrderStatus orderStatus,
		UUID paymentGroupId,
		PaymentGroupStatus paymentGroupStatus,
		UUID paymentId,
		PaymentStatus paymentStatus,
		RefundReason reason,
		RefundStatus status,
		long refundAmount,
		RefundScope refundScope,
		String providerPaymentKey,
		String providerCancelTransactionKey,
		String idempotencyKey,
		String failureCode,
		String failureMessage,
		String rawProviderStatus,
		Instant requestedAt,
		Instant completedAt,
		Instant failedAt,
		Instant createdAt
	) {
	}
}
