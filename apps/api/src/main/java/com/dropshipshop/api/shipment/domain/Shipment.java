package com.dropshipshop.api.shipment.domain;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.order.domain.CustomerOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "shipments")
public class Shipment {

	@Id
	@GeneratedValue
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false, unique = true)
	private CustomerOrder order;

	@Column(nullable = false, length = 100)
	private String carrier;

	@Column(name = "tracking_number", nullable = false, length = 100)
	private String trackingNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ShipmentStatus status = ShipmentStatus.SHIPPED;

	@Column(name = "shipped_at", nullable = false)
	private Instant shippedAt;

	@Column(name = "delivered_at")
	private Instant deliveredAt;

	@Column(name = "tracking_synced_at")
	private Instant trackingSyncedAt;

	@Column(name = "tracking_sync_failure_reason", columnDefinition = "TEXT")
	private String trackingSyncFailureReason;

	@Column(name = "manual_override", nullable = false)
	private boolean manualOverride = false;

	@Column(name = "manual_correction_reason", columnDefinition = "TEXT")
	private String manualCorrectionReason;

	@Column(name = "manual_corrected_by_admin_id")
	private UUID manualCorrectedByAdminId;

	@Column(name = "manual_corrected_at")
	private Instant manualCorrectedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Shipment() {
	}

	public Shipment(CustomerOrder order, String carrier, String trackingNumber, Instant shippedAt) {
		this.order = order;
		this.carrier = carrier;
		this.trackingNumber = trackingNumber;
		this.shippedAt = shippedAt;
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

	public UUID getId() {
		return id;
	}

	public CustomerOrder getOrder() {
		return order;
	}

	public String getCarrier() {
		return carrier;
	}

	public String getTrackingNumber() {
		return trackingNumber;
	}

	public ShipmentStatus getStatus() {
		return status;
	}

	public Instant getShippedAt() {
		return shippedAt;
	}

	public Instant getDeliveredAt() {
		return deliveredAt;
	}

	public Instant getTrackingSyncedAt() {
		return trackingSyncedAt;
	}

	public String getManualCorrectionReason() {
		return manualCorrectionReason;
	}

	public String getTrackingSyncFailureReason() {
		return trackingSyncFailureReason;
	}

	public boolean isManualOverride() {
		return manualOverride;
	}

	public UUID getManualCorrectedByAdminId() {
		return manualCorrectedByAdminId;
	}

	public Instant getManualCorrectedAt() {
		return manualCorrectedAt;
	}

	public boolean markDeliveredByTracking(Instant syncedAt) {
		this.trackingSyncedAt = syncedAt;
		this.trackingSyncFailureReason = null;
		if (status == ShipmentStatus.DELIVERED) {
			return false;
		}
		if (status != ShipmentStatus.SHIPPED) {
			return false;
		}
		this.status = ShipmentStatus.DELIVERED;
		this.deliveredAt = syncedAt;
		return true;
	}

	public void markTrackingSynced(Instant syncedAt) {
		this.trackingSyncedAt = syncedAt;
		this.trackingSyncFailureReason = null;
	}

	public void recordTrackingSyncFailure(Instant syncedAt, String failureReason) {
		this.trackingSyncedAt = syncedAt;
		this.trackingSyncFailureReason = failureReason;
	}

	public boolean markDeliveredByManualCorrection(Instant correctedAt, UUID adminUserId, String reason) {
		if (status != ShipmentStatus.SHIPPED && status != ShipmentStatus.DELIVERED) {
			throw new IllegalStateException("Shipment can be manually delivered only after shipment");
		}
		this.manualOverride = true;
		this.manualCorrectionReason = reason;
		this.manualCorrectedByAdminId = adminUserId;
		this.manualCorrectedAt = correctedAt;
		this.trackingSyncFailureReason = null;
		if (status == ShipmentStatus.DELIVERED) {
			return false;
		}
		this.status = ShipmentStatus.DELIVERED;
		this.deliveredAt = correctedAt;
		return true;
	}
}
