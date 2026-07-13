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

@Service
class NotificationDispatchListener {

	private static final String MESSAGE_MARKER = "message=";

	private final NotificationLogRepository notificationLogRepository;
	private final SmsSender smsSender;
	private final EmailSender emailSender;
	private final InquiryLookupTokenService inquiryLookupTokenService;
	private final String publicBaseUrl;

	NotificationDispatchListener(
		NotificationLogRepository notificationLogRepository,
		SmsSender smsSender,
		EmailSender emailSender,
		InquiryLookupTokenService inquiryLookupTokenService,
		@Value("${app.public-base-url:http://localhost:3000}") String publicBaseUrl
	) {
		this.notificationLogRepository = notificationLogRepository;
		this.smsSender = smsSender;
		this.emailSender = emailSender;
		this.inquiryLookupTokenService = inquiryLookupTokenService;
		this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void dispatch(NotificationDispatchRequested event) {
		dispatchNow(event.notificationId());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public NotificationLog dispatchNow(UUID notificationId) {
		NotificationLog log = notificationLogRepository.findById(notificationId)
			.orElseThrow();
		if (log.getStatus() != NotificationStatus.PENDING) {
			return log;
		}
		try {
			switch (log.getChannel()) {
				case SMS -> dispatchSms(log);
				case EMAIL -> dispatchEmail(log);
				default -> log.markSkipped("Unsupported notification channel: " + log.getChannel());
			}
		} catch (RuntimeException exception) {
			log.markFailed(failureReason(exception));
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

	private void dispatchEmail(NotificationLog log) {
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
