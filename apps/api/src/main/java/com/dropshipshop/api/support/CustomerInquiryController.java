package com.dropshipshop.api.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.support.domain.CustomerInquiry;

import jakarta.validation.Valid;

@RestController
class CustomerInquiryController {

	private final CustomerInquiryRepository customerInquiryRepository;

	CustomerInquiryController(CustomerInquiryRepository customerInquiryRepository) {
		this.customerInquiryRepository = customerInquiryRepository;
	}

	@PostMapping("/api/customer-inquiries")
	@ResponseStatus(HttpStatus.CREATED)
	CustomerInquiryDtos.CustomerInquiryResponse createInquiry(
		@Valid @RequestBody CustomerInquiryDtos.CustomerInquiryRequest request
	) {
		CustomerInquiry inquiry = customerInquiryRepository.save(new CustomerInquiry(
			request.customerName(),
			request.email(),
			blankToNull(request.phone()),
			request.subject(),
			request.message()
		));
		return toResponse(inquiry);
	}

	@GetMapping("/api/admin/customer-inquiries")
	CustomerInquiryDtos.CustomerInquiryListResponse listInquiries() {
		return new CustomerInquiryDtos.CustomerInquiryListResponse(customerInquiryRepository.findAllByOrderByCreatedAtDesc()
			.stream()
			.map(this::toResponse)
			.toList());
	}

	private CustomerInquiryDtos.CustomerInquiryResponse toResponse(CustomerInquiry inquiry) {
		return new CustomerInquiryDtos.CustomerInquiryResponse(
			inquiry.getId(),
			inquiry.getCustomerName(),
			inquiry.getEmail(),
			inquiry.getPhone(),
			inquiry.getSubject(),
			inquiry.getMessage(),
			inquiry.getCreatedAt()
		);
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
