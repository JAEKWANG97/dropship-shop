package com.dropshipshop.api.supplierfulfillment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.shipment.PortalShipmentDtos;
import com.dropshipshop.api.shipment.PortalShipmentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/supplier")
@PreAuthorize("hasRole('SUPPLIER')")
class SupplierShipmentController {

	private final PortalShipmentService shipmentService;
	private final CurrentUser currentUser;

	SupplierShipmentController(PortalShipmentService shipmentService, CurrentUser currentUser) {
		this.shipmentService = shipmentService;
		this.currentUser = currentUser;
	}

	@GetMapping("/carriers")
	PortalShipmentDtos.CarrierListResponse carriers() {
		return shipmentService.carriers();
	}

	@GetMapping("/orders/{orderNumber}/shipments")
	PortalShipmentDtos.SupplierShipmentListResponse list(
		@PathVariable String orderNumber,
		Authentication authentication
	) {
		return shipmentService.listSupplier(currentUser.id(authentication), orderNumber);
	}

	@PostMapping("/orders/{orderNumber}/shipments")
	PortalShipmentDtos.SupplierShipmentResponse create(
		@PathVariable String orderNumber,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody PortalShipmentDtos.ShipmentCreateRequest request,
		Authentication authentication
	) {
		return shipmentService.createSupplier(
			currentUser.id(authentication), orderNumber, idempotencyKey, request
		);
	}

	@PatchMapping("/orders/{orderNumber}/shipments/{shipmentId}")
	PortalShipmentDtos.SupplierShipmentResponse correct(
		@PathVariable String orderNumber,
		@PathVariable java.util.UUID shipmentId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody PortalShipmentDtos.TrackingCorrectionRequest request,
		Authentication authentication
	) {
		return shipmentService.correctSupplier(
			currentUser.id(authentication), orderNumber, shipmentId, idempotencyKey, request
		);
	}
}
