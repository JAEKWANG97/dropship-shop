package com.dropshipshop.api.supplierportal.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentHandoverActorType;
import com.dropshipshop.api.fulfillment.domain.FulfillmentHandoverReasonCode;

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

@Entity
@Table(name = "fulfillment_handover_histories")
public class FulfillmentHandoverHistory {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "fulfillment_id", nullable = false)
	private Fulfillment fulfillment;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor_type", nullable = false, length = 20)
	private FulfillmentHandoverActorType actorType;

	@Column(name = "actor_admin_id")
	private UUID actorAdminId;

	@Enumerated(EnumType.STRING)
	@Column(name = "reason_code", nullable = false, length = 50)
	private FulfillmentHandoverReasonCode reasonCode;

	@Column(length = 200)
	private String reason;

	@Column(name = "request_hash", length = 128)
	private String requestHash;

	@Column(name = "idempotency_key", length = 200)
	private String idempotencyKey;

	@Column(name = "result_snapshot", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String resultSnapshot;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected FulfillmentHandoverHistory() {
	}

	private FulfillmentHandoverHistory(
		Fulfillment fulfillment,
		FulfillmentHandoverActorType actorType,
		UUID actorAdminId,
		FulfillmentHandoverReasonCode reasonCode,
		String reason,
		String requestHash,
		String idempotencyKey,
		String resultSnapshot,
		Instant createdAt
	) {
		this.fulfillment = Objects.requireNonNull(fulfillment, "fulfillment");
		this.actorType = Objects.requireNonNull(actorType, "actorType");
		this.actorAdminId = actorAdminId;
		this.reasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
		this.reason = reason;
		this.requestHash = requestHash;
		this.idempotencyKey = idempotencyKey;
		this.resultSnapshot = resultSnapshot;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	public static FulfillmentHandoverHistory system(
		Fulfillment fulfillment,
		FulfillmentHandoverReasonCode reasonCode,
		Instant createdAt
	) {
		if (reasonCode == FulfillmentHandoverReasonCode.ADMIN_TAKEOVER) {
			throw new IllegalArgumentException("ADMIN_TAKEOVER requires an admin actor");
		}
		return new FulfillmentHandoverHistory(
			fulfillment,
			FulfillmentHandoverActorType.SYSTEM,
			null,
			reasonCode,
			null,
			null,
			null,
			null,
			createdAt
		);
	}

	public static FulfillmentHandoverHistory admin(
		Fulfillment fulfillment,
		UUID actorAdminId,
		FulfillmentHandoverReasonCode reasonCode,
		String reason,
		String requestHash,
		String idempotencyKey,
		String resultSnapshot,
		Instant createdAt
	) {
		if (reasonCode == FulfillmentHandoverReasonCode.ADMIN_TAKEOVER && (reason == null || reason.isBlank())) {
			throw new IllegalArgumentException("ADMIN_TAKEOVER requires a reason");
		}
		return new FulfillmentHandoverHistory(
			fulfillment,
			FulfillmentHandoverActorType.ADMIN,
			Objects.requireNonNull(actorAdminId, "actorAdminId"),
			reasonCode,
			reason,
			requestHash,
			idempotencyKey,
			resultSnapshot,
			createdAt
		);
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public boolean matchesReplay(String idempotencyKey, String requestHash) {
		return Objects.equals(this.idempotencyKey, idempotencyKey)
			&& Objects.equals(this.requestHash, requestHash);
	}

	public UUID getId() {
		return id;
	}

	public Fulfillment getFulfillment() {
		return fulfillment;
	}

	public FulfillmentHandoverActorType getActorType() {
		return actorType;
	}

	public UUID getActorAdminId() {
		return actorAdminId;
	}

	public FulfillmentHandoverReasonCode getReasonCode() {
		return reasonCode;
	}

	public String getReason() {
		return reason;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getResultSnapshot() {
		return resultSnapshot;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
