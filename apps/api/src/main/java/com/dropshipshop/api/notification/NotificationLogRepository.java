package com.dropshipshop.api.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.notification.domain.NotificationStatus;
import jakarta.persistence.LockModeType;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

	interface SupplierInviteDispatchScope {
		UUID getSupplierId();

		UUID getSupplierInviteId();
	}

	List<NotificationLog> findAllByOrderByCreatedAtAsc();

	List<NotificationLog> findAllByStatusOrderByCreatedAtAsc(NotificationStatus status);

	boolean existsByOrderIdAndType(UUID orderId, com.dropshipshop.api.notification.domain.NotificationType type);

	Optional<NotificationLog> findFirstByCustomerInquiryIdOrderByCreatedAtDesc(UUID customerInquiryId);

	Optional<NotificationLog> findFirstBySupplierInviteIdOrderByCreatedAtDesc(UUID supplierInviteId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select log from NotificationLog log where log.id = :id")
	Optional<NotificationLog> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
		select log.supplierId as supplierId, log.supplierInviteId as supplierInviteId
		from NotificationLog log
		where log.id = :id
		""")
	Optional<SupplierInviteDispatchScope> findSupplierInviteDispatchScope(@Param("id") UUID id);

	@Query("""
		select log.id
		from NotificationLog log
		where log.supplierId is not null
			and log.supplierInviteId is null
			and log.recipientRetentionExpiresAt <= :now
			and log.recipientAnonymizedAt is null
		order by log.recipientRetentionExpiresAt asc, log.id asc
		""")
	List<UUID> findSupplierOperationalCleanupCandidateIds(@Param("now") Instant now);

	@Query("""
		select log.id
		from NotificationLog log
		where log.supplierId is not null
			and log.supplierInviteId is null
			and log.status = com.dropshipshop.api.notification.domain.NotificationStatus.PENDING
			and log.type in (
				com.dropshipshop.api.notification.domain.NotificationType.SUPPLIER_FULFILLMENT_REQUESTED,
				com.dropshipshop.api.notification.domain.NotificationType.SUPPLIER_PRODUCT_REVIEW_RESULT,
				com.dropshipshop.api.notification.domain.NotificationType.SUPPLIER_CLAIM_WORK_REQUESTED
			)
		order by log.createdAt asc, log.id asc
		""")
	List<UUID> findPendingSupplierOperationalIds(Pageable pageable);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		update NotificationLog log
		set log.recipient = null,
			log.recipientAnonymizedAt = :now
		where log.supplierInviteId = :supplierInviteId
		""")
	int anonymizeSupplierInviteRecipients(
		@Param("supplierInviteId") UUID supplierInviteId,
		@Param("now") Instant now
	);

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
