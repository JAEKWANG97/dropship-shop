package com.dropshipshop.api.checkout;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/checkouts")
@PreAuthorize("hasRole('CUSTOMER')")
class CheckoutController {

	private final CheckoutService checkoutService;
	private final CurrentUser currentUser;

	CheckoutController(CheckoutService checkoutService, CurrentUser currentUser) {
		this.checkoutService = checkoutService;
		this.currentUser = currentUser;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CheckoutDtos.CheckoutResponse createCheckout(
		@Valid @RequestBody CheckoutDtos.CreateCheckoutRequest request,
		Authentication authentication
	) {
		return checkoutService.createCheckout(currentUser.id(authentication), request);
	}

	@GetMapping("/{checkoutNumber}")
	CheckoutDtos.CheckoutResponse getCheckout(@PathVariable String checkoutNumber, Authentication authentication) {
		return checkoutService.getCheckout(currentUser.id(authentication), checkoutNumber);
	}

	@PostMapping("/{checkoutNumber}/policy-confirmation")
	CheckoutDtos.PolicyConfirmationResponse confirmPolicy(
		@PathVariable String checkoutNumber,
		@Valid @RequestBody CheckoutDtos.PolicyConfirmationRequest request,
		Authentication authentication
	) {
		return checkoutService.confirmPolicy(currentUser.id(authentication), checkoutNumber, request);
	}
}
