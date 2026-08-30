package com.dropshipshop.api.supplierfulfillment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SupplierPiiAccessGrantRepository extends JpaRepository<SupplierPiiAccessGrant, UUID> {

	Optional<SupplierPiiAccessGrant> findByClaim_IdAndIdempotencyKey(UUID claimId, String idempotencyKey);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<SupplierPiiAccessGrant> findFirstByClaim_IdOrderBySequenceDesc(UUID claimId);

	@Query("""
		select grant
		from SupplierPiiAccessGrant grant
		where grant.claim.order.id = :orderId
			and not exists (
				select later.id from SupplierPiiAccessGrant later
				where later.claim = grant.claim and later.sequence > grant.sequence
			)
		order by grant.createdAt desc
		""")
	List<SupplierPiiAccessGrant> findLatestStreamsByOrderId(@Param("orderId") UUID orderId);
}
