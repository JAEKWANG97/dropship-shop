package com.dropshipshop.api.claim;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/claims")
@PreAuthorize("hasRole('ADMIN')")
class AdminClaimController {

	private final AdminClaimService adminClaimService;
	private final CurrentUser currentUser;

	AdminClaimController(AdminClaimService adminClaimService, CurrentUser currentUser) {
		this.adminClaimService = adminClaimService;
		this.currentUser = currentUser;
	}

	@GetMapping
	ClaimDtos.AdminClaimListResponse listCancellationClaims() {
		return adminClaimService.listCancellationClaims();
	}

	@PostMapping("/{claimId}/approve")
	ClaimDtos.ClaimResponse approveCancellationClaim(
		@PathVariable UUID claimId,
		@Valid @RequestBody ClaimDtos.AdminClaimReviewRequest request,
		Authentication authentication
	) {
		return adminClaimService.approveCancellationClaim(claimId, currentUser.id(authentication), request);
	}

	@PostMapping("/{claimId}/reject")
	ClaimDtos.ClaimResponse rejectCancellationClaim(
		@PathVariable UUID claimId,
		@Valid @RequestBody ClaimDtos.AdminClaimReviewRequest request,
		Authentication authentication
	) {
		return adminClaimService.rejectCancellationClaim(claimId, currentUser.id(authentication), request);
	}
}
