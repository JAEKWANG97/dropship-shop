package com.dropshipshop.api.fulfillment.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private FulfillmentChannel channel = FulfillmentChannel.COREABLE_MANUAL;

	@Column(name = "requested_at")
	private Instant requestedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "operational_owner", nullable = false, length = 20)
	private FulfillmentOperationalOwner operationalOwner = FulfillmentOperationalOwner.COREABLE;

	@Column(name = "pii_access_cutoff_at")
	private Instant piiAccessCutoffAt;

	@Column(name = "handed_over_at")
	private Instant handedOverAt;

	@Column(name = "handed_over_reason", length = 200)
	private String handedOverReason;

	@Column(name = "handed_over_by_admin_id")
	private UUID handedOverByAdminId;

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

	@Column(name = "purchase_provider", length = 30)
	private String purchaseProvider;

	@Enumerated(EnumType.STRING)
	@Column(name = "purchase_status", length = 40)
	private SupplierPurchaseStatus purchaseStatus;

	@Column(name = "expected_source_amount")
	private Long expectedSourceAmount;

	@Column(name = "actual_source_amount")
	private Long actualSourceAmount;

	@Column(name = "request_fingerprint", length = 64)
	private String requestFingerprint;

	@Column(name = "last_purchase_error", columnDefinition = "TEXT")
	private String lastPurchaseError;

	@Column(name = "purchase_synced_at")
	private Instant purchaseSyncedAt;

	@Column(name = "supplier_cancel_status", length = 40)
	private String supplierCancelStatus;

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

	public void routeToSupplierPortal(Instant requestedAt, Instant piiAccessCutoffAt) {
		Objects.requireNonNull(requestedAt, "requestedAt");
		Objects.requireNonNull(piiAccessCutoffAt, "piiAccessCutoffAt");
		if (piiAccessCutoffAt.isBefore(requestedAt)) {
			throw new IllegalArgumentException("PII access cutoff cannot precede the request time");
		}
		if (handedOverAt != null) {
			throw new IllegalStateException("Handed-over fulfillment cannot return to supplier ownership");
		}
		channel = FulfillmentChannel.SUPPLIER_PORTAL;
		operationalOwner = FulfillmentOperationalOwner.SUPPLIER;
		this.requestedAt = requestedAt;
		this.piiAccessCutoffAt = piiAccessCutoffAt;
	}

	public boolean shortenPiiAccessCutoffAt(Instant candidate) {
		Objects.requireNonNull(candidate, "candidate");
		if (channel != FulfillmentChannel.SUPPLIER_PORTAL || piiAccessCutoffAt == null) {
			throw new IllegalStateException("PII cutoff exists only for portal fulfillment");
		}
		if (!candidate.isBefore(piiAccessCutoffAt)) {
			return false;
		}
		piiAccessCutoffAt = candidate;
		return true;
	}

	public boolean isOpenPortalSupplierOwned() {
		return isPortalSupplierOwned()
			&& (status == FulfillmentStatus.PENDING || status == FulfillmentStatus.ORDERED);
	}

	public boolean isPortalSupplierOwned() {
		return channel == FulfillmentChannel.SUPPLIER_PORTAL
			&& operationalOwner == FulfillmentOperationalOwner.SUPPLIER;
	}

	public boolean handOverToCoreable(
		Instant handedOverAt,
		FulfillmentHandoverReasonCode reasonCode,
		UUID handedOverByAdminId
	) {
		if (!isOpenPortalSupplierOwned()) {
			return false;
		}
		return applyHandover(handedOverAt, reasonCode, reasonCode.name(), handedOverByAdminId);
	}

	public boolean handOverToCoreable(
		Instant handedOverAt,
		FulfillmentHandoverReasonCode reasonCode,
		String reason,
		UUID handedOverByAdminId
	) {
		if (!isOpenPortalSupplierOwned()) {
			return false;
		}
		return applyHandover(handedOverAt, reasonCode, reason, handedOverByAdminId);
	}

	public boolean handOverTerminalToCoreable(Instant handedOverAt) {
		boolean supplierOwned = isPortalSupplierOwned();
		boolean reportedShortageAwaitingTerminalReview = channel == FulfillmentChannel.SUPPLIER_PORTAL
			&& operationalOwner == FulfillmentOperationalOwner.COREABLE
			&& FulfillmentHandoverReasonCode.SUPPLIER_SHORTAGE_REPORTED.name().equals(handedOverReason);
		if (!supplierOwned && !reportedShortageAwaitingTerminalReview) {
			return false;
		}
		return applyHandover(
			handedOverAt,
			FulfillmentHandoverReasonCode.TERMINAL_STATE,
			FulfillmentHandoverReasonCode.TERMINAL_STATE.name(),
			null
		);
	}

	private boolean applyHandover(
		Instant handedOverAt,
		FulfillmentHandoverReasonCode reasonCode,
		String reason,
		UUID handedOverByAdminId
	) {
		this.operationalOwner = FulfillmentOperationalOwner.COREABLE;
		this.handedOverAt = Objects.requireNonNull(handedOverAt, "handedOverAt");
		this.handedOverReason = Objects.requireNonNull(reason, "reason");
		if (reason.length() > 200) {
			throw new IllegalArgumentException("Handover reason must be at most 200 characters");
		}
		Objects.requireNonNull(reasonCode, "reasonCode");
		this.handedOverByAdminId = handedOverByAdminId;
		return true;
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

	public void queueDomeggookPurchase(long expectedSourceAmount, String requestFingerprint) {
		this.purchaseProvider = "DOMEGGOOK";
		this.purchaseStatus = SupplierPurchaseStatus.READY;
		this.expectedSourceAmount = expectedSourceAmount;
		this.requestFingerprint = requestFingerprint;
		this.lastPurchaseError = null;
	}

	public void markPurchaseProcessing() {
		if (purchaseStatus != SupplierPurchaseStatus.READY && purchaseStatus != SupplierPurchaseStatus.FAILED) {
			throw new IllegalStateException("Supplier purchase can process only from ready or failed");
		}
		this.purchaseStatus = SupplierPurchaseStatus.PROCESSING;
		this.lastPurchaseError = null;
	}

	public void retryPurchase() {
		if (purchaseStatus != SupplierPurchaseStatus.FAILED) {
			throw new IllegalStateException("Only failed supplier purchases can be retried");
		}
		this.purchaseStatus = SupplierPurchaseStatus.READY;
		this.lastPurchaseError = null;
	}

	public void updateExpectedSourceAmount(long expectedSourceAmount) {
		this.expectedSourceAmount = expectedSourceAmount;
	}

	public void updateActualSourceAmount(long actualSourceAmount) {
		this.actualSourceAmount = actualSourceAmount;
	}

	public void markPurchaseOrdered(
		String supplierOrderNumber,
		long actualSourceAmount,
		String orderedAddressSnapshot,
		UUID orderedByAdminId,
		Instant orderedAt
	) {
		markOrdered(supplierOrderNumber, orderedAddressSnapshot, orderedByAdminId, null, "Domeggook automatic order", orderedAt);
		this.purchaseStatus = SupplierPurchaseStatus.ORDERED;
		this.actualSourceAmount = actualSourceAmount;
		this.purchaseSyncedAt = orderedAt;
		this.lastPurchaseError = null;
	}

	public void markPurchaseFailed(String error) {
		this.purchaseStatus = SupplierPurchaseStatus.FAILED;
		this.lastPurchaseError = error;
	}

	public void markPurchaseReconciliationRequired(String error) {
		this.purchaseStatus = SupplierPurchaseStatus.RECONCILIATION_REQUIRED;
		this.lastPurchaseError = error;
	}

	public void markPurchaseSynced(Instant syncedAt) {
		this.purchaseSyncedAt = syncedAt;
		this.lastPurchaseError = null;
	}

	public void recordPurchaseSyncFailure(String error, Instant syncedAt) {
		this.lastPurchaseError = error;
		this.purchaseSyncedAt = syncedAt;
	}

	public void markSupplierCancelRequested(String status, Instant syncedAt) {
		this.purchaseStatus = SupplierPurchaseStatus.CANCEL_REQUESTED;
		this.supplierCancelStatus = status;
		this.purchaseSyncedAt = syncedAt;
	}

	public void markSupplierCancelled(String status, Instant syncedAt) {
		this.purchaseStatus = SupplierPurchaseStatus.CANCELLED;
		this.supplierCancelStatus = status;
		this.purchaseSyncedAt = syncedAt;
		this.status = FulfillmentStatus.CANCELLED;
	}

	public void markSupplierCancelFailed(String error, Instant syncedAt) {
		this.supplierCancelStatus = "FAILED";
		this.lastPurchaseError = error;
		this.purchaseSyncedAt = syncedAt;
	}

	public UUID getId() {
		return id;
	}

	public CustomerOrder getOrder() {
		return order;
	}

	public Supplier getSupplier() {
		return supplier;
	}

	public FulfillmentStatus getStatus() {
		return status;
	}

	public FulfillmentChannel getChannel() {
		return channel;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public FulfillmentOperationalOwner getOperationalOwner() {
		return operationalOwner;
	}

	public Instant getPiiAccessCutoffAt() {
		return piiAccessCutoffAt;
	}

	public Instant getHandedOverAt() {
		return handedOverAt;
	}

	public String getHandedOverReason() {
		return handedOverReason;
	}

	public UUID getHandedOverByAdminId() {
		return handedOverByAdminId;
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

	public String getPurchaseProvider() {
		return purchaseProvider;
	}

	public SupplierPurchaseStatus getPurchaseStatus() {
		return purchaseStatus;
	}

	public Long getExpectedSourceAmount() {
		return expectedSourceAmount;
	}

	public Long getActualSourceAmount() {
		return actualSourceAmount;
	}

	public String getRequestFingerprint() {
		return requestFingerprint;
	}

	public String getLastPurchaseError() {
		return lastPurchaseError;
	}

	public Instant getPurchaseSyncedAt() {
		return purchaseSyncedAt;
	}

	public String getSupplierCancelStatus() {
		return supplierCancelStatus;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
