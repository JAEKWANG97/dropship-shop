package com.dropshipshop.api.shipment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.shipment.domain.ShipmentStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

final class ShipmentTrackingDtos {

	private ShipmentTrackingDtos() {
	}

	record TrackingSyncRequest(
		@Size(max = 100)
		String trackingStatus,

		@Size(max = 2000)
		String failureReason
	) {
	}

	record InternalTrackingSyncRequest(
		@NotEmpty
		List<@Valid InternalTrackingSyncItem> shipments
	) {
	}

	record InternalTrackingSyncItem(
		@NotBlank
		@Size(max = 100)
		String carrier,

		@NotBlank
		@Size(max = 100)
		String trackingNumber,

		@Size(max = 100)
		String trackingStatus,

		@Size(max = 2000)
		String failureReason
	) {
	}

	record TrackingSyncResponse(
		UUID shipmentId,
		ShipmentStatus shipmentStatus,
		OrderStatus orderStatus,
		Instant trackingSyncedAt,
		String trackingSyncFailureReason
	) {
	}

	record InternalTrackingSyncResponse(
		int received,
		int matched,
		int delivered,
		int failed,
		int notFound
	) {
	}
}
