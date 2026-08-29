package com.dropshipshop.api.supplierportal.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.supplierportal.domain.SupplierInvite;

public interface SupplierInviteRepository extends JpaRepository<SupplierInvite, UUID> {

	@Query("select invite.supplier.id from SupplierInvite invite where invite.id = :id")
	Optional<UUID> findSupplierIdById(@Param("id") UUID id);

	Optional<SupplierInvite> findByTokenDigest(String tokenDigest);

	Optional<SupplierInvite> findBySupplier_IdAndIssuanceIdempotencyKey(
		UUID supplierId,
		String issuanceIdempotencyKey
	);

	boolean existsBySupplier_Id(UUID supplierId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select invite from SupplierInvite invite where invite.id = :id")
	Optional<SupplierInvite> findByIdForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select invite
		from SupplierInvite invite
		where invite.supplier.id = :supplierId
			and invite.consumedAt is null
			and invite.revokedAt is null
		order by invite.createdAt desc
		""")
	Optional<SupplierInvite> findOpenBySupplierIdForUpdate(@Param("supplierId") UUID supplierId);

	List<SupplierInvite> findTop100ByRecipientRetentionExpiresAtLessThanEqualAndRecipientAnonymizedAtIsNullOrderByRecipientRetentionExpiresAtAsc(
		Instant now
	);
}
