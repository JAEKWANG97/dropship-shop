package com.dropshipshop.api.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.notification.domain.NotificationStatus;

@Service
class AdminNotificationService {

	private final NotificationLogRepository notificationLogRepository;
	private final ApplicationEventPublisher eventPublisher;

	AdminNotificationService(
		NotificationLogRepository notificationLogRepository,
		ApplicationEventPublisher eventPublisher
	) {
		this.notificationLogRepository = notificationLogRepository;
		this.eventPublisher = eventPublisher;
	}

	@Transactional(readOnly = true)
	List<NotificationLog> list(NotificationStatus status) {
		if (status == null) {
			return notificationLogRepository.findAllByOrderByCreatedAtAsc();
		}
		return notificationLogRepository.findAllByStatusOrderByCreatedAtAsc(status);
	}

	@Transactional
	NotificationLog retry(UUID notificationId) {
		NotificationLog log = notificationLogRepository.findById(notificationId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
		if (log.getStatus() != NotificationStatus.FAILED && log.getStatus() != NotificationStatus.SKIPPED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only failed or skipped notifications can be retried");
		}
		log.markPendingForRetry();
		notificationLogRepository.saveAndFlush(log);
		eventPublisher.publishEvent(new NotificationDispatchRequested(log.getId()));
		return log;
	}
}
