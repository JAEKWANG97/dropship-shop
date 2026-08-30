package com.dropshipshop.api.supplierproduct.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.dropshipshop.api.catalog.domain.InventoryMode;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierAvailability;

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
	name = "supplier_inventory_change_histories",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_supplier_inventory_subject_key",
		columnNames = {"subject_product_option_id", "idempotency_key"}
	)
)
public class SupplierInventoryChangeHistory {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_option_id")
	private ProductOption productOption;

	@Column(name = "subject_product_option_id", nullable = false, updatable = false)
	private UUID subjectProductOptionId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false, updatable = false)
	private Supplier supplier;

	@Column(name = "actor_user_id", updatable = false)
	private UUID actorUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "before_supplier_availability", nullable = false, length = 20, updatable = false)
	private SupplierAvailability beforeSupplierAvailability;

	@Enumerated(EnumType.STRING)
	@Column(name = "after_supplier_availability", nullable = false, length = 20, updatable = false)
	private SupplierAvailability afterSupplierAvailability;

	@Enumerated(EnumType.STRING)
	@Column(name = "before_inventory_mode", nullable = false, length = 20, updatable = false)
	private InventoryMode beforeInventoryMode;

	@Enumerated(EnumType.STRING)
	@Column(name = "after_inventory_mode", nullable = false, length = 20, updatable = false)
	private InventoryMode afterInventoryMode;

	@Column(name = "before_on_hand_quantity", updatable = false)
	private Long beforeOnHandQuantity;

	@Column(name = "after_on_hand_quantity", updatable = false)
	private Long afterOnHandQuantity;

	@Column(name = "before_reserved_quantity", nullable = false, updatable = false)
	private long beforeReservedQuantity;

	@Column(name = "after_reserved_quantity", nullable = false, updatable = false)
	private long afterReservedQuantity;

	@Column(name = "before_inventory_version", nullable = false, updatable = false)
	private long beforeInventoryVersion;

	@Column(name = "after_inventory_version", nullable = false, updatable = false)
	private long afterInventoryVersion;

	@Column(name = "request_hash", nullable = false, length = 128, updatable = false)
	private String requestHash;

	@Column(name = "idempotency_key", nullable = false, length = 200, updatable = false)
	private String idempotencyKey;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SupplierInventoryChangeHistory() {
	}

	public SupplierInventoryChangeHistory(
		ProductOption productOption,
		Supplier supplier,
		UUID actorUserId,
		SupplierAvailability beforeSupplierAvailability,
		SupplierAvailability afterSupplierAvailability,
		InventoryMode beforeInventoryMode,
		InventoryMode afterInventoryMode,
		Long beforeOnHandQuantity,
		Long afterOnHandQuantity,
		long beforeReservedQuantity,
		long afterReservedQuantity,
		long beforeInventoryVersion,
		long afterInventoryVersion,
		String requestHash,
		String idempotencyKey,
		Instant createdAt
	) {
		this.productOption = Objects.requireNonNull(productOption, "productOption");
		this.subjectProductOptionId = Objects.requireNonNull(productOption.getId(), "persisted productOption id");
		this.supplier = Objects.requireNonNull(supplier, "supplier");
		this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
		this.beforeSupplierAvailability = Objects.requireNonNull(beforeSupplierAvailability, "beforeSupplierAvailability");
		this.afterSupplierAvailability = Objects.requireNonNull(afterSupplierAvailability, "afterSupplierAvailability");
		this.beforeInventoryMode = Objects.requireNonNull(beforeInventoryMode, "beforeInventoryMode");
		this.afterInventoryMode = Objects.requireNonNull(afterInventoryMode, "afterInventoryMode");
		this.beforeOnHandQuantity = beforeOnHandQuantity;
		this.afterOnHandQuantity = afterOnHandQuantity;
		this.beforeReservedQuantity = beforeReservedQuantity;
		this.afterReservedQuantity = afterReservedQuantity;
		this.beforeInventoryVersion = beforeInventoryVersion;
		this.afterInventoryVersion = afterInventoryVersion;
		this.requestHash = Objects.requireNonNull(requestHash, "requestHash");
		this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	@PrePersist
	void prePersist() {
		if (subjectProductOptionId == null) {
			subjectProductOptionId = Objects.requireNonNull(productOption.getId(), "persisted productOption id");
		}
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

	public ProductOption getProductOption() {
		return productOption;
	}

	public UUID getSubjectProductOptionId() {
		return subjectProductOptionId;
	}

	public Supplier getSupplier() {
		return supplier;
	}

	public UUID getActorUserId() {
		return actorUserId;
	}

	public SupplierAvailability getBeforeSupplierAvailability() {
		return beforeSupplierAvailability;
	}

	public SupplierAvailability getAfterSupplierAvailability() {
		return afterSupplierAvailability;
	}

	public InventoryMode getBeforeInventoryMode() {
		return beforeInventoryMode;
	}

	public InventoryMode getAfterInventoryMode() {
		return afterInventoryMode;
	}

	public Long getBeforeOnHandQuantity() {
		return beforeOnHandQuantity;
	}

	public Long getAfterOnHandQuantity() {
		return afterOnHandQuantity;
	}

	public long getBeforeReservedQuantity() {
		return beforeReservedQuantity;
	}

	public long getAfterReservedQuantity() {
		return afterReservedQuantity;
	}

	public long getBeforeInventoryVersion() {
		return beforeInventoryVersion;
	}

	public long getAfterInventoryVersion() {
		return afterInventoryVersion;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
