package com.dropshipshop.api.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.notification.domain.NotificationLog;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

	List<NotificationLog> findAllByOrderByCreatedAtAsc();

	boolean existsByOrderIdAndType(UUID orderId, com.dropshipshop.api.notification.domain.NotificationType type);
}
