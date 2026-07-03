package com.dropshipshop.api.refund.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.refund.domain.RefundStatus;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

	List<Refund> findAllByOrderByCreatedAtAsc();

	Optional<Refund> findByOrder_Id(UUID orderId);

	@Query("""
		select refund
		from Refund refund
		join refund.order customerOrder
		where customerOrder.user.id = :userId
			and refund.status in :statuses
		order by refund.createdAt desc
		""")
	List<Refund> findActiveByUserId(
		@Param("userId") UUID userId,
		@Param("statuses") List<RefundStatus> statuses
	);
}
