package com.dropshipshop.api.shipment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.shipment.ShipmentTrackingDtos.InternalTrackingSyncRequest;
import com.dropshipshop.api.shipment.ShipmentTrackingDtos.InternalTrackingSyncResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/internal/shipments")
class InternalShipmentTrackingController {

	static final String SYNC_TOKEN_HEADER = "X-Internal-Sync-Token";

	private final ShipmentTrackingService shipmentTrackingService;
	private final String syncToken;

	InternalShipmentTrackingController(
		ShipmentTrackingService shipmentTrackingService,
		@Value("${app.internal.sync-token:}") String syncToken
	) {
		this.shipmentTrackingService = shipmentTrackingService;
		this.syncToken = syncToken;
	}

	@PostMapping("/tracking-sync")
	InternalTrackingSyncResponse syncShipments(
		@RequestHeader(value = SYNC_TOKEN_HEADER, required = false) String providedToken,
		@Valid @RequestBody InternalTrackingSyncRequest request
	) {
		requireInternalToken(providedToken);
		return shipmentTrackingService.syncInternal(request);
	}

	private void requireInternalToken(String providedToken) {
		if (!matchesToken(providedToken)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal sync token is required");
		}
	}

	private boolean matchesToken(String providedToken) {
		if (syncToken.isBlank() || providedToken == null || providedToken.isBlank()) {
			return false;
		}
		return MessageDigest.isEqual(
			providedToken.getBytes(StandardCharsets.UTF_8),
			syncToken.getBytes(StandardCharsets.UTF_8)
		);
	}
}
