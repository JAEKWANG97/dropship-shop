package com.dropshipshop.api.notification;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.notification.domain.NotificationStatus;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
class AdminNotificationController {

	private final AdminNotificationService adminNotificationService;

	AdminNotificationController(AdminNotificationService adminNotificationService) {
		this.adminNotificationService = adminNotificationService;
	}

	@GetMapping
	NotificationDtos.AdminNotificationListResponse listNotifications(@RequestParam(required = false) NotificationStatus status) {
		return new NotificationDtos.AdminNotificationListResponse(
			adminNotificationService.list(status)
				.stream()
				.map(this::toResponse)
				.toList()
		);
	}

	@PostMapping("/{notificationId}/retry")
	NotificationDtos.AdminNotificationResponse retry(@PathVariable UUID notificationId) {
		return toResponse(adminNotificationService.retry(notificationId));
	}

	private NotificationDtos.AdminNotificationResponse toResponse(NotificationLog log) {
		return new NotificationDtos.AdminNotificationResponse(
			log.getId(),
			log.getOrderId(),
			log.getCustomerInquiryId(),
			log.getType(),
			log.getChannel(),
			log.getStatus(),
			log.getRecipient(),
			log.getTemplateKey(),
			log.getFailureReason(),
			log.getSentAt(),
			log.getCreatedAt()
		);
	}
}
