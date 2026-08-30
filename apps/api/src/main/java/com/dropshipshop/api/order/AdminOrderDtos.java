package com.dropshipshop.api.order;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.claim.domain.ClaimReason;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.domain.ClaimType;
import com.dropshipshop.api.claim.domain.RequestedAction;
import com.dropshipshop.api.fulfillment.domain.FulfillmentStatus;
import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;
import com.dropshipshop.api.fulfillment.domain.SupplierPurchaseStatus;
import com.dropshipshop.api.order.domain.AdminOrderActionType;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentExceptionReason;
import com.dropshipshop.api.payment.domain.PaymentMethod;
import com.dropshipshop.api.payment.domain.PaymentProvider;
import com.dropshipshop.api.payment.domain.PaymentStatus;
import com.dropshipshop.api.refund.domain.RefundReason;
import com.dropshipshop.api.refund.domain.RefundScope;
import com.dropshipshop.api.refund.domain.RefundStatus;
import com.dropshipshop.api.shipment.domain.ShipmentStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

final class AdminOrderDtos {

	private AdminOrderDtos() {
	}

	record AdminOrderListResponse(
		List<AdminOrderSummaryResponse> orders,
		int page,
		int size,
		long totalElements,
		int totalPages
	) {
	}

	record AdminOrderSummaryResponse(
		UUID orderId,
		String orderNumber,
		OrderStatus status,
		UUID supplierId,
		String supplierName,
		UUID customerId,
		String customerEmail,
		String checkoutNumber,
		long itemCount,
		long totalAmount,
		Instant createdAt
	) {
	}

	record AdminOrderDetailResponse(
		UUID orderId,
		String orderNumber,
		OrderStatus status,
		Instant createdAt,
		SupplierResponse supplier,
		CustomerResponse customer,
		AdminShippingAddressResponse shippingAddress,
		AdminPaymentGroupResponse paymentGroup,
		AdminPaymentResponse payment,
		AdminFulfillmentResponse fulfillment,
		AdminShipmentResponse shipment,
		AdminRefundResponse refund,
		AdminClaimResponse claim,
		List<AdminOrderItemResponse> items
	) {
	}

	record SupplierResponse(
		UUID supplierId,
		String name,
		String contactName,
		String phone,
		String email
	) {
	}

	record CustomerResponse(
		UUID customerId,
		String email,
		String displayName
	) {
	}

	record AdminShippingAddressResponse(
		String recipientName,
		String recipientPhone,
		String postalCode,
		String address1,
		String address2,
		String deliveryMemo
	) {
	}

	record AdminPaymentGroupResponse(
		UUID paymentGroupId,
		String checkoutNumber,
		PaymentGroupStatus status,
		long totalAmount,
		Long approvedAmount,
		Instant approvedAt,
		AdminBankTransferDepositResponse bankTransferDeposit
	) {
	}

	record AdminBankTransferDepositResponse(
		String bankName,
		String accountNumber,
		String accountHolder,
		String depositorName,
		String cashReceiptNotice,
		UUID depositConfirmedByAdminId,
		Instant depositConfirmedAt,
		String depositConfirmationReason,
		String actualDepositorName,
		Long actualDepositAmount,
		Instant depositReceivedAt,
		String depositTransactionReference,
		String depositMismatchMemo,
		UUID depositMismatchRecordedByAdminId,
		Instant depositMismatchRecordedAt,
		UUID unpaidCancelledByAdminId,
		Instant unpaidCancelledAt,
		String unpaidCancelReason
	) {
	}

	record AdminPaymentResponse(
		UUID paymentId,
		PaymentProvider provider,
		PaymentStatus status,
		PaymentMethod method,
		long requestedAmount,
		Long approvedAmount,
		Instant approvedAt
	) {
	}

	record AdminFulfillmentResponse(
		UUID fulfillmentId,
		FulfillmentStatus status,
		FulfillmentChannel channel,
		Instant requestedAt,
		FulfillmentOperationalOwner operationalOwner,
		Instant piiAccessCutoffAt,
		Instant handedOverAt,
		String handedOverReason,
		UUID handedOverByAdminId,
		Instant supplierOrderStartedAt,
		Instant addressLockedAt,
		UUID addressLockedByAdminId,
		String supplierOrderNumber,
		UUID orderedByAdminId,
		Instant orderedAt,
		LocalDate expectedShipDate,
		String supplierResponseMemo,
		String outOfStockReason,
		String purchaseProvider,
		SupplierPurchaseStatus purchaseStatus,
		Long expectedSourceAmount,
		Long actualSourceAmount,
		String lastPurchaseError,
		Instant purchaseSyncedAt,
		String supplierCancelStatus
	) {
	}

	record SupplierPurchaseValidationResponse(
		long expectedAmount,
		long itemAmount,
		long shippingAmount
	) {
	}

	record AdminOrderActionResponse(
		UUID orderId,
		OrderStatus status,
		AdminFulfillmentResponse fulfillment,
		AdminShipmentResponse shipment
	) {
	}

	record BankTransferPaymentCommandResponse(
		UUID orderId,
		OrderStatus status,
		AdminFulfillmentResponse fulfillment,
		AdminShipmentResponse shipment,
		String outcome,
		PaymentExceptionReason exceptionReason,
		long expectedAmount,
		long actualAmount,
		PaymentGroupStatus paymentGroupStatus,
		List<OrderStatus> orderStatuses,
		PaymentCommandPaymentResponse payment,
		PaymentCommandRefundResponse refund,
		List<PaymentCommandRefundResponse> refunds,
		boolean supplierVisible,
		String customerDisplayStatus,
		String nextAction
	) {
	}

	record PaymentCommandPaymentResponse(
		UUID paymentId,
		PaymentProvider provider,
		PaymentStatus status,
		long actualAmount,
		Instant depositedAt,
		String transactionReference
	) {
	}

	record PaymentCommandRefundResponse(
		UUID refundId,
		UUID orderId,
		RefundScope refundScope,
		RefundReason reason,
		RefundStatus status,
		long refundAmount
	) {
	}

	record OrderStatusHistoryListResponse(
		List<OrderStatusHistoryResponse> histories
	) {
	}

	record OrderStatusHistoryResponse(
		UUID historyId,
		UUID actorUserId,
		String actionType,
		OrderStatus fromStatus,
		OrderStatus toStatus,
		String guardResult,
		String sideEffectSummary,
		String reason,
		Instant createdAt
	) {
	}

	record AdminActionHistoryListResponse(
		List<AdminActionHistoryResponse> actions
	) {
	}

	record AdminActionHistoryResponse(
		UUID actionHistoryId,
		UUID orderId,
		UUID adminUserId,
		AdminOrderActionType actionType,
		OrderStatus beforeStatus,
		OrderStatus afterStatus,
		String reason,
		Instant createdAt
	) {
	}

	record AdminShipmentResponse(
		UUID shipmentId,
		ShipmentStatus status,
		String carrier,
		String trackingNumber,
		Instant shippedAt,
		Instant deliveredAt,
		Instant trackingSyncedAt,
		String trackingSyncFailureReason,
		boolean manualOverride,
		UUID manualCorrectedByAdminId,
		Instant manualCorrectedAt,
		String manualCorrectionReason
	) {
	}

	record AdminRefundResponse(
		UUID refundId,
		UUID orderId,
		String orderNumber,
		UUID paymentGroupId,
		List<UUID> appliedOrderIds,
		RefundReason reason,
		RefundStatus status,
		long refundAmount,
		RefundScope refundScope,
		String providerPaymentKey,
		String providerCancelTransactionKey,
		String failureCode,
		String failureMessage,
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
		Instant failedAt
	) {
	}

	record AdminClaimResponse(
		UUID claimId,
		ClaimType claimType,
		ClaimReason claimReason,
		ClaimStatus status,
		RequestedAction requestedAction,
		String customerMemo,
		UUID reviewedByAdminId,
		String adminReviewReason,
		Instant reviewedAt,
		UUID returnReceivedByAdminId,
		Instant returnReceivedAt,
		String returnReceivedMemo,
		UUID refundId,
		Instant completedAt,
		Instant createdAt,
		List<AdminClaimEvidenceResponse> evidenceFiles
	) {
	}

	record AdminClaimEvidenceResponse(
		UUID evidenceId,
		String fileUrl,
		String originalFilename,
		String contentType,
		long sizeBytes,
		Instant uploadedAt
	) {
	}

	record AdminOrderItemResponse(
		UUID orderItemId,
		UUID productId,
		UUID productOptionId,
		String productName,
		String optionName,
		int quantity,
		long unitPrice,
		long lineAmount,
		int productDetailVersion,
		Integer productNoticeVersion
	) {
	}

	record SupplierWorkStartRequest(
		@NotBlank
		@Size(max = 1000)
		String reason
	) {
	}

	record SupplierOrderCompletedRequest(
		@NotBlank
		@Size(max = 100)
		String supplierOrderNumber,

		LocalDate expectedShipDate,

		@Size(max = 2000)
		String supplierResponseMemo,

		@NotBlank
		@Size(max = 1000)
		String reason
	) {
	}

	record OutOfStockRequest(
		@NotBlank
		@Size(max = 1000)
		String reason
	) {
	}

	record DelayNoticeRequest(
		@NotBlank
		@Size(max = 1000)
		String reason
	) {
	}

	record ShipmentCreateRequest(
		@NotBlank
		@Size(max = 100)
		String carrier,

		@NotBlank
		@Size(max = 100)
		String trackingNumber
	) {
	}

	record BankTransferDepositConfirmRequest(
		@NotBlank
		@Size(max = 100)
		String actualDepositorName,

		@Positive
		long actualAmount,

		@NotNull
		@PastOrPresent
		Instant depositedAt,

		@NotBlank
		@Size(max = 200)
		String transactionReference,

		@NotBlank
		@Size(max = 1000)
		String reason
	) {
	}

	record BankTransferUnpaidCancelRequest(
		@NotBlank
		@Size(max = 1000)
		String reason
	) {
	}

	record BankTransferDepositMismatchRequest(
		@NotBlank
		@Size(max = 100)
		String actualDepositorName,

		@NotNull
		@Positive
		Long actualAmount,

		@NotNull
		@PastOrPresent
		Instant depositedAt,

		@NotBlank
		@Size(max = 200)
		String transactionReference,

		@NotBlank
		@Size(max = 1000)
		String reason
	) {
	}

	record BankTransferLateDepositRequest(
		@NotBlank
		@Size(max = 100)
		String actualDepositorName,

		@Positive
		long actualAmount,

		@NotNull
		@PastOrPresent
		Instant depositedAt,

		@NotBlank
		@Size(max = 200)
		String transactionReference,

		@NotBlank
		@Size(max = 1000)
		String reason
	) {
	}

	record SupplierPurchaseCancelRequest(
		@NotBlank
		@Size(max = 500)
		String reason
	) {
	}
}
