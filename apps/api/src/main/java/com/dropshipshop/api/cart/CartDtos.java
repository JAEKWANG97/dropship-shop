package com.dropshipshop.api.cart;

import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

final class CartDtos {

	private CartDtos() {
	}

	record AddCartItemRequest(
		@NotNull UUID productOptionId,
		@Min(1) @Max(99) int quantity
	) {
	}

	record UpdateCartItemRequest(
		@Min(1) @Max(99) int quantity
	) {
	}

	record CartResponse(
		UUID cartId,
		List<CartItemResponse> items,
		long subtotalAmount,
		boolean salesEnabled,
		String salesNotice,
		boolean checkoutAvailable,
		List<CartValidationIssueResponse> issues
	) {
	}

	record CartItemResponse(
		UUID id,
		UUID productId,
		UUID productOptionId,
		String productName,
		String optionName,
		int quantity,
		int minimumOrderQuantity,
		int orderQuantityStep,
		long unitPrice,
		long lineAmount,
		ProductStatus productStatus,
		ProductOptionStatus optionStatus,
		String thumbnailImageUrl,
		boolean sellable,
		String unavailableReason
	) {
	}

	record CartValidationResponse(
		boolean checkoutAvailable,
		List<CartValidationIssueResponse> issues
	) {
	}

	record CartValidationIssueResponse(
		UUID cartItemId,
		String code,
		String message
	) {
	}
}
