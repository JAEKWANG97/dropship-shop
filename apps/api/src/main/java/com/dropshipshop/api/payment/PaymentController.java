package com.dropshipshop.api.payment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasRole('CUSTOMER')")
class PaymentController {

	private final PaymentService paymentService;
	private final CurrentUser currentUser;

	PaymentController(PaymentService paymentService, CurrentUser currentUser) {
		this.paymentService = paymentService;
		this.currentUser = currentUser;
	}

	@PostMapping("/toss/confirm")
	PaymentDtos.PaymentConfirmResponse confirmTossPayment(
		@Valid @RequestBody PaymentDtos.TossConfirmRequest request,
		Authentication authentication
	) {
		return paymentService.confirmTossPayment(currentUser.id(authentication), request);
	}
}
