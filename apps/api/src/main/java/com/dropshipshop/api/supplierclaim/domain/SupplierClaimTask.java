package com.dropshipshop.api.supplierclaim.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.order.domain.CustomerOrder;

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
@Table(name = "supplier_claim_tasks")
public class SupplierClaimTask implements Persistable<UUID> {

	@Id
	private UUID id;

	@Transient
	private boolean newEntity = true;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "claim_id", nullable = false, updatable = false)
	private Claim claim;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false, updatable = false)
	private CustomerOrder order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false, updatable = false)
	private Supplier supplier;

	@Enumerated(EnumType.STRING)
	@Column(name = "requested_type", nullable = false, length = 40, updatable = false)
	private SupplierClaimRequestedType requestedType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SupplierClaimTaskStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "instruction_code", nullable = false, length = 40, updatable = false)
	private SupplierClaimInstructionCode instructionCode;

	@Column(nullable = false, length = 200, updatable = false)
	private String instructions;

	@Column(name = "requested_by_admin_id", updatable = false)
	private UUID requestedByAdminId;

	@Column(name = "creation_request_hash", nullable = false, length = 128, updatable = false)
	private String creationRequestHash;

	@Column(name = "creation_idempotency_key", nullable = false, length = 200, updatable = false)
	private String creationIdempotencyKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "creation_result_snapshot", nullable = false, columnDefinition = "jsonb")
	private String creationResultSnapshot;

	@Column(name = "requested_at", nullable = false, updatable = false)
	private Instant requestedAt;

	@Column(name = "due_at", nullable = false, updatable = false)
	private Instant dueAt;

	@Column(name = "answered_at")
	private Instant answeredAt;

	@Column(name = "closed_by_admin_id")
	private UUID closedByAdminId;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "close_reason_code", length = 40)
	private SupplierClaimTaskCloseReasonCode closeReasonCode;

	@Column(name = "close_request_hash", length = 128)
	private String closeRequestHash;

	@Column(name = "close_idempotency_key", length = 200)
	private String closeIdempotencyKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "close_result_snapshot", columnDefinition = "jsonb")
	private String closeResultSnapshot;

	protected SupplierClaimTask() {
	}

	public SupplierClaimTask(
		Claim claim,
		Supplier supplier,
		SupplierClaimRequestedType requestedType,
		SupplierClaimInstructionCode instructionCode,
		UUID requestedByAdminId,
		String creationRequestHash,
		String creationIdempotencyKey,
		Instant requestedAt,
		Instant dueAt
	) {
		if (instructionCode.requestedType() != requestedType) {
			throw new IllegalArgumentException("Requested type and instruction code do not match");
		}
		Objects.requireNonNull(requestedAt, "requestedAt");
		Objects.requireNonNull(dueAt, "dueAt");
		if (!dueAt.isAfter(requestedAt) || dueAt.isAfter(requestedAt.plus(30, ChronoUnit.DAYS))) {
			throw new IllegalArgumentException("dueAt must be in the next 30 days");
		}
		this.id = UUID.randomUUID();
		this.claim = Objects.requireNonNull(claim, "claim");
		this.order = claim.getOrder();
		this.supplier = Objects.requireNonNull(supplier, "supplier");
		if (claim.getOrder().getSupplier() != supplier
			&& !Objects.equals(claim.getOrder().getSupplier().getId(), supplier.getId())) {
			throw new IllegalArgumentException("Task supplier must own the claim order");
		}
		this.requestedType = requestedType;
		this.status = SupplierClaimTaskStatus.OPEN;
		this.instructionCode = instructionCode;
		this.instructions = instructionCode.instructions();
		this.requestedByAdminId = Objects.requireNonNull(requestedByAdminId, "requestedByAdminId");
		this.creationRequestHash = requireText(creationRequestHash, "creationRequestHash");
		this.creationIdempotencyKey = requireText(creationIdempotencyKey, "creationIdempotencyKey");
		this.creationResultSnapshot = "{}";
		this.requestedAt = requestedAt;
		this.dueAt = dueAt;
	}

	public void initializeCreationResult(String resultSnapshot) {
		if (!"{}".equals(creationResultSnapshot)) {
			throw new IllegalStateException("Task creation result is immutable");
		}
		this.creationResultSnapshot = requireText(resultSnapshot, "resultSnapshot");
	}

	public void answer(Instant answeredAt) {
		if (status != SupplierClaimTaskStatus.OPEN) {
			throw new IllegalStateException("Only an open task can receive its first fact");
		}
		status = SupplierClaimTaskStatus.ANSWERED;
		this.answeredAt = Objects.requireNonNull(answeredAt, "answeredAt");
	}

	public void closeByAdmin(
		UUID adminUserId,
		SupplierClaimTaskCloseReasonCode reason,
		String requestHash,
		String idempotencyKey,
		Instant closedAt
	) {
		if (status == SupplierClaimTaskStatus.CLOSED || !reason.isAdminReason()) {
			throw new IllegalStateException("Task cannot be closed with this reason");
		}
		status = SupplierClaimTaskStatus.CLOSED;
		closedByAdminId = Objects.requireNonNull(adminUserId, "adminUserId");
		this.closedAt = Objects.requireNonNull(closedAt, "closedAt");
		closeReasonCode = reason;
		closeRequestHash = requireText(requestHash, "requestHash");
		closeIdempotencyKey = requireText(idempotencyKey, "idempotencyKey");
	}

	public void initializeCloseResult(String resultSnapshot) {
		if (closeResultSnapshot != null) {
			throw new IllegalStateException("Task close result is immutable");
		}
		closeResultSnapshot = requireText(resultSnapshot, "resultSnapshot");
	}

	public boolean closeBySystem(SupplierClaimTaskCloseReasonCode reason, Instant closedAt) {
		if (status == SupplierClaimTaskStatus.CLOSED) return false;
		if (reason != SupplierClaimTaskCloseReasonCode.DUE_AT_EXPIRED
			&& reason != SupplierClaimTaskCloseReasonCode.CLAIM_TERMINAL) {
			throw new IllegalArgumentException("System close reason is required");
		}
		Objects.requireNonNull(closedAt, "closedAt");
		if (reason == SupplierClaimTaskCloseReasonCode.DUE_AT_EXPIRED && closedAt.isBefore(dueAt)) {
			throw new IllegalStateException("Task deadline has not expired");
		}
		if (reason == SupplierClaimTaskCloseReasonCode.CLAIM_TERMINAL
			&& claim.getStatus() != com.dropshipshop.api.claim.domain.ClaimStatus.REJECTED
			&& claim.getStatus() != com.dropshipshop.api.claim.domain.ClaimStatus.COMPLETED
			&& claim.getStatus() != com.dropshipshop.api.claim.domain.ClaimStatus.WITHDRAWN) {
			throw new IllegalStateException("Claim is not terminal");
		}
		status = SupplierClaimTaskStatus.CLOSED;
		this.closedAt = closedAt;
		closeReasonCode = reason;
		return true;
	}

	public boolean matchesCreationReplay(String requestHash) {
		return Objects.equals(creationRequestHash, requestHash);
	}

	public boolean matchesCloseReplay(String idempotencyKey, String requestHash) {
		return Objects.equals(closeIdempotencyKey, idempotencyKey)
			&& Objects.equals(closeRequestHash, requestHash);
	}

	public boolean acceptsInput() {
		return status == SupplierClaimTaskStatus.OPEN || status == SupplierClaimTaskStatus.ANSWERED;
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
	public Claim getClaim() { return claim; }
	public CustomerOrder getOrder() { return order; }
	public Supplier getSupplier() { return supplier; }
	public SupplierClaimRequestedType getRequestedType() { return requestedType; }
	public SupplierClaimTaskStatus getStatus() { return status; }
	public SupplierClaimInstructionCode getInstructionCode() { return instructionCode; }
	public String getInstructions() { return instructions; }
	public UUID getRequestedByAdminId() { return requestedByAdminId; }
	public String getCreationRequestHash() { return creationRequestHash; }
	public String getCreationIdempotencyKey() { return creationIdempotencyKey; }
	public String getCreationResultSnapshot() { return creationResultSnapshot; }
	public Instant getRequestedAt() { return requestedAt; }
	public Instant getDueAt() { return dueAt; }
	public Instant getAnsweredAt() { return answeredAt; }
	public UUID getClosedByAdminId() { return closedByAdminId; }
	public Instant getClosedAt() { return closedAt; }
	public SupplierClaimTaskCloseReasonCode getCloseReasonCode() { return closeReasonCode; }
	public String getCloseRequestHash() { return closeRequestHash; }
	public String getCloseIdempotencyKey() { return closeIdempotencyKey; }
	public String getCloseResultSnapshot() { return closeResultSnapshot; }
}
