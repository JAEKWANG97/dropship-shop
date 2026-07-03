package com.dropshipshop.api.notification;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.dropshipshop.api.notification.domain.NotificationChannel;
import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.notification.domain.NotificationStatus;
import com.dropshipshop.api.sms.SmsSendResult;
import com.dropshipshop.api.sms.SmsSender;

@Service
class NotificationDispatchListener {

	private static final String MESSAGE_MARKER = "message=";

	private final NotificationLogRepository notificationLogRepository;
	private final SmsSender smsSender;

	NotificationDispatchListener(
		NotificationLogRepository notificationLogRepository,
		SmsSender smsSender
	) {
		this.notificationLogRepository = notificationLogRepository;
		this.smsSender = smsSender;
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
		if (log.getChannel() != NotificationChannel.SMS) {
			log.markSkipped("Unsupported notification channel: " + log.getChannel());
			return log;
		}
		if (isBlank(log.getRecipient())) {
			log.markSkipped("SMS recipient is missing");
			return log;
		}

		try {
			SmsSendResult result = smsSender.sendTransactional(log.getRecipient(), message(log));
			if (result.successful()) {
				log.markSent(Instant.now());
			} else {
				log.markSkipped(result.skippedReason());
			}
		} catch (RuntimeException exception) {
			log.markFailed(failureReason(exception));
		}
		return log;
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
