package com.dropshipshop.api.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
		String displayStatus,
		long totalAmount,
		Instant createdAt
	) {
	}

	record OrderDetailResponse(
		UUID orderId,
		String orderNumber,
		String displayStatus,
		long subtotalAmount,
		long shippingFee,
		long discountAmount,
		long totalAmount,
		Instant createdAt,
		PaymentGroupSummaryResponse paymentGroup,
		PaymentSummaryResponse payment,
		ShippingAddressResponse shippingAddress,
		List<OrderItemResponse> items,
		FulfillmentSummaryResponse fulfillment,
		ShipmentSummaryResponse shipment,
		RefundSummaryResponse refund
	) {
	}

	record PaymentGroupSummaryResponse(
		UUID paymentGroupId,
		String checkoutNumber,
		String displayStatus,
		long totalAmount,
		Long approvedAmount,
		Instant approvedAt
	) {
	}

	record PaymentSummaryResponse(
		UUID paymentId,
		String displayStatus,
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
		String displayStatus
	) {
	}

	record ShipmentSummaryResponse(
		String displayStatus,
		String carrier,
		String trackingNumber
	) {
	}

	record RefundSummaryResponse(
		String displayStatus,
		Long amount
	) {
	}
}
