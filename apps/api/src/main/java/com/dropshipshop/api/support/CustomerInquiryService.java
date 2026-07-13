package com.dropshipshop.api.support;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.notification.NotificationLogRepository;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.support.domain.CustomerInquiry;
import com.dropshipshop.api.support.domain.CustomerInquiryStatus;

@Service
class CustomerInquiryService {

	private static final int RATE_LIMIT_COUNT = 3;
	private static final long RATE_LIMIT_SECONDS = 600;

	private final CustomerInquiryRepository customerInquiryRepository;
	private final NotificationLogRepository notificationLogRepository;
	private final NotificationService notificationService;
	private final InquiryLookupTokenService lookupTokenService;
	private final String consentPolicyVersion;

	CustomerInquiryService(
		CustomerInquiryRepository customerInquiryRepository,
		NotificationLogRepository notificationLogRepository,
		NotificationService notificationService,
		InquiryLookupTokenService lookupTokenService,
		@Value("${app.inquiry.consent-policy-version:support-inquiry-privacy-2026-07-13}") String consentPolicyVersion
	) {
		this.customerInquiryRepository = customerInquiryRepository;
		this.notificationLogRepository = notificationLogRepository;
		this.notificationService = notificationService;
		this.lookupTokenService = lookupTokenService;
		this.consentPolicyVersion = consentPolicyVersion;
	}

	@Transactional
	CustomerInquiryDtos.CustomerInquiryCreatedResponse create(CustomerInquiryDtos.CustomerInquiryRequest request) {
		Instant now = Instant.now();
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (customerInquiryRepository.countByEmailAndCreatedAtAfter(email, now.minusSeconds(RATE_LIMIT_SECONDS)) >= RATE_LIMIT_COUNT) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "10분 뒤 다시 문의해 주세요");
		}

		CustomerInquiry inquiry = customerInquiryRepository.save(new CustomerInquiry(
			request.customerName().trim(),
			email,
			blankToNull(request.phone()),
			request.subject().trim(),
			request.message().trim(),
			consentPolicyVersion,
			now,
			now.atZone(ZoneOffset.UTC).plusYears(3).toInstant()
		));
		return new CustomerInquiryDtos.CustomerInquiryCreatedResponse(
			inquiry.getId(),
			lookupTokenService.token(inquiry.getId()),
			inquiry.getStatus(),
			inquiry.getCreatedAt()
		);
	}

	@Transactional(readOnly = true)
	CustomerInquiryDtos.CustomerInquiryLookupResponse lookup(
		UUID inquiryId,
		CustomerInquiryDtos.CustomerInquiryLookupRequest request
	) {
		CustomerInquiry inquiry = find(inquiryId);
		if (!lookupTokenService.matches(inquiryId, request.lookupToken())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found");
		}
		return new CustomerInquiryDtos.CustomerInquiryLookupResponse(
			inquiry.getId(),
			inquiry.getSubject(),
			inquiry.getMessage(),
			inquiry.getStatus(),
			inquiry.getAnswer(),
			inquiry.getCreatedAt(),
			inquiry.getAnsweredAt(),
			inquiry.getClosedAt()
		);
	}

	@Transactional(readOnly = true)
	CustomerInquiryDtos.CustomerInquiryListResponse list(CustomerInquiryStatus status) {
		List<CustomerInquiry> inquiries = status == null
			? customerInquiryRepository.findAllByOrderByCreatedAtDesc()
			: customerInquiryRepository.findAllByStatusOrderByCreatedAtDesc(status);
		return new CustomerInquiryDtos.CustomerInquiryListResponse(
			inquiries.stream().map(inquiry -> toAdminResponse(inquiry, null)).toList()
		);
	}

	@Transactional(readOnly = true)
	CustomerInquiryDtos.AdminCustomerInquiryResponse detail(UUID inquiryId) {
		CustomerInquiry inquiry = find(inquiryId);
		NotificationLog latestNotification = notificationLogRepository
			.findFirstByCustomerInquiryIdOrderByCreatedAtDesc(inquiryId)
			.orElse(null);
		return toAdminResponse(inquiry, latestNotification);
	}

	@Transactional
	CustomerInquiryDtos.AdminCustomerInquiryResponse changeStatus(
		UUID inquiryId,
		UUID adminUserId,
		CustomerInquiryDtos.AdminInquiryStatusRequest request
	) {
		CustomerInquiry inquiry = find(inquiryId);
		try {
			inquiry.changeStatus(request.status(), request.adminMemo(), adminUserId, Instant.now());
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
		return toAdminResponse(inquiry, null);
	}

	@Transactional
	CustomerInquiryDtos.AdminCustomerInquiryResponse answer(
		UUID inquiryId,
		UUID adminUserId,
		CustomerInquiryDtos.AdminInquiryAnswerRequest request
	) {
		CustomerInquiry inquiry = find(inquiryId);
		try {
			inquiry.answer(request.answer().trim(), request.adminMemo(), adminUserId, Instant.now());
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
		NotificationLog notification = notificationService.customerInquiryAnswered(inquiry);
		return toAdminResponse(inquiry, notification);
	}

	@Scheduled(cron = "0 20 3 * * *", zone = "Asia/Seoul")
	@Transactional
	public int deleteExpired() {
		Instant now = Instant.now();
		notificationLogRepository.anonymizeExpiredCustomerInquiryNotifications(now);
		return customerInquiryRepository.deleteExpired(now);
	}

	private CustomerInquiry find(UUID inquiryId) {
		return customerInquiryRepository.findById(inquiryId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));
	}

	private CustomerInquiryDtos.AdminCustomerInquiryResponse toAdminResponse(
		CustomerInquiry inquiry,
		NotificationLog notification
	) {
		return new CustomerInquiryDtos.AdminCustomerInquiryResponse(
			inquiry.getId(),
			inquiry.getCustomerName(),
			inquiry.getEmail(),
			inquiry.getPhone(),
			inquiry.getSubject(),
			inquiry.getMessage(),
			inquiry.getStatus(),
			inquiry.getConsentPolicyVersion(),
			inquiry.getConsentedAt(),
			inquiry.getRetentionExpiresAt(),
			inquiry.getAdminMemo(),
			inquiry.getAnswer(),
			inquiry.getHandledByAdminId(),
			inquiry.getAnsweredAt(),
			inquiry.getClosedAt(),
			inquiry.getCreatedAt(),
			inquiry.getUpdatedAt(),
			notification == null ? null : new CustomerInquiryDtos.InquiryNotificationResponse(
				notification.getId(),
				notification.getStatus(),
				notification.getFailureReason(),
				notification.getSentAt(),
				notification.getCreatedAt()
			)
		);
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
