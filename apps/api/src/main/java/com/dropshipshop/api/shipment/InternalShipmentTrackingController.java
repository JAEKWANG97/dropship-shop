package com.dropshipshop.api.shipment;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.shipment.ShipmentTrackingDtos.InternalTrackingSyncRequest;
import com.dropshipshop.api.shipment.ShipmentTrackingDtos.InternalTrackingSyncResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/internal/shipments")
class InternalShipmentTrackingController {

	private final ShipmentTrackingService shipmentTrackingService;

	InternalShipmentTrackingController(ShipmentTrackingService shipmentTrackingService) {
		this.shipmentTrackingService = shipmentTrackingService;
	}

	@PostMapping("/tracking-sync")
	InternalTrackingSyncResponse syncShipments(@Valid @RequestBody InternalTrackingSyncRequest request) {
		return shipmentTrackingService.syncInternal(request);
	}
}
