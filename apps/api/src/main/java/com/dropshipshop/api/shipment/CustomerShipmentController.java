package com.dropshipshop.api.shipment;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasRole('CUSTOMER')")
class CustomerShipmentController {

	private final PortalShipmentService shipmentService;
	private final CurrentUser currentUser;

	CustomerShipmentController(PortalShipmentService shipmentService, CurrentUser currentUser) {
		this.shipmentService = shipmentService;
		this.currentUser = currentUser;
	}

	@GetMapping("/{orderId}/shipments")
	PortalShipmentDtos.CustomerShipmentListResponse list(
		@PathVariable UUID orderId,
		Authentication authentication
	) {
		return shipmentService.listCustomer(currentUser.id(authentication), orderId);
	}
}
