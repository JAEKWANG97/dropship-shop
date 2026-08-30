package com.dropshipshop.api.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_image_cleanup_jobs")
public class ProductImageCleanupJob {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "storage_object_key", nullable = false, unique = true, length = 1000, updatable = false)
	private String storageObjectKey;

	@Column(name = "subject_product_id", nullable = false, updatable = false)
	private UUID subjectProductId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductImageCleanupStatus status = ProductImageCleanupStatus.PENDING;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "last_error_code", length = 100)
	private String lastErrorCode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected ProductImageCleanupJob() {
	}

	public ProductImageCleanupJob(String storageObjectKey, UUID subjectProductId, Instant nextAttemptAt) {
		if (storageObjectKey == null || storageObjectKey.isBlank()) {
			throw new IllegalArgumentException("storageObjectKey is required");
		}
		this.storageObjectKey = storageObjectKey;
		this.subjectProductId = Objects.requireNonNull(subjectProductId, "subjectProductId");
		this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public void markCompleted(Instant completedAt) {
		this.status = ProductImageCleanupStatus.COMPLETED;
		this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
		this.lastErrorCode = null;
	}

	public void markCompletedWithoutDeletion(String reasonCode, Instant completedAt) {
		requireErrorCode(reasonCode);
		this.status = ProductImageCleanupStatus.COMPLETED;
		this.completedAt = Objects.requireNonNull(completedAt, "completedAt");
		this.lastErrorCode = reasonCode;
	}

	public void reopen(Instant nextAttemptAt) {
		this.status = ProductImageCleanupStatus.PENDING;
		this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
		this.completedAt = null;
		this.lastErrorCode = null;
	}

	public void scheduleRetry(String errorCode, Instant nextAttemptAt) {
		requireErrorCode(errorCode);
		this.status = ProductImageCleanupStatus.PENDING;
		this.attemptCount = Math.addExact(attemptCount, 1);
		this.lastErrorCode = errorCode;
		this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
	}

	private static void requireErrorCode(String errorCode) {
		if (errorCode == null || errorCode.isBlank() || errorCode.length() > 100) {
			throw new IllegalArgumentException("A short allowlisted errorCode is required");
		}
	}

	public UUID getId() {
		return id;
	}

	public String getStorageObjectKey() {
		return storageObjectKey;
	}

	public UUID getSubjectProductId() {
		return subjectProductId;
	}

	public ProductImageCleanupStatus getStatus() {
		return status;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public String getLastErrorCode() {
		return lastErrorCode;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}
}
