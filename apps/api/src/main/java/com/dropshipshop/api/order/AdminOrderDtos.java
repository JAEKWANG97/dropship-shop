package com.dropshipshop.api.order;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.fulfillment.domain.FulfillmentStatus;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentMethod;
import com.dropshipshop.api.payment.domain.PaymentStatus;
import com.dropshipshop.api.refund.domain.RefundReason;
import com.dropshipshop.api.refund.domain.RefundScope;
import com.dropshipshop.api.refund.domain.RefundStatus;
import com.dropshipshop.api.shipment.domain.ShipmentStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class AdminOrderDtos {

	private AdminOrderDtos() {
	}

	record AdminOrderListResponse(
		List<AdminOrderSummaryResponse> orders
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
		String address2
	) {
	}

	record AdminPaymentGroupResponse(
		UUID paymentGroupId,
		String checkoutNumber,
		PaymentGroupStatus status,
		long totalAmount,
		Long approvedAmount,
		Instant approvedAt
	) {
	}

	record AdminPaymentResponse(
		UUID paymentId,
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
		Instant supplierOrderStartedAt,
		Instant addressLockedAt,
		UUID addressLockedByAdminId,
		String supplierOrderNumber,
		UUID orderedByAdminId,
		Instant orderedAt,
		LocalDate expectedShipDate,
		String supplierResponseMemo,
		String outOfStockReason
	) {
	}

	record AdminOrderActionResponse(
		UUID orderId,
		OrderStatus status,
		AdminFulfillmentResponse fulfillment,
		AdminShipmentResponse shipment
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
		RefundReason reason,
		RefundStatus status,
		long refundAmount,
		RefundScope refundScope,
		String providerPaymentKey,
		String providerCancelTransactionKey,
		String failureCode,
		String failureMessage,
		Instant requestedAt,
		Instant completedAt,
		Instant failedAt
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

	record ShipmentCreateRequest(
		@NotBlank
		@Size(max = 100)
		String carrier,

		@NotBlank
		@Size(max = 100)
		String trackingNumber
	) {
	}
}
