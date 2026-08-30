package com.dropshipshop.api.catalog.cleanup;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.domain.ProductImageCleanupJob;
import com.dropshipshop.api.catalog.domain.ProductImageCleanupStatus;
import com.dropshipshop.api.catalog.repository.ProductImageCleanupJobRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.common.storage.FileStorage;

@Service
public class ProductImageCleanupService {
	private static final String DELETE_FAILED = "DELETE_FAILED";
	private static final String LIVE_REFERENCE = "LIVE_REFERENCE";
	private static final int BATCH_SIZE = 100;

	private final ProductImageCleanupJobRepository cleanupJobRepository;
	private final ProductImageRepository imageRepository;
	private final FileStorage fileStorage;

	public ProductImageCleanupService(
		ProductImageCleanupJobRepository cleanupJobRepository,
		ProductImageRepository imageRepository,
		FileStorage fileStorage
	) {
		this.cleanupJobRepository = cleanupJobRepository;
		this.imageRepository = imageRepository;
		this.fileStorage = fileStorage;
	}

	@Transactional
	public ProductImageCleanupJob enqueueCleanup(String storageObjectKey, UUID subjectProductId, Instant now) {
		if (storageObjectKey == null || storageObjectKey.isBlank()) {
			throw new IllegalArgumentException("storageObjectKey is required");
		}
		Objects.requireNonNull(subjectProductId, "subjectProductId");
		Objects.requireNonNull(now, "now");
		// ponytail: enqueue callers hold the product write lock; use a database upsert if that contract changes.
		cleanupJobRepository.insertPendingIfAbsent(UUID.randomUUID(), storageObjectKey, subjectProductId, now);
		ProductImageCleanupJob job = cleanupJobRepository.findByStorageObjectKeyForUpdate(storageObjectKey)
			.orElseThrow(() -> new IllegalStateException("Cleanup job insert failed"));
		if (job.getStatus() == ProductImageCleanupStatus.COMPLETED
			&& LIVE_REFERENCE.equals(job.getLastErrorCode())) {
			job.reopen(now);
		}
		return job;
	}

	@Transactional(readOnly = true)
	public boolean hasCleanupJob(String storageObjectKey) {
		return cleanupJobRepository.existsByStorageObjectKey(storageObjectKey);
	}

	@Transactional
	public int processDueJobs(Instant now) {
		var jobs = cleanupJobRepository.findDueForUpdate(
			ProductImageCleanupStatus.PENDING,
			now,
			PageRequest.of(0, BATCH_SIZE)
		);
		for (ProductImageCleanupJob job : jobs) {
			if (imageRepository.existsByStorageObjectKey(job.getStorageObjectKey())) {
				job.markCompletedWithoutDeletion(LIVE_REFERENCE, now);
				continue;
			}
			try {
				fileStorage.delete(job.getStorageObjectKey());
				job.markCompleted(now);
			} catch (RuntimeException exception) {
				job.scheduleRetry(DELETE_FAILED, now.plus(retryDelay(job.getAttemptCount() + 1)));
			}
		}
		return jobs.size();
	}

	private static Duration retryDelay(int attemptNumber) {
		long minutes = Math.min(24 * 60L, 1L << Math.min(attemptNumber, 10));
		return Duration.ofMinutes(minutes);
	}
}
