package com.dropshipshop.api.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.dropshipshop.api.common.money.MoneyMath;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_options")
public class ProductOption {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(name = "additional_price", nullable = false)
	private long additionalPrice;

	@Column(name = "source_option_code", length = 100)
	private String sourceOptionCode;

	@Column(name = "source_additional_price")
	private Long sourceAdditionalPrice;

	@Column(name = "source_stock_quantity")
	private Long sourceStockQuantity;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductOptionStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "supplier_availability", nullable = false, length = 20)
	private SupplierAvailability supplierAvailability;

	@Enumerated(EnumType.STRING)
	@Column(name = "inventory_mode", nullable = false, length = 20)
	private InventoryMode inventoryMode;

	@Column(name = "on_hand_quantity")
	private Long onHandQuantity;

	@Column(name = "reserved_quantity", nullable = false)
	private long reservedQuantity;

	@Column(name = "inventory_version", nullable = false)
	private long inventoryVersion;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ProductOption() {
	}

	public ProductOption(Product product, String name, long additionalPrice, ProductOptionStatus status) {
		this(product, name, additionalPrice, status, null, null, null, 0);
	}

	public ProductOption(
		Product product,
		String name,
		long additionalPrice,
		ProductOptionStatus status,
		String sourceOptionCode,
		Long sourceAdditionalPrice,
		Long sourceStockQuantity,
		int sortOrder
	) {
		validatePrices(product, additionalPrice, sourceAdditionalPrice);
		this.product = product;
		this.name = name;
		this.additionalPrice = additionalPrice;
		this.status = status;
		this.sourceOptionCode = sourceOptionCode;
		this.sourceAdditionalPrice = sourceAdditionalPrice;
		this.sourceStockQuantity = sourceStockQuantity;
		this.sortOrder = sortOrder;
		this.supplierAvailability = SupplierAvailability.AVAILABLE;
		this.inventoryMode = product.getManagementChannel() == ProductManagementChannel.SUPPLIER_PORTAL
			? InventoryMode.TRACKED
			: InventoryMode.UNTRACKED;
		this.onHandQuantity = inventoryMode == InventoryMode.TRACKED ? 0L : null;
		this.reservedQuantity = 0;
		this.inventoryVersion = 0;
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}

	public void update(String name, long additionalPrice) {
		update(name, additionalPrice, sourceOptionCode, sourceAdditionalPrice, sourceStockQuantity, sortOrder);
	}

	public void update(
		String name,
		long additionalPrice,
		String sourceOptionCode,
		Long sourceAdditionalPrice,
		Long sourceStockQuantity,
		int sortOrder
	) {
		validatePrices(product, additionalPrice, sourceAdditionalPrice);
		this.name = name;
		this.additionalPrice = additionalPrice;
		this.sourceOptionCode = sourceOptionCode;
		this.sourceAdditionalPrice = sourceAdditionalPrice;
		this.sourceStockQuantity = sourceStockQuantity;
		this.sortOrder = sortOrder;
	}

	public void updateStatus(ProductOptionStatus status) {
		this.status = status;
	}

	public void updateInventory(
		SupplierAvailability supplierAvailability,
		InventoryMode inventoryMode,
		Long onHandQuantity
	) {
		SupplierAvailability nextAvailability = Objects.requireNonNull(supplierAvailability, "supplierAvailability");
		InventoryMode nextMode = Objects.requireNonNull(inventoryMode, "inventoryMode");
		if (nextMode == InventoryMode.TRACKED) {
			if (onHandQuantity == null || onHandQuantity < 0) {
				throw new IllegalArgumentException("Tracked inventory requires a non-negative on-hand quantity");
			}
			if (onHandQuantity < reservedQuantity) {
				throw new IllegalStateException("On-hand quantity cannot be lower than reserved quantity");
			}
		} else {
			if (onHandQuantity != null) {
				throw new IllegalArgumentException("Untracked inventory cannot have an on-hand quantity");
			}
			if (reservedQuantity != 0) {
				throw new IllegalStateException("Inventory with active reservations cannot become untracked");
			}
		}
		this.supplierAvailability = nextAvailability;
		this.inventoryMode = nextMode;
		this.onHandQuantity = onHandQuantity;
		incrementInventoryVersion();
	}

	public boolean isTracked() {
		return inventoryMode == InventoryMode.TRACKED;
	}

	public boolean canReserve(int quantity) {
		return quantity > 0
			&& supplierAvailability == SupplierAvailability.AVAILABLE
			&& (!isTracked() || getAvailableQuantity() >= quantity);
	}

	public void reserve(int quantity) {
		requireTrackedPositiveQuantity(quantity);
		if (!canReserve(quantity)) {
			throw new IllegalStateException("Insufficient available inventory");
		}
		reservedQuantity = Math.addExact(reservedQuantity, quantity);
		incrementInventoryVersion();
	}

	public void consumeReservation(int quantity) {
		requireTrackedPositiveQuantity(quantity);
		if (reservedQuantity < quantity) {
			throw new IllegalStateException("Reserved quantity is insufficient");
		}
		onHandQuantity = Math.subtractExact(onHandQuantity, quantity);
		reservedQuantity = Math.subtractExact(reservedQuantity, quantity);
		incrementInventoryVersion();
	}

	public void releaseReservation(int quantity) {
		requireTrackedPositiveQuantity(quantity);
		if (reservedQuantity < quantity) {
			throw new IllegalStateException("Reserved quantity is insufficient");
		}
		reservedQuantity = Math.subtractExact(reservedQuantity, quantity);
		incrementInventoryVersion();
	}

	public void reacquireAndConsume(int quantity) {
		requireTrackedPositiveQuantity(quantity);
		if (!canReserve(quantity)) {
			throw new IllegalStateException("Insufficient available inventory");
		}
		onHandQuantity = Math.subtractExact(onHandQuantity, quantity);
		incrementInventoryVersion();
	}

	public UUID getId() {
		return id;
	}

	public Product getProduct() {
		return product;
	}

	public String getName() {
		return name;
	}

	public long getAdditionalPrice() {
		return additionalPrice;
	}

	public ProductOptionStatus getStatus() {
		return status;
	}

	public SupplierAvailability getSupplierAvailability() {
		return supplierAvailability;
	}

	public InventoryMode getInventoryMode() {
		return inventoryMode;
	}

	public Long getOnHandQuantity() {
		return onHandQuantity;
	}

	public long getReservedQuantity() {
		return reservedQuantity;
	}

	public long getAvailableQuantity() {
		return isTracked() ? Math.subtractExact(onHandQuantity, reservedQuantity) : 0;
	}

	public long getInventoryVersion() {
		return inventoryVersion;
	}

	public String getSourceOptionCode() {
		return sourceOptionCode;
	}

	public Long getSourceAdditionalPrice() {
		return sourceAdditionalPrice;
	}

	public Long getSourceStockQuantity() {
		return sourceStockQuantity;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	private static void validatePrices(Product product, long additionalPrice, Long sourceAdditionalPrice) {
		MoneyMath.requireCustomerUnitPrice(additionalPrice, "additionalPrice");
		MoneyMath.requireCustomerUnitPrice(
			MoneyMath.addNonNegative(product.getBasePrice(), additionalPrice),
			"unitPrice"
		);
		if (sourceAdditionalPrice != null) {
			MoneyMath.requireSupplierUnitCost(sourceAdditionalPrice, "sourceAdditionalPrice");
		}
	}

	private void requireTrackedPositiveQuantity(int quantity) {
		if (!isTracked()) {
			throw new IllegalStateException("Inventory operation requires tracked inventory");
		}
		if (quantity <= 0) {
			throw new IllegalArgumentException("Inventory quantity must be positive");
		}
	}

	private void incrementInventoryVersion() {
		inventoryVersion = Math.incrementExact(inventoryVersion);
	}
}
