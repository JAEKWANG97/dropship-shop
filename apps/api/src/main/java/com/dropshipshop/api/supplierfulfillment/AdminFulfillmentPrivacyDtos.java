package com.dropshipshop.api.supplierfulfillment;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class AdminFulfillmentPrivacyDtos {

	private AdminFulfillmentPrivacyDtos() {
	}

	record PortalTakeoverRequest(@NotBlank @Size(max = 200) String reason) {
	}

	record PortalTakeoverResponse(
		UUID orderId,
		UUID fulfillmentId,
		FulfillmentOperationalOwner operationalOwner,
		Instant handedOverAt,
		String reason
	) {
	}

	record GrantRequest(
		@NotNull SupplierPiiGrantAction action,
		UUID expectedLatestGrantId,
		@NotNull Instant accessUntil,
		@NotBlank @Size(max = 200) String reason
	) {
	}

	record RevokeRequest(
		@NotNull UUID expectedLatestGrantId,
		@NotBlank @Size(max = 200) String reason
	) {
	}

	record GrantResponse(
		UUID grantId,
		UUID claimId,
		UUID supplierId,
		int sequence,
		SupplierPiiGrantAction action,
		Instant accessUntil,
		UUID previousGrantId,
		Instant createdAt
	) {
	}
}
