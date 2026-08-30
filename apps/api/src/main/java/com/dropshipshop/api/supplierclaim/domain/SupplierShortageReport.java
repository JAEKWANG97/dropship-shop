package com.dropshipshop.api.supplierclaim.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.order.domain.CustomerOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "supplier_shortage_reports")
public class SupplierShortageReport implements Persistable<UUID> {

	@Id
	private UUID id;

	@Transient
	private boolean newEntity = true;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false, updatable = false)
	private CustomerOrder order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false, updatable = false)
	private Supplier supplier;

	@Column(name = "actor_user_id", updatable = false)
	private UUID actorUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "reason_code", nullable = false, length = 40, updatable = false)
	private SupplierShortageReasonCode reasonCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SupplierShortageStatus status;

	@Column(name = "request_hash", nullable = false, length = 128, updatable = false)
	private String requestHash;

	@Column(name = "idempotency_key", nullable = false, length = 200, updatable = false)
	private String idempotencyKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "submit_result_snapshot", nullable = false, columnDefinition = "jsonb")
	private String submitResultSnapshot;

	@Column(name = "reviewed_by_admin_id")
	private UUID reviewedByAdminId;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_reason_code", length = 40)
	private SupplierShortageReviewReasonCode reviewReasonCode;

	@Column(name = "review_request_hash", length = 128)
	private String reviewRequestHash;

	@Column(name = "review_idempotency_key", length = 200)
	private String reviewIdempotencyKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "review_result_snapshot", columnDefinition = "jsonb")
	private String reviewResultSnapshot;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SupplierShortageReport() {
	}

	public SupplierShortageReport(
		CustomerOrder order,
		Supplier supplier,
		UUID actorUserId,
		SupplierShortageReasonCode reasonCode,
		String requestHash,
		String idempotencyKey,
		Instant createdAt
	) {
		this.id = UUID.randomUUID();
		this.order = Objects.requireNonNull(order, "order");
		this.supplier = Objects.requireNonNull(supplier, "supplier");
		if (order.getSupplier() != supplier
			&& !Objects.equals(order.getSupplier().getId(), supplier.getId())) {
			throw new IllegalArgumentException("Shortage supplier must own the order");
		}
		this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
		this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
		this.status = SupplierShortageStatus.REPORTED;
		this.requestHash = requireText(requestHash, "requestHash");
		this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
		this.submitResultSnapshot = "{}";
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	public void initializeSubmitResult(String resultSnapshot) {
		if (!"{}".equals(submitResultSnapshot)) {
			throw new IllegalStateException("Shortage submit result is immutable");
		}
		this.submitResultSnapshot = requireText(resultSnapshot, "resultSnapshot");
	}

	public void review(
		SupplierShortageStatus targetStatus,
		UUID adminUserId,
		SupplierShortageReviewReasonCode reason,
		String requestHash,
		String idempotencyKey,
		Instant reviewedAt
	) {
		if (status != SupplierShortageStatus.REPORTED) {
			throw new IllegalStateException("Shortage report has already been reviewed");
		}
		if ((targetStatus == SupplierShortageStatus.APPROVED)
			!= (reason == SupplierShortageReviewReasonCode.SHORTAGE_CONFIRMED)
			|| targetStatus == SupplierShortageStatus.REPORTED) {
			throw new IllegalArgumentException("Shortage review action and reason do not match");
		}
		this.status = targetStatus;
		this.reviewedByAdminId = Objects.requireNonNull(adminUserId, "adminUserId");
		this.reviewedAt = Objects.requireNonNull(reviewedAt, "reviewedAt");
		this.reviewReasonCode = Objects.requireNonNull(reason, "reason");
		this.reviewRequestHash = requireText(requestHash, "requestHash");
		this.reviewIdempotencyKey = requireText(idempotencyKey, "idempotencyKey");
	}

	public void initializeReviewResult(String resultSnapshot) {
		if (reviewResultSnapshot != null) {
			throw new IllegalStateException("Shortage review result is immutable");
		}
		this.reviewResultSnapshot = requireText(resultSnapshot, "resultSnapshot");
	}

	public boolean matchesSubmitReplay(String requestHash) {
		return Objects.equals(this.requestHash, requestHash);
	}

	public boolean matchesReviewReplay(String idempotencyKey, String requestHash) {
		return Objects.equals(this.reviewIdempotencyKey, idempotencyKey)
			&& Objects.equals(this.reviewRequestHash, requestHash);
	}

	private static String requireText(String value, String field) {
		String normalized = Objects.requireNonNull(value, field).trim();
		if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
		return normalized;
	}

	@Override
	public UUID getId() { return id; }
	@Override
	public boolean isNew() { return newEntity; }
	@PostLoad
	@PostPersist
	void markNotNew() { newEntity = false; }
	public CustomerOrder getOrder() { return order; }
	public Supplier getSupplier() { return supplier; }
	public UUID getActorUserId() { return actorUserId; }
	public SupplierShortageReasonCode getReasonCode() { return reasonCode; }
	public SupplierShortageStatus getStatus() { return status; }
	public String getRequestHash() { return requestHash; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public String getSubmitResultSnapshot() { return submitResultSnapshot; }
	public UUID getReviewedByAdminId() { return reviewedByAdminId; }
	public Instant getReviewedAt() { return reviewedAt; }
	public SupplierShortageReviewReasonCode getReviewReasonCode() { return reviewReasonCode; }
	public String getReviewRequestHash() { return reviewRequestHash; }
	public String getReviewIdempotencyKey() { return reviewIdempotencyKey; }
	public String getReviewResultSnapshot() { return reviewResultSnapshot; }
	public Instant getCreatedAt() { return createdAt; }
}
