package com.dropshipshop.api.supplierfulfillment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class SupplierOrderDtos {

	private SupplierOrderDtos() {
	}

	record OrderListResponse(List<OrderSummaryResponse> orders) {
	}

	record OrderSummaryResponse(
		String orderNumber,
		String status,
		Instant requestedAt,
		List<ListItemResponse> items
	) {
	}

	record ListItemResponse(String productName, String optionName, int quantity) {
	}

	record OrderDetailResponse(
		String orderNumber,
		String status,
		Instant requestedAt,
		String piiAccessLevel,
		String piiBasis,
		Instant piiAccessUntil,
		RecipientResponse recipient,
		List<DetailItemResponse> items
	) {
	}

	record RecipientResponse(
		String name,
		String phone,
		String postalCode,
		String address1,
		String address2,
		String deliveryMemo
	) {
	}

	record DetailItemResponse(
		UUID orderItemId,
		String productName,
		String optionName,
		int quantity,
		int allocatedQuantity,
		int remainingQuantity
	) {
	}

	record AccessLogListResponse(List<AccessLogResponse> logs) {
	}

	record AccessLogResponse(
		UUID id,
		UUID actorUserId,
		UUID orderId,
		String orderNumber,
		SupplierPiiAccessReason accessReason,
		Instant accessedAt
	) {
	}
}
