package com.dropshipshop.api.support;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.support.domain.CustomerInquiryStatus;

import jakarta.validation.Valid;

@RestController
class CustomerInquiryController {

	private final CustomerInquiryService customerInquiryService;
	private final CurrentUser currentUser;

	CustomerInquiryController(CustomerInquiryService customerInquiryService, CurrentUser currentUser) {
		this.customerInquiryService = customerInquiryService;
		this.currentUser = currentUser;
	}

	@PostMapping("/api/customer-inquiries")
	@ResponseStatus(HttpStatus.CREATED)
	CustomerInquiryDtos.CustomerInquiryCreatedResponse createInquiry(
		@Valid @RequestBody CustomerInquiryDtos.CustomerInquiryRequest request
	) {
		return customerInquiryService.create(request);
	}

	@PostMapping("/api/customer-inquiries/{inquiryId}/lookup")
	CustomerInquiryDtos.CustomerInquiryLookupResponse lookupInquiry(
		@PathVariable UUID inquiryId,
		@Valid @RequestBody CustomerInquiryDtos.CustomerInquiryLookupRequest request
	) {
		return customerInquiryService.lookup(inquiryId, request);
	}

	@GetMapping("/api/admin/customer-inquiries")
	@PreAuthorize("hasRole('ADMIN')")
	CustomerInquiryDtos.CustomerInquiryListResponse listInquiries(
		@RequestParam(required = false) CustomerInquiryStatus status
	) {
		return customerInquiryService.list(status);
	}

	@GetMapping("/api/admin/customer-inquiries/{inquiryId}")
	@PreAuthorize("hasRole('ADMIN')")
	CustomerInquiryDtos.AdminCustomerInquiryResponse getInquiry(@PathVariable UUID inquiryId) {
		return customerInquiryService.detail(inquiryId);
	}

	@PatchMapping("/api/admin/customer-inquiries/{inquiryId}/status")
	@PreAuthorize("hasRole('ADMIN')")
	CustomerInquiryDtos.AdminCustomerInquiryResponse changeStatus(
		@PathVariable UUID inquiryId,
		@Valid @RequestBody CustomerInquiryDtos.AdminInquiryStatusRequest request,
		Authentication authentication
	) {
		return customerInquiryService.changeStatus(inquiryId, currentUser.id(authentication), request);
	}

	@PostMapping("/api/admin/customer-inquiries/{inquiryId}/answer")
	@PreAuthorize("hasRole('ADMIN')")
	CustomerInquiryDtos.AdminCustomerInquiryResponse answer(
		@PathVariable UUID inquiryId,
		@Valid @RequestBody CustomerInquiryDtos.AdminInquiryAnswerRequest request,
		Authentication authentication
	) {
		return customerInquiryService.answer(inquiryId, currentUser.id(authentication), request);
	}
}
