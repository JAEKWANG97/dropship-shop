package com.dropshipshop.api.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ProductImageCleanupJobTest {

	@Test
	void recordsRetriesWithoutLosingTheImmutableCleanupTarget() {
		Instant firstAttempt = Instant.parse("2026-08-30T00:00:00Z");
		UUID productId = UUID.randomUUID();
		ProductImageCleanupJob job = new ProductImageCleanupJob("products/key.webp", productId, firstAttempt);

		job.scheduleRetry("DELETE_FAILED", firstAttempt.plusSeconds(60));

		assertThat(job.getStorageObjectKey()).isEqualTo("products/key.webp");
		assertThat(job.getSubjectProductId()).isEqualTo(productId);
		assertThat(job.getStatus()).isEqualTo(ProductImageCleanupStatus.PENDING);
		assertThat(job.getAttemptCount()).isEqualTo(1);
		assertThat(job.getLastErrorCode()).isEqualTo("DELETE_FAILED");

		job.markCompleted(firstAttempt.plusSeconds(120));
		assertThat(job.getStatus()).isEqualTo(ProductImageCleanupStatus.COMPLETED);
		assertThat(job.getLastErrorCode()).isNull();
	}
}
