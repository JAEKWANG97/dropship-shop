package com.dropshipshop.api.notification;

import java.time.Instant;
import java.time.Duration;
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
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

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
	private final UserAccountRepository userAccountRepository;

	NotificationDispatchListener(
		NotificationLogRepository notificationLogRepository,
		SmsSender smsSender,
		EmailSender emailSender,
		InquiryLookupTokenService inquiryLookupTokenService,
		SupplierPortalFeatureGate supplierPortalFeatureGate,
		SupplierPortalHasher supplierPortalHasher,
		SupplierRepository supplierRepository,
		SupplierInviteRepository supplierInviteRepository,
		UserAccountRepository userAccountRepository,
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
		this.userAccountRepository = userAccountRepository;
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
		dispatchSupplierInviteNow(event.notificationId(), event.inviteToken());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public NotificationLog dispatchNow(UUID notificationId) {
		return dispatchNow(notificationId, null);
	}

	private NotificationLog dispatchNow(UUID notificationId, String inviteToken) {
		NotificationLog log = notificationLogRepository.findByIdForUpdate(notificationId)
			.orElseThrow();
		return dispatchLocked(log, inviteToken, null, null);
	}

	private NotificationLog dispatchSupplierInviteNow(UUID notificationId, String inviteToken) {
		NotificationLogRepository.SupplierInviteDispatchScope scope = notificationLogRepository
			.findSupplierInviteDispatchScope(notificationId)
			.orElseThrow();
		Supplier supplier = scope.getSupplierId() == null ? null
			: supplierRepository.findByIdForUpdate(scope.getSupplierId()).orElse(null);
		SupplierInvite invite = scope.getSupplierInviteId() == null ? null
			: supplierInviteRepository.findByIdForUpdate(scope.getSupplierInviteId()).orElse(null);
		NotificationLog log = notificationLogRepository.findByIdForUpdate(notificationId).orElseThrow();
		return dispatchLocked(log, inviteToken, supplier, invite);
	}

	private NotificationLog dispatchLocked(
		NotificationLog log,
		String inviteToken,
		Supplier lockedSupplier,
		SupplierInvite lockedInvite
	) {
		if (log.getStatus() != NotificationStatus.PENDING) {
			return log;
		}
		try {
			switch (log.getChannel()) {
				case SMS -> dispatchSms(log);
				case EMAIL -> dispatchEmail(log, inviteToken, lockedSupplier, lockedInvite);
				default -> log.markSkipped("Unsupported notification channel: " + log.getChannel());
			}
		} catch (RuntimeException exception) {
			log.markFailed(log.getSupplierInviteId() == null && !log.isSupplierOperational()
				? failureReason(exception) : "EMAIL_PROVIDER_FAILURE");
			log.scheduleOperationalCleanup(Instant.now());
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

	private void dispatchEmail(
		NotificationLog log,
		String inviteToken,
		Supplier lockedSupplier,
		SupplierInvite lockedInvite
	) {
		if (log.getSupplierInviteId() != null) {
			dispatchSupplierInvite(log, inviteToken, lockedSupplier, lockedInvite);
			return;
		}
		if (log.isSupplierOperational()) {
			dispatchSupplierOperational(log);
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

	private void dispatchSupplierOperational(NotificationLog log) {
		Instant now = Instant.now();
		if (log.getCreatedAt() == null || !now.isBefore(log.getCreatedAt().plus(Duration.ofDays(7)))) {
			log.markFailed("DELIVERY_WINDOW_EXPIRED");
			log.scheduleOperationalCleanup(now);
			return;
		}
		if (!supplierPortalFeatureGate.isEnabled()) {
			log.markSkipped("PORTAL_NOT_RELEASED");
			log.scheduleOperationalCleanup(now);
			return;
		}
		Supplier supplier = log.getSupplierId() == null ? null
			: supplierRepository.findByIdForUpdate(log.getSupplierId()).orElse(null);
		if (supplier == null
			|| supplier.getPortalStatus() != SupplierPortalStatus.ACTIVE
			|| supplier.getManagerUserId() == null
			|| supplier.getContactEmailVerifiedAt() == null
			|| !supplier.hasTimeValidContract(now)
			|| userAccountRepository.findByIdAndStatus(supplier.getManagerUserId(), UserStatus.ACTIVE).isEmpty()
			|| isBlank(log.getRecipient())
			|| !log.getRecipient().equals(supplier.getEmail())) {
			log.markSkipped("SUPPLIER_AUTHORIZATION_CHANGED");
			log.scheduleOperationalCleanup(now);
			return;
		}
		String portalUrl = publicBaseUrl + switch (log.getType()) {
			case SUPPLIER_FULFILLMENT_REQUESTED -> "/supplier/orders";
			case SUPPLIER_PRODUCT_REVIEW_RESULT -> "/supplier/products";
			case SUPPLIER_CLAIM_WORK_REQUESTED -> "/supplier/claim-tasks";
			default -> "/supplier";
		};
		String subject = switch (log.getType()) {
			case SUPPLIER_FULFILLMENT_REQUESTED -> "[코어블SAF] 새 출고 요청";
			case SUPPLIER_PRODUCT_REVIEW_RESULT -> "[코어블SAF] 상품 검토 결과";
			case SUPPLIER_CLAIM_WORK_REQUESTED -> "[코어블SAF] 클레임 처리 요청";
			default -> "[코어블SAF] 공급처 포털 알림";
		};
		EmailSendResult result = emailSender.sendTransactional(
			log.getRecipient(), subject, "%s\n\n공급처 포털\n%s".formatted(log.getPayloadSnapshot(), portalUrl)
		);
		if (result.successful()) {
			log.markSent(now);
		} else {
			log.markSkipped("EMAIL_PROVIDER_SKIPPED");
		}
		log.scheduleOperationalCleanup(now);
	}

	private void dispatchSupplierInvite(
		NotificationLog log,
		String inviteToken,
		Supplier supplier,
		SupplierInvite invite
	) {
		if (!supplierPortalFeatureGate.isEnabled()) {
			log.markSkipped("PORTAL_NOT_RELEASED");
			return;
		}
		if (isBlank(log.getRecipient()) || isBlank(inviteToken)) {
			log.markSkipped("INVITE_TOKEN_UNAVAILABLE");
			return;
		}
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
