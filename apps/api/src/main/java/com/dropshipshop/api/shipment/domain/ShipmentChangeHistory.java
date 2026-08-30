package com.dropshipshop.api.shipment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "shipment_change_histories",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_shipment_change_histories_shipment_key",
		columnNames = {"shipment_id", "idempotency_key"}
	)
)
public class ShipmentChangeHistory {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shipment_id", nullable = false, updatable = false)
	private Shipment shipment;

	@Column(name = "actor_user_id", updatable = false)
	private UUID actorUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor_type", nullable = false, length = 20, updatable = false)
	private ShipmentActorType actorType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50, updatable = false)
	private ShipmentChangeAction action;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "before_snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
	private String beforeSnapshot;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "after_snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
	private String afterSnapshot;

	@Column(nullable = false, length = 200, updatable = false)
	private String reason;

	@Column(name = "evidence_observed_at", updatable = false)
	private Instant evidenceObservedAt;

	@Column(name = "request_hash", nullable = false, length = 128, updatable = false)
	private String requestHash;

	@Column(name = "idempotency_key", nullable = false, length = 200, updatable = false)
	private String idempotencyKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "result_snapshot", nullable = false, columnDefinition = "jsonb", updatable = false)
	private String resultSnapshot;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ShipmentChangeHistory() {
	}

	private ShipmentChangeHistory(
		Shipment shipment,
		UUID actorUserId,
		ShipmentActorType actorType,
		ShipmentChangeAction action,
		String beforeSnapshot,
		String afterSnapshot,
		String reason,
		Instant evidenceObservedAt,
		String requestHash,
		String idempotencyKey,
		String resultSnapshot,
		Instant createdAt
	) {
		this.shipment = Objects.requireNonNull(shipment, "shipment");
		this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
		this.actorType = Objects.requireNonNull(actorType, "actorType");
		this.action = Objects.requireNonNull(action, "action");
		validateActorAction(actorType, action);
		this.beforeSnapshot = requireText(beforeSnapshot, "beforeSnapshot", Integer.MAX_VALUE);
		this.afterSnapshot = requireText(afterSnapshot, "afterSnapshot", Integer.MAX_VALUE);
		this.reason = requireText(reason, "reason", 200);
		validateEvidence(action, evidenceObservedAt);
		this.evidenceObservedAt = evidenceObservedAt;
		this.requestHash = requireText(requestHash, "requestHash", 128);
		this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 200);
		this.resultSnapshot = resultSnapshot == null
			? null
			: requireText(resultSnapshot, "resultSnapshot", Integer.MAX_VALUE);
		this.createdAt = createdAt;
	}

	public static ShipmentChangeHistory command(
		Shipment shipment,
		UUID actorUserId,
		ShipmentActorType actorType,
		ShipmentChangeAction action,
		String beforeSnapshot,
		String afterSnapshot,
		String reason,
		Instant evidenceObservedAt,
		String requestHash,
		String idempotencyKey
	) {
		return new ShipmentChangeHistory(
			shipment, actorUserId, actorType, action, beforeSnapshot, afterSnapshot, reason,
			evidenceObservedAt, requestHash, idempotencyKey, null, null
		);
	}

	public static ShipmentChangeHistory command(
		Shipment shipment,
		UUID actorUserId,
		ShipmentActorType actorType,
		ShipmentChangeAction action,
		String beforeSnapshot,
		String afterSnapshot,
		String reason,
		Instant evidenceObservedAt,
		String requestHash,
		String idempotencyKey,
		String resultSnapshot,
		Instant createdAt
	) {
		return new ShipmentChangeHistory(
			shipment, actorUserId, actorType, action, beforeSnapshot, afterSnapshot, reason,
			evidenceObservedAt, requestHash, idempotencyKey, resultSnapshot, createdAt
		);
	}

	public void storeResult(String resultSnapshot) {
		if (this.resultSnapshot != null) {
			throw new IllegalStateException("Shipment change result is immutable");
		}
		this.resultSnapshot = requireText(resultSnapshot, "resultSnapshot", Integer.MAX_VALUE);
	}

	public boolean matchesReplay(String requestHash) {
		return Objects.equals(this.requestHash, requestHash);
	}

	public boolean matchesReplay(String idempotencyKey, String requestHash) {
		return Objects.equals(this.idempotencyKey, idempotencyKey) && matchesReplay(requestHash);
	}

	@PrePersist
	void prePersist() {
		if (resultSnapshot == null) {
			throw new IllegalStateException("Shipment change result must be stored before persistence");
		}
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	private static void validateActorAction(ShipmentActorType actorType, ShipmentChangeAction action) {
		boolean supplierAction = action == ShipmentChangeAction.SUPPLIER_CORRECTED;
		if ((actorType == ShipmentActorType.SUPPLIER) != supplierAction) {
			throw new IllegalArgumentException("Shipment change action does not match actor type");
		}
	}

	private static void validateEvidence(ShipmentChangeAction action, Instant evidenceObservedAt) {
		boolean requiresEvidence = action == ShipmentChangeAction.ADMIN_DELIVERY_COMPLETED
			|| action == ShipmentChangeAction.ADMIN_DELIVERED_AT_CORRECTED;
		if (requiresEvidence != (evidenceObservedAt != null)) {
			throw new IllegalArgumentException("Shipment change evidence does not match action");
		}
	}

	private static String requireText(String value, String field, int maxLength) {
		String normalized = Objects.requireNonNull(value, field).trim();
		if (normalized.isEmpty() || normalized.length() > maxLength) {
			throw new IllegalArgumentException(field + " must be non-blank and at most " + maxLength + " characters");
		}
		return normalized;
	}

	public UUID getId() { return id; }
	public Shipment getShipment() { return shipment; }
	public UUID getActorUserId() { return actorUserId; }
	public ShipmentActorType getActorType() { return actorType; }
	public ShipmentChangeAction getAction() { return action; }
	public String getBeforeSnapshot() { return beforeSnapshot; }
	public String getAfterSnapshot() { return afterSnapshot; }
	public String getReason() { return reason; }
	public Instant getEvidenceObservedAt() { return evidenceObservedAt; }
	public String getRequestHash() { return requestHash; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public String getResultSnapshot() { return resultSnapshot; }
	public Instant getCreatedAt() { return createdAt; }
}
