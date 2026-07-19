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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public final class RefundDtos {

	private RefundDtos() {
	}

	public record AdminRefundListResponse(
		List<AdminRefundListItemResponse> refunds
	) {
	}

	public record AdminRefundListItemResponse(
		UUID refundId,
		UUID orderId,
		String orderNumber,
		OrderStatus orderStatus,
		RefundReason reason,
		RefundStatus status,
		long refundAmount,
		RefundScope refundScope,
		Instant requestedAt,
		Instant completedAt,
		Instant createdAt
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
		UUID reviewedByAdminId,
		String adminReviewReason,
		Instant reviewedAt,
		UUID manualRefundedByAdminId,
		Instant manualRefundedAt,
		String manualRefundReason,
		String manualRefundBankName,
		String manualRefundAccountNumber,
		String manualRefundAccountHolder,
		Instant manualRefundTransferredAt,
		String manualRefundTransactionReference,
		Instant requestedAt,
		Instant completedAt,
		Instant failedAt,
		Instant createdAt
	) {
	}

	public record RefundApprovalRequest(
		@NotBlank
		@Size(max = 1000)
		String reason
	) {
	}

	public record RefundManualReviewRequest(
		@NotNull
		RefundStatus status,

		@NotBlank
		@Size(max = 1000)
		String reason
	) {
	}

	public record ManualBankTransferRefundCompleteRequest(
		@NotBlank
		@Size(max = 1000)
		String reason,

		@NotBlank
		@Size(max = 100)
		String bankName,

		@NotBlank
		@Size(max = 100)
		String accountNumber,

		@NotBlank
		@Size(max = 100)
		String accountHolder,

		@NotNull
		@PastOrPresent
		Instant transferredAt,

		@NotBlank
		@Size(max = 200)
		String transactionReference
	) {
	}
}
