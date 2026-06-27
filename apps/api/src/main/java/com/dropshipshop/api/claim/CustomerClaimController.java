package com.dropshipshop.api.claim;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

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

	@PostMapping("/cancel")
	@ResponseStatus(HttpStatus.CREATED)
	ClaimDtos.ClaimResponse selfServiceCancel(
		@PathVariable UUID orderId,
		@Valid @RequestBody ClaimDtos.CustomerCancelRequest request,
		Authentication authentication
	) {
		return customerClaimService.selfServiceCancel(currentUser.id(authentication), orderId, request);
	}

	@PostMapping("/claims")
	@ResponseStatus(HttpStatus.CREATED)
	ClaimDtos.ClaimResponse createClaim(
		@PathVariable UUID orderId,
		@Valid @RequestBody ClaimDtos.CustomerClaimRequest request,
		Authentication authentication
	) {
		return customerClaimService.createClaim(currentUser.id(authentication), orderId, request);
	}
}
