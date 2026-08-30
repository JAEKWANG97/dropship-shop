package com.dropshipshop.api.catalog.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.catalog.domain.ProductImageCleanupJob;
import com.dropshipshop.api.catalog.domain.ProductImageCleanupStatus;

public interface ProductImageCleanupJobRepository extends JpaRepository<ProductImageCleanupJob, UUID> {

	Optional<ProductImageCleanupJob> findByStorageObjectKey(String storageObjectKey);

	boolean existsByStorageObjectKey(String storageObjectKey);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select job from ProductImageCleanupJob job where job.storageObjectKey = :storageObjectKey")
	Optional<ProductImageCleanupJob> findByStorageObjectKeyForUpdate(@Param("storageObjectKey") String storageObjectKey);

	@Modifying(flushAutomatically = true)
	@Query(value = """
		insert into product_image_cleanup_jobs (
			id, storage_object_key, subject_product_id, status, attempt_count,
			next_attempt_at, last_error_code, created_at, completed_at
		) select
			:id, :storageObjectKey, :subjectProductId, 'PENDING', 0,
			:now, null, :now, null
		where not exists (
			select 1 from product_image_cleanup_jobs where storage_object_key = :storageObjectKey
		)
		""", nativeQuery = true)
	int insertPendingIfAbsent(
		@Param("id") UUID id,
		@Param("storageObjectKey") String storageObjectKey,
		@Param("subjectProductId") UUID subjectProductId,
		@Param("now") Instant now
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select job from ProductImageCleanupJob job
		where job.status = :status and job.nextAttemptAt <= :now
		order by job.nextAttemptAt, job.createdAt, job.id
		""")
	List<ProductImageCleanupJob> findDueForUpdate(
		@Param("status") ProductImageCleanupStatus status,
		@Param("now") Instant now,
		Pageable pageable
	);
}
