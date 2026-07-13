package com.dropshipshop.api.support;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.notification.domain.NotificationStatus;
import com.dropshipshop.api.support.domain.CustomerInquiryStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class CustomerInquiryDtos {

	private CustomerInquiryDtos() {
	}

	record CustomerInquiryRequest(
		@NotBlank @Size(max = 100) String customerName,
		@NotBlank @Email @Size(max = 320) String email,
		@Size(max = 50) String phone,
		@NotBlank @Size(max = 200) String subject,
		@NotBlank @Size(max = 2000) String message,
		@AssertTrue boolean privacyConsent
	) {
	}

	record CustomerInquiryCreatedResponse(
		UUID inquiryId,
		String lookupToken,
		CustomerInquiryStatus status,
		Instant createdAt
	) {
	}

	record CustomerInquiryLookupRequest(@NotBlank @Size(max = 200) String lookupToken) {
	}

	record CustomerInquiryLookupResponse(
		UUID inquiryId,
		String subject,
		String message,
		CustomerInquiryStatus status,
		String answer,
		Instant createdAt,
		Instant answeredAt,
		Instant closedAt
	) {
	}

	record AdminInquiryStatusRequest(
		@NotNull CustomerInquiryStatus status,
		@Size(max = 5000) String adminMemo
	) {
	}

	record AdminInquiryAnswerRequest(
		@NotBlank @Size(max = 5000) String answer,
		@Size(max = 5000) String adminMemo
	) {
	}

	record InquiryNotificationResponse(
		UUID notificationId,
		NotificationStatus status,
		String failureReason,
		Instant sentAt,
		Instant createdAt
	) {
	}

	record AdminCustomerInquiryResponse(
		UUID inquiryId,
		String customerName,
		String email,
		String phone,
		String subject,
		String message,
		CustomerInquiryStatus status,
		String consentPolicyVersion,
		Instant consentedAt,
		Instant retentionExpiresAt,
		String adminMemo,
		String answer,
		UUID handledByAdminId,
		Instant answeredAt,
		Instant closedAt,
		Instant createdAt,
		Instant updatedAt,
		InquiryNotificationResponse latestAnswerNotification
	) {
	}

	record CustomerInquiryListResponse(List<AdminCustomerInquiryResponse> inquiries) {
	}
}
