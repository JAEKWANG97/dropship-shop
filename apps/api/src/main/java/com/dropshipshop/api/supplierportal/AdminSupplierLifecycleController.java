package com.dropshipshop.api.supplierportal;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.supplierportal.SupplierLifecycleService.CommandOutcome;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/suppliers/{supplierId}")
@PreAuthorize("hasRole('ADMIN')")
class AdminSupplierLifecycleController {

	private final SupplierLifecycleService lifecycleService;
	private final CurrentUser currentUser;

	AdminSupplierLifecycleController(SupplierLifecycleService lifecycleService, CurrentUser currentUser) {
		this.lifecycleService = lifecycleService;
		this.currentUser = currentUser;
	}

	@PostMapping("/invite/reissue")
	SupplierPortalDtos.InviteResponse reissueInvite(
		@PathVariable UUID supplierId,
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@Valid @RequestBody SupplierPortalDtos.InviteReissueRequest request,
		Authentication authentication
	) {
		return lifecycleService.reissueInvite(
			supplierId,
			currentUser.id(authentication),
			idempotencyKey,
			request
		);
	}

	@PatchMapping("/portal-status")
	SupplierPortalDtos.SupplierLifecycleResponse updatePortalStatus(
		@PathVariable UUID supplierId,
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@Valid @RequestBody SupplierPortalDtos.PortalStatusRequest request,
		Authentication authentication
	) {
		return response(lifecycleService.updatePortalStatus(
			supplierId,
			currentUser.id(authentication),
			idempotencyKey,
			request
		));
	}

	@PatchMapping("/sales-status")
	SupplierPortalDtos.SupplierLifecycleResponse updateSalesStatus(
		@PathVariable UUID supplierId,
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@Valid @RequestBody SupplierPortalDtos.SalesStatusRequest request,
		Authentication authentication
	) {
		return response(lifecycleService.changeSalesStatus(
			supplierId,
			currentUser.id(authentication),
			idempotencyKey,
			request
		));
	}

	@PostMapping("/manager-disconnect")
	SupplierPortalDtos.SupplierLifecycleResponse disconnectManager(
		@PathVariable UUID supplierId,
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@Valid @RequestBody SupplierPortalDtos.ManagerDisconnectRequest request,
		Authentication authentication
	) {
		return lifecycleService.disconnectManager(
			supplierId,
			currentUser.id(authentication),
			idempotencyKey,
			request
		);
	}

	@PatchMapping("/contact-email")
	SupplierPortalDtos.SupplierLifecycleResponse changeContactEmail(
		@PathVariable UUID supplierId,
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@Valid @RequestBody SupplierPortalDtos.ContactEmailRequest request,
		Authentication authentication
	) {
		return lifecycleService.changeContactEmail(
			supplierId,
			currentUser.id(authentication),
			idempotencyKey,
			request
		);
	}

	private SupplierPortalDtos.SupplierLifecycleResponse response(CommandOutcome outcome) {
		if (outcome.contractNotVerified()) {
			throw new ApiErrorException(
				HttpStatus.CONFLICT,
				ApiErrorCode.CONTRACT_NOT_VERIFIED,
				"Supplier contract is not currently verified"
			);
		}
		return outcome.response();
	}
}
