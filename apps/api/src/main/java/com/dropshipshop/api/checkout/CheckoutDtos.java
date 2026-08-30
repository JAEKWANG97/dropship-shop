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
		@Size(max = 300) String deliveryMemo,
		@Size(max = 100) String depositorName,
		Long clientSubmittedTotalAmount
	) {
		CreateCheckoutRequest(
			String recipientName,
			String recipientPhone,
			String postalCode,
			String address1,
			String address2,
			String depositorName,
			Long clientSubmittedTotalAmount
		) {
			this(recipientName, recipientPhone, postalCode, address1, address2, null,
				depositorName, clientSubmittedTotalAmount);
		}
	}

	record UpdateShippingAddressRequest(
		@NotBlank @Size(max = 100) String recipientName,
		@NotBlank @Size(max = 30) String recipientPhone,
		@NotBlank @Size(max = 20) String postalCode,
		@NotBlank @Size(max = 300) String address1,
		@Size(max = 300) String address2,
		@Size(max = 300) String deliveryMemo
	) {
		UpdateShippingAddressRequest(
			String recipientName,
			String recipientPhone,
			String postalCode,
			String address1,
			String address2
		) {
			this(recipientName, recipientPhone, postalCode, address1, address2, null);
		}
	}

	record PolicyConfirmationRequest(
		@NotBlank @Size(max = 50) String termsVersion,
		@NotBlank @Size(max = 50) String privacyVersion,
		@NotBlank @Size(max = 50) String orderPolicyVersion,
		@NotBlank @Size(max = 50) String cancellationRefundPolicyVersion,
		@NotBlank @Size(max = 50) String outOfStockNoticeVersion
	) {
	}

	record CheckoutResponse(
		UUID paymentGroupId,
		String checkoutNumber,
		PaymentGroupStatus status,
		long totalAmount,
		long refundableAmount,
		String customerDisplayStatus,
		String customerDisplayLabel,
		Long refundAmount,
		Instant expiresAt,
		Instant policyConfirmedAt,
		BankTransferDepositResponse bankTransferDeposit,
		ShippingAddressResponse shippingAddress,
		PolicyEvidenceResponse policyEvidence,
		List<PolicyLinkResponse> policyLinks,
		List<OrderResponse> orders
	) {
	}

	record ShippingAddressResponse(
		String recipientName,
		String recipientPhone,
		String postalCode,
		String address1,
		String address2,
		String deliveryMemo
	) {
	}

	record PolicyEvidenceResponse(
		String termsVersion,
		String privacyVersion,
		String orderPolicyVersion,
		String cancellationRefundPolicyVersion,
		String outOfStockNoticeVersion,
		String confirmedNoticeText
	) {
	}

	record BankTransferDepositResponse(
		String bankName,
		String accountNumber,
		String accountHolder,
		String depositorName,
		long amount,
		Instant deadline,
		String cashReceiptNotice
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
		String customerDisplayStatus,
		String customerDisplayLabel,
		Long refundAmount,
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
