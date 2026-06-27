package com.dropshipshop.api.fulfillment.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.order.domain.CustomerOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "fulfillments")
public class Fulfillment {

	@Id
	@GeneratedValue
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false, unique = true)
	private CustomerOrder order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false)
	private Supplier supplier;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private FulfillmentStatus status = FulfillmentStatus.PENDING;

	@Column(name = "supplier_order_started_at")
	private Instant supplierOrderStartedAt;

	@Column(name = "supplier_order_number", length = 100)
	private String supplierOrderNumber;

	@Column(name = "ordered_address_snapshot", columnDefinition = "TEXT")
	private String orderedAddressSnapshot;

	@Column(name = "ordered_by_admin_id")
	private UUID orderedByAdminId;

	@Column(name = "ordered_at")
	private Instant orderedAt;

	@Column(name = "expected_ship_date")
	private LocalDate expectedShipDate;

	@Column(name = "supplier_response_memo", columnDefinition = "TEXT")
	private String supplierResponseMemo;

	@Column(name = "out_of_stock_reason", columnDefinition = "TEXT")
	private String outOfStockReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Fulfillment() {
	}

	public Fulfillment(CustomerOrder order) {
		this.order = order;
		this.supplier = order.getSupplier();
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

	public void startWork(Instant startedAt) {
		if (supplierOrderStartedAt != null) {
			throw new IllegalStateException("Supplier order work already started");
		}
		this.supplierOrderStartedAt = startedAt;
	}

	public void markOrdered(
		String supplierOrderNumber,
		String orderedAddressSnapshot,
		UUID orderedByAdminId,
		LocalDate expectedShipDate,
		String supplierResponseMemo,
		Instant orderedAt
	) {
		if (status != FulfillmentStatus.PENDING || supplierOrderStartedAt == null) {
			throw new IllegalStateException("Fulfillment can be ordered only after work start");
		}
		this.status = FulfillmentStatus.ORDERED;
		this.supplierOrderNumber = supplierOrderNumber;
		this.orderedAddressSnapshot = orderedAddressSnapshot;
		this.orderedByAdminId = orderedByAdminId;
		this.expectedShipDate = expectedShipDate;
		this.supplierResponseMemo = supplierResponseMemo;
		this.orderedAt = orderedAt;
	}

	public void markOutOfStock(String reason) {
		if (status != FulfillmentStatus.PENDING && status != FulfillmentStatus.ORDERED) {
			throw new IllegalStateException("Fulfillment out of stock can be marked only before shipment");
		}
		this.status = FulfillmentStatus.OUT_OF_STOCK;
		this.outOfStockReason = reason;
	}

	public UUID getId() {
		return id;
	}

	public FulfillmentStatus getStatus() {
		return status;
	}

	public Instant getSupplierOrderStartedAt() {
		return supplierOrderStartedAt;
	}

	public String getSupplierOrderNumber() {
		return supplierOrderNumber;
	}

	public UUID getOrderedByAdminId() {
		return orderedByAdminId;
	}

	public Instant getOrderedAt() {
		return orderedAt;
	}

	public LocalDate getExpectedShipDate() {
		return expectedShipDate;
	}

	public String getSupplierResponseMemo() {
		return supplierResponseMemo;
	}

	public String getOutOfStockReason() {
		return outOfStockReason;
	}
}
