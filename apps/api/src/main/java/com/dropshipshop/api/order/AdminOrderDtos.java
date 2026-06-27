package com.dropshipshop.api.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentMethod;
import com.dropshipshop.api.payment.domain.PaymentStatus;

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
}
