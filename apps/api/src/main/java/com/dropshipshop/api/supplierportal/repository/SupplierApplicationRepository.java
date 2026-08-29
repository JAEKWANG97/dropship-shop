package com.dropshipshop.api.supplierportal.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.supplierportal.domain.SupplierApplication;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationStatus;

public interface SupplierApplicationRepository extends JpaRepository<SupplierApplication, UUID> {

	Optional<SupplierApplication> findByIdempotencyKey(String idempotencyKey);

	Optional<SupplierApplication> findByIdAndReviewIdempotencyKey(UUID id, String reviewIdempotencyKey);

	Optional<SupplierApplication> findByApprovedSupplier_Id(UUID supplierId);

	boolean existsByApprovedSupplier_Id(UUID supplierId);

	boolean existsByRequestedExistingSupplier_Id(UUID supplierId);

	Page<SupplierApplication> findAllByStatus(SupplierApplicationStatus status, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select application from SupplierApplication application where application.id = :id")
	Optional<SupplierApplication> findByIdForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select application
		from SupplierApplication application
		where application.normalizedContactEmail = :normalizedContactEmail
			and application.status in (
				com.dropshipshop.api.supplierportal.domain.SupplierApplicationStatus.SUBMITTED,
				com.dropshipshop.api.supplierportal.domain.SupplierApplicationStatus.APPROVED
			)
		""")
	Optional<SupplierApplication> findActiveByNormalizedContactEmailForUpdate(
		@Param("normalizedContactEmail") String normalizedContactEmail
	);

	List<SupplierApplication> findTop100ByRetentionExpiresAtLessThanEqualAndAnonymizedAtIsNullOrderByRetentionExpiresAtAsc(
		Instant now
	);
}
