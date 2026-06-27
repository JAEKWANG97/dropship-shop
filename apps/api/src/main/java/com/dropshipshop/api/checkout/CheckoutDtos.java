package com.dropshipshop.api.checkout;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class CheckoutDtos {

	private CheckoutDtos() {
	}

	record CreateCheckoutRequest(
		@NotBlank @Size(max = 100) String recipientName,
		@NotBlank @Size(max = 30) String recipientPhone,
		@NotBlank @Size(max = 20) String postalCode,
		@NotBlank @Size(max = 300) String address1,
		@Size(max = 300) String address2,
		Long clientSubmittedTotalAmount
	) {
	}

	record PolicyConfirmationRequest(
		@NotBlank @Size(max = 50) String termsVersion,
		@NotBlank @Size(max = 50) String privacyVersion,
		@NotBlank @Size(max = 50) String orderPolicyVersion,
		@NotBlank @Size(max = 50) String cancellationRefundPolicyVersion,
		@NotBlank @Size(max = 50) String outOfStockNoticeVersion,
		@NotBlank String confirmedNoticeText
	) {
	}

	record CheckoutResponse(
		UUID paymentGroupId,
		String checkoutNumber,
		PaymentGroupStatus status,
		long totalAmount,
		long refundableAmount,
		Instant expiresAt,
		Instant policyConfirmedAt,
		List<PolicyLinkResponse> policyLinks,
		List<OrderResponse> orders
	) {
	}

	record OrderResponse(
		UUID id,
		String orderNumber,
		UUID supplierId,
		String deliveryGroupName,
		OrderStatus status,
		long subtotalAmount,
		long shippingFee,
		long discountAmount,
		long totalAmount,
		List<OrderItemResponse> items
	) {
	}

	record OrderItemResponse(
		UUID id,
		String productName,
		String optionName,
		int quantity,
		long unitPrice,
		long lineAmount,
		int productDetailVersion,
		Integer productNoticeVersion
	) {
	}

	record PolicyConfirmationResponse(
		String checkoutNumber,
		Instant policyConfirmedAt
	) {
	}

	record PolicyLinkResponse(
		String label,
		String href,
		String policyType
	) {
	}
}
