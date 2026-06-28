package com.dropshipshop.api.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.notification.domain.NotificationStatus;
import com.dropshipshop.api.notification.domain.NotificationType;

final class NotificationDtos {

	private NotificationDtos() {
	}

	record AdminNotificationListResponse(List<AdminNotificationResponse> notifications) {
	}

	record AdminNotificationResponse(
		UUID notificationId,
		UUID orderId,
		NotificationType type,
		NotificationStatus status,
		String recipient,
		String templateKey,
		Instant sentAt,
		Instant createdAt
	) {
	}
}
