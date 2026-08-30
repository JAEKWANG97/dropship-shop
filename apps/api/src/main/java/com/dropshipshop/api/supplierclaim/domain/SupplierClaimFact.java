package com.dropshipshop.api.supplierclaim.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.claim.domain.Claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "supplier_claim_facts")
public class SupplierClaimFact implements Persistable<UUID> {

	@Id
	private UUID id;

	@Transient
	private boolean newEntity = true;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "task_id", nullable = false, updatable = false)
	private SupplierClaimTask task;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "claim_id", nullable = false, updatable = false)
	private Claim claim;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false, updatable = false)
	private Supplier supplier;

	@Column(name = "actor_user_id", updatable = false)
	private UUID actorUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40, updatable = false)
	private SupplierClaimRequestedType type;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb", updatable = false)
	private String payload;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "corrects_fact_id", updatable = false)
	private SupplierClaimFact correctsFact;

	@Column(name = "request_hash", nullable = false, length = 128, updatable = false)
	private String requestHash;

	@Column(name = "idempotency_key", nullable = false, length = 200, updatable = false)
	private String idempotencyKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "result_snapshot", nullable = false, columnDefinition = "jsonb")
	private String resultSnapshot;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SupplierClaimFact() {
	}

	public SupplierClaimFact(
		SupplierClaimTask task,
		UUID actorUserId,
		String payload,
		SupplierClaimFact correctsFact,
		String requestHash,
		String idempotencyKey,
		Instant createdAt
	) {
		this.id = UUID.randomUUID();
		this.task = Objects.requireNonNull(task, "task");
		this.claim = task.getClaim();
		this.supplier = task.getSupplier();
		this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
		this.type = task.getRequestedType();
		this.payload = requireText(payload, "payload");
		this.correctsFact = correctsFact;
		if (correctsFact != null
			&& (!correctsFact.getTask().getId().equals(task.getId())
				|| correctsFact.getType() != type)) {
			throw new IllegalArgumentException("Correction target must belong to the same task and type");
		}
		this.requestHash = requireText(requestHash, "requestHash");
		this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
		this.resultSnapshot = "{}";
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	public void initializeResult(String resultSnapshot) {
		if (!"{}".equals(this.resultSnapshot)) {
			throw new IllegalStateException("Fact result is immutable");
		}
		this.resultSnapshot = requireText(resultSnapshot, "resultSnapshot");
	}

	public boolean matchesReplay(String requestHash) {
		return Objects.equals(this.requestHash, requestHash);
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
	public SupplierClaimTask getTask() { return task; }
	public Claim getClaim() { return claim; }
	public Supplier getSupplier() { return supplier; }
	public UUID getActorUserId() { return actorUserId; }
	public SupplierClaimRequestedType getType() { return type; }
	public String getPayload() { return payload; }
	public SupplierClaimFact getCorrectsFact() { return correctsFact; }
	public String getRequestHash() { return requestHash; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public String getResultSnapshot() { return resultSnapshot; }
	public Instant getCreatedAt() { return createdAt; }
}
