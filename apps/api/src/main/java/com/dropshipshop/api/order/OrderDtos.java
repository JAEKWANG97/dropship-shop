package com.dropshipshop.api.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.claim.domain.ClaimReason;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.domain.ClaimType;
import com.dropshipshop.api.claim.domain.RequestedAction;
import com.dropshipshop.api.fulfillment.domain.FulfillmentStatus;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentStatus;
import com.dropshipshop.api.refund.domain.RefundStatus;
import com.dropshipshop.api.shipment.domain.ShipmentStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class OrderDtos {

	private OrderDtos() {
	}

	record OrderListResponse(
		List<OrderSummaryResponse> orders
	) {
	}

	record OrderSummaryResponse(
		UUID orderId,
		String orderNumber,
		UUID paymentGroupId,
		String checkoutNumber,
		OrderStatus status,
		long totalAmount,
		Instant createdAt
	) {
	}

	record OrderDetailResponse(
		UUID orderId,
		String orderNumber,
		OrderStatus status,
		long subtotalAmount,
		long shippingFee,
		long discountAmount,
		long totalAmount,
		Instant createdAt,
		String customerDisplayStatus,
		String customerDisplayLabel,
		Long refundAmount,
		PaymentGroupSummaryResponse paymentGroup,
		PaymentSummaryResponse payment,
		ShippingAddressResponse shippingAddress,
		List<OrderItemResponse> items,
		FulfillmentSummaryResponse fulfillment,
		ShipmentSummaryResponse shipment,
		RefundSummaryResponse refund,
		List<ClaimSummaryResponse> claims,
		ClaimSummaryResponse claim
	) {
	}

	record PaymentGroupSummaryResponse(
		UUID paymentGroupId,
		String checkoutNumber,
		PaymentGroupStatus status,
		long totalAmount,
		Long approvedAmount,
		Instant approvedAt,
		String customerDisplayStatus,
		String customerDisplayLabel,
		Long refundAmount
	) {
	}

	record PaymentSummaryResponse(
		UUID paymentId,
		PaymentStatus status,
		Long approvedAmount,
		Instant approvedAt
	) {
	}

	record ShippingAddressResponse(
		String recipientName,
		String recipientPhone,
		String postalCode,
		String address1,
		String address2
	) {
	}

	record UpdateShippingAddressRequest(
		@NotBlank @Size(max = 100) String recipientName,
		@NotBlank @Size(max = 30) String recipientPhone,
		@NotBlank @Size(max = 20) String postalCode,
		@NotBlank @Size(max = 300) String address1,
		@Size(max = 300) String address2
	) {
	}

	record OrderItemResponse(
		UUID orderItemId,
		String productName,
		String productSummary,
		String optionName,
		int quantity,
		long unitPrice,
		long lineAmount,
		int productDetailVersion,
		Integer productNoticeVersion
	) {
	}

	record FulfillmentSummaryResponse(
		FulfillmentStatus status
	) {
	}

	record ShipmentSummaryResponse(
		ShipmentStatus status,
		String carrier,
		String trackingNumber
	) {
	}

	record RefundSummaryResponse(
		RefundStatus status,
		Long amount
	) {
	}

	record ClaimSummaryResponse(
		UUID claimId,
		ClaimType claimType,
		ClaimReason claimReason,
		ClaimStatus status,
		String customerStatus,
		String customerStatusLabel,
		RequestedAction requestedAction,
		String customerMemo,
		String adminReviewReason,
		Instant reviewedAt,
		Instant returnReceivedAt,
		String returnReceivedMemo,
		UUID refundId,
		Instant completedAt,
		Instant createdAt,
		List<ClaimEvidenceResponse> evidenceFiles
	) {
	}

	record ClaimEvidenceResponse(
		UUID evidenceId,
		String fileUrl,
		String originalFilename,
		String contentType,
		long sizeBytes,
		Instant uploadedAt
	) {
	}
}
