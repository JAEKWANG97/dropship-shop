package com.dropshipshop.api.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.notification.domain.NotificationStatus;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

	List<NotificationLog> findAllByOrderByCreatedAtAsc();

	List<NotificationLog> findAllByStatusOrderByCreatedAtAsc(NotificationStatus status);

	boolean existsByOrderIdAndType(UUID orderId, com.dropshipshop.api.notification.domain.NotificationType type);

	Optional<NotificationLog> findFirstByCustomerInquiryIdOrderByCreatedAtDesc(UUID customerInquiryId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
		UPDATE notification_logs
		SET recipient = 'retention_cleanup',
			payload_snapshot = 'retention_cleanup',
			failure_reason = NULL,
			customer_inquiry_id = NULL
		WHERE customer_inquiry_id IN (
			SELECT id FROM customer_inquiries WHERE retention_expires_at <= :now
		)
		""", nativeQuery = true)
	int anonymizeExpiredCustomerInquiryNotifications(@Param("now") Instant now);
}
