package com.dropshipshop.api.payment;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminPaymentExceptionController {

	private final PaymentExceptionService paymentExceptionService;

	AdminPaymentExceptionController(PaymentExceptionService paymentExceptionService) {
		this.paymentExceptionService = paymentExceptionService;
	}

	@GetMapping("/payment-exceptions")
	PaymentDtos.AdminPaymentExceptionListResponse listPaymentExceptions() {
		return paymentExceptionService.listPaymentExceptions();
	}

	@PostMapping("/payments/{paymentId}/retry-cancel")
	PaymentDtos.AdminPaymentExceptionResponse retryPaymentExceptionCancel(@PathVariable UUID paymentId) {
		return paymentExceptionService.retryCancel(paymentId);
	}
}
