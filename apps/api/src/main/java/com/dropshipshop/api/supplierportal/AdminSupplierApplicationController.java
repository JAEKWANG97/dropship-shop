package com.dropshipshop.api.supplierportal;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.supplierportal.SupplierApplicationService.ReviewOutcome;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/admin/supplier-applications")
@PreAuthorize("hasRole('ADMIN')")
@Validated
class AdminSupplierApplicationController {

	private final SupplierApplicationService applicationService;
	private final CurrentUser currentUser;

	AdminSupplierApplicationController(SupplierApplicationService applicationService, CurrentUser currentUser) {
		this.applicationService = applicationService;
		this.currentUser = currentUser;
	}

	@GetMapping
	SupplierPortalDtos.ApplicationPageResponse list(
		@RequestParam(required = false) SupplierApplicationStatus status,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return applicationService.list(status, page, size);
	}

	@GetMapping("/{applicationId}")
	SupplierPortalDtos.ApplicationDetailResponse get(@PathVariable UUID applicationId) {
		return applicationService.get(applicationId);
	}

	@PostMapping("/{applicationId}/approve")
	SupplierPortalDtos.ApplicationReviewResponse approve(
		@PathVariable UUID applicationId,
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@Valid @RequestBody SupplierPortalDtos.ApplicationApproveRequest request,
		Authentication authentication
	) {
		return response(applicationService.approve(
			applicationId,
			currentUser.id(authentication),
			idempotencyKey,
			request
		));
	}

	@PostMapping("/{applicationId}/reject")
	SupplierPortalDtos.ApplicationReviewResponse reject(
		@PathVariable UUID applicationId,
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@Valid @RequestBody SupplierPortalDtos.ApplicationRejectRequest request,
		Authentication authentication
	) {
		return response(applicationService.reject(
			applicationId,
			currentUser.id(authentication),
			idempotencyKey,
			request
		));
	}

	private SupplierPortalDtos.ApplicationReviewResponse response(ReviewOutcome outcome) {
		if (outcome.expired()) {
			throw new ApiErrorException(
				HttpStatus.CONFLICT,
				ApiErrorCode.APPLICATION_EXPIRED,
				"Supplier application expired"
			);
		}
		return outcome.response();
	}
}
