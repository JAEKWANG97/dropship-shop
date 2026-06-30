package com.dropshipshop.api.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class CustomerInquiryDtos {

	private CustomerInquiryDtos() {
	}

	record CustomerInquiryRequest(
		@NotBlank @Size(max = 100) String customerName,
		@NotBlank @Email @Size(max = 320) String email,
		@Size(max = 50) String phone,
		@NotBlank @Size(max = 200) String subject,
		@NotBlank @Size(max = 2000) String message
	) {
	}

	record CustomerInquiryResponse(
		UUID inquiryId,
		String customerName,
		String email,
		String phone,
		String subject,
		String message,
		Instant createdAt
	) {
	}

	record CustomerInquiryListResponse(List<CustomerInquiryResponse> inquiries) {
	}
}
