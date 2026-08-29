package com.dropshipshop.api.supplierportal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/supplier-applications")
class SupplierApplicationController {

	private final SupplierApplicationService applicationService;

	SupplierApplicationController(SupplierApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	SupplierPortalDtos.ApplicationAcceptedResponse submit(
		@Valid @RequestBody SupplierPortalDtos.ApplicationSubmitRequest request,
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		HttpServletRequest servletRequest
	) {
		return applicationService.submit(request, idempotencyKey, servletRequest.getRemoteAddr());
	}
}
