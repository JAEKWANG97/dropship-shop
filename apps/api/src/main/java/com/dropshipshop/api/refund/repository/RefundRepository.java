package com.dropshipshop.api.refund.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.refund.domain.RefundReason;
import com.dropshipshop.api.refund.domain.RefundStatus;

import jakarta.persistence.LockModeType;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

	List<Refund> findAllByOrderByCreatedAtAsc();

	Optional<Refund> findByOrder_Id(UUID orderId);

	Optional<Refund> findByPaymentGroup_IdAndRefundScope(UUID paymentGroupId, com.dropshipshop.api.refund.domain.RefundScope refundScope);

	List<Refund> findAllByPaymentGroup_IdOrderByCreatedAtAsc(UUID paymentGroupId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select refund from Refund refund where refund.id = :id")
	Optional<Refund> findByIdForUpdate(@Param("id") UUID id);

	boolean existsByPaymentGroup_Id(UUID paymentGroupId);

	boolean existsByOrder_IdAndCreatedAtAfter(UUID orderId, Instant createdAt);

	boolean existsByPaymentGroup_IdAndOrderIsNullAndCreatedAtAfter(UUID paymentGroupId, Instant createdAt);

	@Query("select refund.paymentGroup.id from Refund refund where refund.id = :refundId")
	Optional<UUID> findPaymentGroupIdById(@Param("refundId") UUID refundId);

	@Query("""
		select refund.order.id as orderId,
			refund.paymentGroup.id as paymentGroupId,
			refund.reason as reason
		from Refund refund
		where refund.id = :refundId
		""")
	Optional<RefundLockTarget> findLockTargetById(@Param("refundId") UUID refundId);

	@Query("""
		select refund
		from Refund refund
		join refund.paymentGroup paymentGroup
		where paymentGroup.user.id = :userId
			and refund.status in :statuses
		order by refund.createdAt desc
		""")
	List<Refund> findActiveByUserId(
		@Param("userId") UUID userId,
		@Param("statuses") List<RefundStatus> statuses
	);

	interface RefundLockTarget {
		UUID getOrderId();

		UUID getPaymentGroupId();

		RefundReason getReason();
	}
}
