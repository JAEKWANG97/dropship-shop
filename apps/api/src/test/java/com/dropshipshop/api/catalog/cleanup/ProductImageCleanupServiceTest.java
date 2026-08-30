package com.dropshipshop.api.catalog.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import com.dropshipshop.api.catalog.domain.ProductImageCleanupJob;
import com.dropshipshop.api.catalog.domain.ProductImageCleanupStatus;
import com.dropshipshop.api.catalog.repository.ProductImageCleanupJobRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.common.storage.FileStorage;

class ProductImageCleanupServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
	private static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
	private static final String OBJECT_KEY = "products/101/thumbnail.webp";

	private final ProductImageCleanupJobRepository cleanupJobRepository = mock(ProductImageCleanupJobRepository.class);
	private final ProductImageRepository imageRepository = mock(ProductImageRepository.class);
	private final FileStorage fileStorage = mock(FileStorage.class);
	private final ProductImageCleanupService service = new ProductImageCleanupService(
		cleanupJobRepository, imageRepository, fileStorage
	);

	@Test
	void completesCleanupAfterSuccessfulStorageDeletion() {
		ProductImageCleanupJob job = new ProductImageCleanupJob(OBJECT_KEY, PRODUCT_ID, NOW);
		when(cleanupJobRepository.findDueForUpdate(
			eq(ProductImageCleanupStatus.PENDING),
			eq(NOW),
			any(Pageable.class)
		)).thenReturn(List.of(job));

		int processed = service.processDueJobs(NOW);

		assertThat(processed).isEqualTo(1);
		assertThat(job.getStatus()).isEqualTo(ProductImageCleanupStatus.COMPLETED);
		assertThat(job.getCompletedAt()).isEqualTo(NOW);
		assertThat(job.getAttemptCount()).isZero();
		assertThat(job.getLastErrorCode()).isNull();
		verify(fileStorage).delete(OBJECT_KEY);
	}

	@Test
	void schedulesRetryWhenStorageDeletionFails() {
		ProductImageCleanupJob job = new ProductImageCleanupJob(OBJECT_KEY, PRODUCT_ID, NOW);
		when(cleanupJobRepository.findDueForUpdate(
			eq(ProductImageCleanupStatus.PENDING),
			eq(NOW),
			any(Pageable.class)
		)).thenReturn(List.of(job));
		doThrow(new IllegalStateException("storage unavailable"))
			.when(fileStorage).delete(OBJECT_KEY);

		int processed = service.processDueJobs(NOW);

		assertThat(processed).isEqualTo(1);
		assertThat(job.getStatus()).isEqualTo(ProductImageCleanupStatus.PENDING);
		assertThat(job.getAttemptCount()).isEqualTo(1);
		assertThat(job.getLastErrorCode()).isEqualTo("DELETE_FAILED");
		assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2 * 60));
		assertThat(job.getCompletedAt()).isNull();
	}

	@Test
	void doesNotDeleteBeforeRetryIsDueAndCompletesAtDueTime() {
		Instant retryAt = NOW.plusSeconds(2 * 60);
		ProductImageCleanupJob job = new ProductImageCleanupJob(OBJECT_KEY, PRODUCT_ID, NOW);
		job.scheduleRetry("DELETE_FAILED", retryAt);
		when(cleanupJobRepository.findDueForUpdate(
			eq(ProductImageCleanupStatus.PENDING),
			eq(retryAt.minusSeconds(1)),
			any(Pageable.class)
		)).thenReturn(List.of());
		when(cleanupJobRepository.findDueForUpdate(
			eq(ProductImageCleanupStatus.PENDING),
			eq(retryAt),
			any(Pageable.class)
		)).thenReturn(List.of(job));

		assertThat(service.processDueJobs(retryAt.minusSeconds(1))).isZero();
		verify(fileStorage, never()).delete(OBJECT_KEY);

		assertThat(service.processDueJobs(retryAt)).isEqualTo(1);
		assertThat(job.getStatus()).isEqualTo(ProductImageCleanupStatus.COMPLETED);
		assertThat(job.getAttemptCount()).isEqualTo(1);
		assertThat(job.getCompletedAt()).isEqualTo(retryAt);
		verify(fileStorage).delete(OBJECT_KEY);
	}

	@Test
	void completesWithoutDeletionWhenTheObjectKeyIsStillReferenced() {
		ProductImageCleanupJob job = new ProductImageCleanupJob(OBJECT_KEY, PRODUCT_ID, NOW);
		when(cleanupJobRepository.findDueForUpdate(
			eq(ProductImageCleanupStatus.PENDING),
			eq(NOW),
			any(Pageable.class)
		)).thenReturn(List.of(job));
		when(imageRepository.existsByStorageObjectKey(OBJECT_KEY)).thenReturn(true);

		assertThat(service.processDueJobs(NOW)).isEqualTo(1);

		assertThat(job.getStatus()).isEqualTo(ProductImageCleanupStatus.COMPLETED);
		assertThat(job.getCompletedAt()).isEqualTo(NOW);
		assertThat(job.getLastErrorCode()).isEqualTo("LIVE_REFERENCE");
		verify(fileStorage, never()).delete(OBJECT_KEY);
	}

	@Test
	void reopensLiveReferenceCompletionAndKeepsRepeatedEnqueueIdempotent() {
		Instant requeuedAt = NOW.plusSeconds(60);
		ProductImageCleanupJob job = new ProductImageCleanupJob(OBJECT_KEY, PRODUCT_ID, NOW);
		job.markCompletedWithoutDeletion("LIVE_REFERENCE", NOW);
		when(cleanupJobRepository.findByStorageObjectKeyForUpdate(OBJECT_KEY)).thenReturn(Optional.of(job));

		ProductImageCleanupJob first = service.enqueueCleanup(OBJECT_KEY, PRODUCT_ID, requeuedAt);
		ProductImageCleanupJob repeated = service.enqueueCleanup(OBJECT_KEY, PRODUCT_ID, requeuedAt);

		assertThat(first).isSameAs(job);
		assertThat(repeated).isSameAs(job);
		assertThat(job.getStatus()).isEqualTo(ProductImageCleanupStatus.PENDING);
		assertThat(job.getNextAttemptAt()).isEqualTo(requeuedAt);
		assertThat(job.getCompletedAt()).isNull();
		assertThat(job.getLastErrorCode()).isNull();
	}
}
