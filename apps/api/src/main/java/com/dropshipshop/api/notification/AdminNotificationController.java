package com.dropshipshop.api.notification;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
class AdminNotificationController {

	private final NotificationLogRepository notificationLogRepository;

	AdminNotificationController(NotificationLogRepository notificationLogRepository) {
		this.notificationLogRepository = notificationLogRepository;
	}

	@GetMapping
	NotificationDtos.AdminNotificationListResponse listNotifications() {
		return new NotificationDtos.AdminNotificationListResponse(
			notificationLogRepository.findAllByOrderByCreatedAtAsc()
				.stream()
				.map(log -> new NotificationDtos.AdminNotificationResponse(
					log.getId(),
					log.getOrderId(),
					log.getType(),
					log.getStatus(),
					log.getRecipient(),
					log.getTemplateKey(),
					log.getSentAt(),
					log.getCreatedAt()
				))
				.toList()
		);
	}
}
