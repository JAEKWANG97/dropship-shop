package com.dropshipshop.api.notification;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.dropshipshop.api.email.EmailSendResult;
import com.dropshipshop.api.email.EmailSender;
import com.dropshipshop.api.notification.domain.NotificationChannel;
import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.notification.domain.NotificationStatus;
import com.dropshipshop.api.sms.SmsSendResult;
import com.dropshipshop.api.sms.SmsSender;
import com.dropshipshop.api.support.InquiryLookupTokenService;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.domain.SupplierInvite;
import com.dropshipshop.api.supplierportal.repository.SupplierInviteRepository;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.repository.SupplierRepository;

@Service
class NotificationDispatchListener {

	private static final String MESSAGE_MARKER = "message=";

	private final NotificationLogRepository notificationLogRepository;
	private final SmsSender smsSender;
	private final EmailSender emailSender;
	private final InquiryLookupTokenService inquiryLookupTokenService;
	private final String publicBaseUrl;
	private final SupplierPortalFeatureGate supplierPortalFeatureGate;
	private final SupplierPortalHasher supplierPortalHasher;
	private final SupplierRepository supplierRepository;
	private final SupplierInviteRepository supplierInviteRepository;

	NotificationDispatchListener(
		NotificationLogRepository notificationLogRepository,
		SmsSender smsSender,
		EmailSender emailSender,
		InquiryLookupTokenService inquiryLookupTokenService,
		SupplierPortalFeatureGate supplierPortalFeatureGate,
		SupplierPortalHasher supplierPortalHasher,
		SupplierRepository supplierRepository,
		SupplierInviteRepository supplierInviteRepository,
		@Value("${app.public-base-url:http://localhost:3000}") String publicBaseUrl
	) {
		this.notificationLogRepository = notificationLogRepository;
		this.smsSender = smsSender;
		this.emailSender = emailSender;
		this.inquiryLookupTokenService = inquiryLookupTokenService;
		this.supplierPortalFeatureGate = supplierPortalFeatureGate;
		this.supplierPortalHasher = supplierPortalHasher;
		this.supplierRepository = supplierRepository;
		this.supplierInviteRepository = supplierInviteRepository;
		this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void dispatch(NotificationDispatchRequested event) {
		dispatchNow(event.notificationId(), null);
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void dispatchSupplierInvite(SupplierInviteDispatchRequested event) {
		dispatchNow(event.notificationId(), event.inviteToken());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public NotificationLog dispatchNow(UUID notificationId) {
		return dispatchNow(notificationId, null);
	}

	private NotificationLog dispatchNow(UUID notificationId, String inviteToken) {
		NotificationLog log = notificationLogRepository.findById(notificationId)
			.orElseThrow();
		if (log.getStatus() != NotificationStatus.PENDING) {
			return log;
		}
		try {
			switch (log.getChannel()) {
				case SMS -> dispatchSms(log);
				case EMAIL -> dispatchEmail(log, inviteToken);
				default -> log.markSkipped("Unsupported notification channel: " + log.getChannel());
			}
		} catch (RuntimeException exception) {
			log.markFailed(log.getSupplierInviteId() == null ? failureReason(exception) : "EMAIL_PROVIDER_FAILURE");
		}
		return log;
	}

	private void dispatchSms(NotificationLog log) {
		if (isBlank(log.getRecipient())) {
			log.markSkipped("SMS recipient is missing");
			return;
		}
		SmsSendResult result = smsSender.sendTransactional(log.getRecipient(), message(log));
		markResult(log, result.successful(), result.skippedReason());
	}

	private void dispatchEmail(NotificationLog log, String inviteToken) {
		if (log.getSupplierInviteId() != null) {
			dispatchSupplierInvite(log, inviteToken);
			return;
		}
		if (isBlank(log.getRecipient()) || log.getCustomerInquiryId() == null) {
			log.markSkipped("Email recipient or inquiry id is missing");
			return;
		}
		String lookupUrl = "%s/support/inquiries/%s#token=%s".formatted(
			publicBaseUrl,
			log.getCustomerInquiryId(),
			inquiryLookupTokenService.token(log.getCustomerInquiryId())
		);
		EmailSendResult result = emailSender.sendTransactional(
			log.getRecipient(),
			"[코어블SAF] 고객 문의 답변이 등록되었습니다",
			"문의 답변\n\n%s\n\n답변 상태 확인\n%s".formatted(message(log), lookupUrl)
		);
		markResult(log, result.successful(), result.skippedReason());
	}

	private void dispatchSupplierInvite(NotificationLog log, String inviteToken) {
		if (!supplierPortalFeatureGate.isEnabled()) {
			log.markSkipped("PORTAL_NOT_RELEASED");
			return;
		}
		if (isBlank(log.getRecipient()) || isBlank(inviteToken)) {
			log.markSkipped("INVITE_TOKEN_UNAVAILABLE");
			return;
		}
		Supplier supplier = log.getSupplierId() == null ? null : supplierRepository
			.findByIdForUpdate(log.getSupplierId())
			.orElse(null);
		SupplierInvite invite = log.getSupplierInviteId() == null ? null : supplierInviteRepository
			.findByIdForUpdate(log.getSupplierInviteId())
			.orElse(null);
		Instant now = Instant.now();
		if (supplier == null || invite == null
			|| !supplier.getId().equals(invite.getSupplier().getId())
			|| supplier.getPortalStatus() != SupplierPortalStatus.PENDING_ACTIVATION
			|| supplier.getManagerUserId() != null
			|| supplier.getContactEmailVerifiedAt() != null
			|| !log.getRecipient().equals(supplier.getEmail())
			|| !log.getRecipient().equals(invite.getRecipientEmail())
			|| invite.getConsumedAt() != null
			|| invite.getRevokedAt() != null
			|| !now.isBefore(invite.getExpiresAt())
			|| !supplierPortalHasher.matches(invite.getTokenDigest(), supplierPortalHasher.tokenDigest(inviteToken))) {
			log.markSkipped("INVITE_STATE_CHANGED");
			return;
		}
		String activationUrl = "%s/supplier/activate#token=%s".formatted(publicBaseUrl, inviteToken);
		EmailSendResult result = emailSender.sendTransactional(
			log.getRecipient(),
			"[코어블SAF] 공급처 포털 초대",
			"공급처 포털 담당자 연결을 시작해 주세요.\n\n%s".formatted(activationUrl)
		);
		if (result.successful()) {
			log.markSent(now);
		} else {
			log.markSkipped("EMAIL_PROVIDER_SKIPPED");
		}
	}

	private void markResult(NotificationLog log, boolean successful, String skippedReason) {
		if (successful) {
			log.markSent(Instant.now());
		} else {
			log.markSkipped(skippedReason);
		}
	}

	private String message(NotificationLog log) {
		String payload = log.getPayloadSnapshot();
		int markerIndex = payload.indexOf(MESSAGE_MARKER);
		if (markerIndex < 0) {
			return payload;
		}
		return payload.substring(markerIndex + MESSAGE_MARKER.length());
	}

	private String failureReason(RuntimeException exception) {
		String message = exception.getMessage();
		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}
		return message;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
