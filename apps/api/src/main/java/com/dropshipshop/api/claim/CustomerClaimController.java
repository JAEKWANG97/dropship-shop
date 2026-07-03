package com.dropshipshop.api.claim;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.claim.domain.ClaimReason;
import com.dropshipshop.api.claim.domain.ClaimType;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders/{orderId}")
@PreAuthorize("hasRole('CUSTOMER')")
class CustomerClaimController {

	private final CustomerClaimService customerClaimService;
	private final CurrentUser currentUser;

	CustomerClaimController(CustomerClaimService customerClaimService, CurrentUser currentUser) {
		this.customerClaimService = customerClaimService;
		this.currentUser = currentUser;
	}

	@GetMapping("/claims")
	ClaimDtos.CustomerClaimListResponse listClaims(
		@PathVariable UUID orderId,
		Authentication authentication
	) {
		return customerClaimService.listClaims(currentUser.id(authentication), orderId);
	}

	@GetMapping("/claims/{claimId}")
	ClaimDtos.ClaimResponse getClaim(
		@PathVariable UUID orderId,
		@PathVariable UUID claimId,
		Authentication authentication
	) {
		return customerClaimService.getClaim(currentUser.id(authentication), orderId, claimId);
	}

	@PostMapping("/cancel")
	@ResponseStatus(HttpStatus.CREATED)
	ClaimDtos.ClaimResponse selfServiceCancel(
		@PathVariable UUID orderId,
		@Valid @RequestBody ClaimDtos.CustomerCancelRequest request,
		Authentication authentication
	) {
		return customerClaimService.selfServiceCancel(currentUser.id(authentication), orderId, request);
	}

	@PostMapping(value = "/claims", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ClaimDtos.ClaimResponse createClaim(
		@PathVariable UUID orderId,
		@Valid @RequestBody ClaimDtos.CustomerClaimRequest request,
		Authentication authentication
	) {
		return customerClaimService.createClaim(currentUser.id(authentication), orderId, request);
	}

	@PostMapping(value = "/claims", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ClaimDtos.ClaimResponse createClaimWithEvidence(
		@PathVariable UUID orderId,
		@RequestParam ClaimType claimType,
		@RequestParam ClaimReason claimReason,
		@RequestParam String customerMemo,
		@RequestPart(name = "evidenceFiles", required = false) List<MultipartFile> evidenceFiles,
		Authentication authentication
	) {
		return customerClaimService.createClaim(
			currentUser.id(authentication),
			orderId,
			new ClaimDtos.CustomerClaimRequest(claimType, claimReason, customerMemo),
			evidenceFiles
		);
	}

	@PostMapping(value = "/claims/{claimId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	ClaimDtos.ClaimResponse addEvidence(
		@PathVariable UUID orderId,
		@PathVariable UUID claimId,
		@RequestPart(name = "evidenceFiles", required = false) List<MultipartFile> evidenceFiles,
		Authentication authentication
	) {
		return customerClaimService.addEvidence(currentUser.id(authentication), orderId, claimId, evidenceFiles);
	}
}
