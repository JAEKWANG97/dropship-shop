package com.dropshipshop.api.supplierfulfillment;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@PreAuthorize("hasRole('ADMIN')")
class AdminFulfillmentPrivacyController {

	private final AdminPortalTakeoverService takeoverService;
	private final AdminSupplierPiiGrantService grantService;
	private final CurrentUser currentUser;

	AdminFulfillmentPrivacyController(
		AdminPortalTakeoverService takeoverService,
		AdminSupplierPiiGrantService grantService,
		CurrentUser currentUser
	) {
		this.takeoverService = takeoverService;
		this.grantService = grantService;
		this.currentUser = currentUser;
	}

	@PostMapping("/api/admin/orders/{orderId}/portal-takeover")
	AdminFulfillmentPrivacyDtos.PortalTakeoverResponse takeOver(
		@PathVariable UUID orderId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody AdminFulfillmentPrivacyDtos.PortalTakeoverRequest request,
		Authentication authentication
	) {
		return takeoverService.takeOver(orderId, currentUser.id(authentication), idempotencyKey, request);
	}

	@PostMapping("/api/admin/claims/{claimId}/supplier-pii-access-grants")
	AdminFulfillmentPrivacyDtos.GrantResponse grant(
		@PathVariable UUID claimId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody AdminFulfillmentPrivacyDtos.GrantRequest request,
		Authentication authentication
	) {
		return grantService.grant(claimId, currentUser.id(authentication), idempotencyKey, request);
	}

	@PostMapping("/api/admin/claims/{claimId}/supplier-pii-access-grants/revoke")
	AdminFulfillmentPrivacyDtos.GrantResponse revoke(
		@PathVariable UUID claimId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@Valid @RequestBody AdminFulfillmentPrivacyDtos.RevokeRequest request,
		Authentication authentication
	) {
		return grantService.revoke(claimId, currentUser.id(authentication), idempotencyKey, request);
	}
}
