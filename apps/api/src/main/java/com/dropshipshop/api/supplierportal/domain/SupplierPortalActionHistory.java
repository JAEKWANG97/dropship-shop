package com.dropshipshop.api.supplierportal.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.domain.SupplierSalesAction;
import com.dropshipshop.api.catalog.domain.SupplierStatus;

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
@Table(name = "supplier_portal_action_histories")
public class SupplierPortalActionHistory {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false)
	private Supplier supplier;

	@Column(name = "actor_admin_id", nullable = false)
	private UUID actorAdminId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private SupplierPortalAction action;

	@Enumerated(EnumType.STRING)
	@Column(name = "before_portal_status", nullable = false, length = 30)
	private SupplierPortalStatus beforePortalStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "after_portal_status", nullable = false, length = 30)
	private SupplierPortalStatus afterPortalStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "before_sales_status", nullable = false, length = 20)
	private SupplierStatus beforeSalesStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "after_sales_status", nullable = false, length = 20)
	private SupplierStatus afterSalesStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "sales_action", length = 20)
	private SupplierSalesAction salesAction;

	@Column(length = 500)
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

	protected SupplierPortalActionHistory() {
	}

	public SupplierPortalActionHistory(
		Supplier supplier,
		UUID actorAdminId,
		SupplierPortalAction action,
		SupplierPortalStatus beforePortalStatus,
		SupplierPortalStatus afterPortalStatus,
		SupplierStatus beforeSalesStatus,
		SupplierStatus afterSalesStatus,
		SupplierSalesAction salesAction,
		String reason,
		String requestHash,
		String idempotencyKey,
		String resultSnapshot,
		Instant createdAt
	) {
		this.supplier = Objects.requireNonNull(supplier, "supplier");
		this.actorAdminId = Objects.requireNonNull(actorAdminId, "actorAdminId");
		this.action = Objects.requireNonNull(action, "action");
		this.beforePortalStatus = Objects.requireNonNull(beforePortalStatus, "beforePortalStatus");
		this.afterPortalStatus = Objects.requireNonNull(afterPortalStatus, "afterPortalStatus");
		this.beforeSalesStatus = Objects.requireNonNull(beforeSalesStatus, "beforeSalesStatus");
		this.afterSalesStatus = Objects.requireNonNull(afterSalesStatus, "afterSalesStatus");
		this.salesAction = salesAction;
		this.reason = Objects.requireNonNull(reason, "reason");
		this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
		this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
		this.resultSnapshot = Objects.requireNonNull(resultSnapshot, "resultSnapshot");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
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

	public void clearRelationshipReplayMaterial() {
		reason = null;
		requestHash = null;
		idempotencyKey = null;
		resultSnapshot = null;
	}

	public UUID getId() {
		return id;
	}

	public Supplier getSupplier() {
		return supplier;
	}

	public UUID getActorAdminId() {
		return actorAdminId;
	}

	public SupplierPortalAction getAction() {
		return action;
	}

	public SupplierPortalStatus getBeforePortalStatus() {
		return beforePortalStatus;
	}

	public SupplierPortalStatus getAfterPortalStatus() {
		return afterPortalStatus;
	}

	public SupplierStatus getBeforeSalesStatus() {
		return beforeSalesStatus;
	}

	public SupplierStatus getAfterSalesStatus() {
		return afterSalesStatus;
	}

	public SupplierSalesAction getSalesAction() {
		return salesAction;
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
