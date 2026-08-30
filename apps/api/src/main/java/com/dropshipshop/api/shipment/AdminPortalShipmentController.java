package com.dropshipshop.api.shipment;

import java.util.UUID;

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

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminPortalShipmentController {

	private final PortalShipmentService shipmentService;
	private final CurrentUser currentUser;

	AdminPortalShipmentController(PortalShipmentService shipmentService, CurrentUser currentUser) {
		this.shipmentService = shipmentService;
		this.currentUser = currentUser;
	}

	@GetMapping("/carriers")
	PortalShipmentDtos.CarrierListResponse carriers() {
		return shipmentService.carriers();
	}

	@GetMapping("/orders/{orderId}/portal-shipments")
	PortalShipmentDtos.AdminShipmentListResponse list(@PathVariable UUID orderId) {
		return shipmentService.listAdmin(orderId);
	}

	@PostMapping("/orders/{orderId}/portal-shipments")
	PortalShipmentDtos.AdminShipmentResponse create(
		@PathVariable UUID orderId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody PortalShipmentDtos.ShipmentCreateRequest request,
		Authentication authentication
	) {
		return shipmentService.createAdmin(
			currentUser.id(authentication), orderId, idempotencyKey, request
		);
	}

	@PatchMapping("/shipments/{shipmentId}/tracking-correction")
	PortalShipmentDtos.AdminShipmentResponse correctTracking(
		@PathVariable UUID shipmentId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody PortalShipmentDtos.TrackingCorrectionRequest request,
		Authentication authentication
	) {
		return shipmentService.correctAdmin(
			currentUser.id(authentication), shipmentId, idempotencyKey, request
		);
	}

	@PostMapping("/shipments/{shipmentId}/void")
	PortalShipmentDtos.AdminShipmentResponse voidShipment(
		@PathVariable UUID shipmentId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody PortalShipmentDtos.ShipmentVoidRequest request,
		Authentication authentication
	) {
		return shipmentService.voidAdmin(
			currentUser.id(authentication), shipmentId, idempotencyKey, request
		);
	}

	@PostMapping("/shipments/{shipmentId}/delivery-complete")
	PortalShipmentDtos.AdminShipmentResponse completeDelivery(
		@PathVariable UUID shipmentId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody PortalShipmentDtos.DeliveryCompleteRequest request,
		Authentication authentication
	) {
		return shipmentService.completeDelivery(
			currentUser.id(authentication), shipmentId, idempotencyKey, request
		);
	}

	@PostMapping("/shipments/{shipmentId}/delivery-correction")
	PortalShipmentDtos.AdminShipmentResponse correctDelivery(
		@PathVariable UUID shipmentId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody PortalShipmentDtos.DeliveryCorrectionRequest request,
		Authentication authentication
	) {
		return shipmentService.correctDelivery(
			currentUser.id(authentication), shipmentId, idempotencyKey, request
		);
	}
}
