package com.dropshipshop.api.supplierfulfillment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.user.domain.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

@Entity
@Table(name = "supplier_pii_access_grants")
public class SupplierPiiAccessGrant implements Persistable<UUID> {

	@Id
	private UUID id;

	@Transient
	private boolean newEntity = true;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "claim_id", nullable = false)
	private Claim claim;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false)
	private Supplier supplier;

	@Column(nullable = false)
	private int sequence;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SupplierPiiGrantAction action;

	@Column(name = "access_until")
	private Instant accessUntil;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "previous_grant_id")
	private SupplierPiiAccessGrant previousGrant;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "acted_by_admin_id", nullable = false)
	private UserAccount actedByAdmin;

	@Column(nullable = false, length = 200)
	private String reason;

	@Column(name = "request_hash", nullable = false, length = 128)
	private String requestHash;

	@Column(name = "idempotency_key", nullable = false, length = 200)
	private String idempotencyKey;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "result_snapshot", nullable = false, columnDefinition = "jsonb")
	private String resultSnapshot;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SupplierPiiAccessGrant() {
	}

	public SupplierPiiAccessGrant(
		Claim claim,
		Supplier supplier,
		int sequence,
		SupplierPiiGrantAction action,
		Instant accessUntil,
		SupplierPiiAccessGrant previousGrant,
		UserAccount actedByAdmin,
		String reason,
		String requestHash,
		String idempotencyKey,
		String resultSnapshot,
		Instant createdAt
	) {
		this.id = UUID.randomUUID();
		this.claim = Objects.requireNonNull(claim, "claim");
		this.supplier = Objects.requireNonNull(supplier, "supplier");
		this.sequence = sequence;
		this.action = Objects.requireNonNull(action, "action");
		this.accessUntil = accessUntil;
		this.previousGrant = previousGrant;
		this.actedByAdmin = Objects.requireNonNull(actedByAdmin, "actedByAdmin");
		this.reason = Objects.requireNonNull(reason, "reason");
		this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
		this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
		this.resultSnapshot = Objects.requireNonNull(resultSnapshot, "resultSnapshot");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	public boolean matchesReplay(String requestHash) { return Objects.equals(this.requestHash, requestHash); }
	public void initializeResultSnapshot(String resultSnapshot) {
		if (!"{}".equals(this.resultSnapshot)) {
			throw new IllegalStateException("Grant result snapshot is already initialized");
		}
		this.resultSnapshot = Objects.requireNonNull(resultSnapshot, "resultSnapshot");
	}
	public boolean isActiveAt(Instant now) {
		return (action == SupplierPiiGrantAction.GRANTED || action == SupplierPiiGrantAction.EXTENDED)
			&& accessUntil != null && now.isBefore(accessUntil);
	}
	@Override
	public UUID getId() { return id; }
	@Override
	public boolean isNew() { return newEntity; }
	@PostLoad
	@PostPersist
	void markNotNew() { newEntity = false; }
	public Claim getClaim() { return claim; }
	public Supplier getSupplier() { return supplier; }
	public int getSequence() { return sequence; }
	public SupplierPiiGrantAction getAction() { return action; }
	public Instant getAccessUntil() { return accessUntil; }
	public SupplierPiiAccessGrant getPreviousGrant() { return previousGrant; }
	public UserAccount getActedByAdmin() { return actedByAdmin; }
	public String getReason() { return reason; }
	public String getRequestHash() { return requestHash; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public String getResultSnapshot() { return resultSnapshot; }
	public Instant getCreatedAt() { return createdAt; }
}
