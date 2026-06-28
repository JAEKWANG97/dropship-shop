package com.dropshipshop.api.shipment;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.shipment.ShipmentTrackingDtos.TrackingSyncRequest;
import com.dropshipshop.api.shipment.ShipmentTrackingDtos.TrackingSyncResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/shipments")
@PreAuthorize("hasRole('ADMIN')")
class AdminShipmentTrackingController {

	private final ShipmentTrackingService shipmentTrackingService;

	AdminShipmentTrackingController(ShipmentTrackingService shipmentTrackingService) {
		this.shipmentTrackingService = shipmentTrackingService;
	}

	@PostMapping("/{shipmentId}/tracking-sync")
	TrackingSyncResponse syncShipment(
		@PathVariable UUID shipmentId,
		@Valid @RequestBody TrackingSyncRequest request
	) {
		return shipmentTrackingService.syncShipment(shipmentId, request);
	}
}
