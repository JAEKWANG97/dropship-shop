package com.dropshipshop.api.shipment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OptimisticLock;
import org.hibernate.type.SqlTypes;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "shipments")
public class Shipment {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private CustomerOrder order;

	@Column(nullable = false, length = 100)
	private String carrier;

	@Column(name = "carrier_code", length = 40)
	private String carrierCode;

	@Column(name = "tracking_number", nullable = false, length = 100)
	private String trackingNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ShipmentStatus status = ShipmentStatus.SHIPPED;

	@Column(name = "shipped_at")
	private Instant shippedAt;

	@Column(name = "delivered_at")
	private Instant deliveredAt;

	@Column(name = "delivery_evidence_observed_at")
	private Instant deliveryEvidenceObservedAt;

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

	@Column(name = "idempotency_key", length = 200)
	private String idempotencyKey;

	@Column(name = "creation_request_hash", length = 128)
	private String creationRequestHash;

	@JdbcTypeCode(SqlTypes.JSON)
	@OptimisticLock(excluded = true)
	@Column(name = "creation_result_snapshot", columnDefinition = "jsonb")
	private String creationResultSnapshot;

	@Column(name = "registered_at", nullable = false)
	private Instant registeredAt;

	@Column(name = "registered_by_user_id")
	private UUID registeredByUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "registered_actor_type", length = 20)
	private ShipmentActorType registeredActorType;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	@OptimisticLock(excluded = true)
	private Instant updatedAt;

	protected Shipment() {
	}

	/** Compatibility constructor for pre-B-104 Coreable and Domeggook paths. */
	public Shipment(CustomerOrder order, String carrier, String trackingNumber, Instant shippedAt) {
		this.order = order;
		this.carrier = carrier;
		this.trackingNumber = trackingNumber;
		this.shippedAt = shippedAt;
		this.registeredAt = shippedAt;
	}

	public static Shipment portal(
		CustomerOrder order,
		String carrierCode,
		String carrierName,
		String trackingNumber,
		Instant registeredAt,
		UUID actorUserId,
		ShipmentActorType actorType,
		String idempotencyKey,
		String requestHash
	) {
		Shipment shipment = new Shipment();
		shipment.order = Objects.requireNonNull(order, "order");
		shipment.carrierCode = requireText(carrierCode, "carrierCode", 40);
		shipment.carrier = requireText(carrierName, "carrierName", 100);
		shipment.trackingNumber = requireText(trackingNumber, "trackingNumber", 100);
		shipment.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
		shipment.registeredByUserId = Objects.requireNonNull(actorUserId, "actorUserId");
		shipment.registeredActorType = Objects.requireNonNull(actorType, "actorType");
		shipment.idempotencyKey = requireText(idempotencyKey, "idempotencyKey", 200);
		shipment.creationRequestHash = requireText(requestHash, "requestHash", 128);
		shipment.status = ShipmentStatus.TRACKING_REGISTERED;
		return shipment;
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
		if (registeredAt == null) {
			registeredAt = shippedAt == null ? now : shippedAt;
		}
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}

	public void storeCreationResult(String resultSnapshot) {
		if (!isPortal()) {
			throw new IllegalStateException("Only portal shipment creation has a replay result");
		}
		if (creationResultSnapshot != null) {
			throw new IllegalStateException("Shipment creation result is immutable");
		}
		creationResultSnapshot = requireText(resultSnapshot, "resultSnapshot", Integer.MAX_VALUE);
	}

	public boolean matchesCreationReplay(String idempotencyKey, String requestHash) {
		return Objects.equals(this.idempotencyKey, idempotencyKey)
			&& Objects.equals(this.creationRequestHash, requestHash);
	}

	public boolean matchesCreationReplay(String requestHash) {
		return Objects.equals(this.creationRequestHash, requestHash);
	}

	public void correctTracking(String carrierCode, String carrierName, String trackingNumber) {
		requirePortalStatus(ShipmentStatus.TRACKING_REGISTERED, "Tracking can be corrected only before delivery");
		this.carrierCode = requireText(carrierCode, "carrierCode", 40);
		this.carrier = requireText(carrierName, "carrierName", 100);
		this.trackingNumber = requireText(trackingNumber, "trackingNumber", 100);
	}

	public void voidShipment() {
		requirePortalStatus(ShipmentStatus.TRACKING_REGISTERED, "Only a non-delivered portal shipment can be voided");
		status = ShipmentStatus.VOIDED;
	}

	public void completePortalDelivery(Instant deliveredAt, Instant evidenceObservedAt) {
		requirePortalStatus(ShipmentStatus.TRACKING_REGISTERED, "Only a registered portal shipment can be delivered");
		validateDeliveryEvidence(deliveredAt, evidenceObservedAt);
		this.deliveredAt = deliveredAt;
		this.deliveryEvidenceObservedAt = evidenceObservedAt;
		this.status = ShipmentStatus.DELIVERED;
	}

	public void reopenPortalDelivery() {
		if (!isPortalDeliveryEvidence()) {
			throw new IllegalStateException("Only an admin-evidenced portal delivery can be reopened");
		}
		this.status = ShipmentStatus.TRACKING_REGISTERED;
		this.deliveredAt = null;
		this.deliveryEvidenceObservedAt = null;
	}

	public void correctPortalDeliveredAt(Instant deliveredAt, Instant evidenceObservedAt) {
		if (!isPortalDeliveryEvidence()) {
			throw new IllegalStateException("Only an admin-evidenced portal delivery can be corrected");
		}
		validateDeliveryEvidence(deliveredAt, evidenceObservedAt);
		this.deliveredAt = deliveredAt;
		this.deliveryEvidenceObservedAt = evidenceObservedAt;
	}

	private void requirePortalStatus(ShipmentStatus requiredStatus, String message) {
		if (!isPortal() || status != requiredStatus) {
			throw new IllegalStateException(message);
		}
	}

	private void validateDeliveryEvidence(Instant deliveredAt, Instant evidenceObservedAt) {
		Instant delivered = Objects.requireNonNull(deliveredAt, "deliveredAt");
		Instant observed = Objects.requireNonNull(evidenceObservedAt, "evidenceObservedAt");
		if (delivered.isBefore(registeredAt) || observed.isBefore(delivered) || observed.isAfter(Instant.now())) {
			throw new IllegalArgumentException(
				"Delivery evidence must satisfy registeredAt <= deliveredAt <= evidenceObservedAt <= now"
			);
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
	public CustomerOrder getOrder() { return order; }
	public String getCarrier() { return carrier; }
	public String getCarrierName() { return carrier; }
	public String getCarrierCode() { return carrierCode; }
	public String getTrackingNumber() { return trackingNumber; }
	public ShipmentStatus getStatus() { return status; }
	public Instant getShippedAt() { return shippedAt; }
	public Instant getDeliveredAt() { return deliveredAt; }
	public Instant getDeliveryEvidenceObservedAt() { return deliveryEvidenceObservedAt; }
	public Instant getEvidenceObservedAt() { return deliveryEvidenceObservedAt; }
	public Instant getTrackingSyncedAt() { return trackingSyncedAt; }
	public String getManualCorrectionReason() { return manualCorrectionReason; }
	public String getTrackingSyncFailureReason() { return trackingSyncFailureReason; }
	public boolean isManualOverride() { return manualOverride; }
	public UUID getManualCorrectedByAdminId() { return manualCorrectedByAdminId; }
	public Instant getManualCorrectedAt() { return manualCorrectedAt; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public String getCreationRequestHash() { return creationRequestHash; }
	public String getCreationResultSnapshot() { return creationResultSnapshot; }
	public Instant getRegisteredAt() { return registeredAt; }
	public UUID getRegisteredByUserId() { return registeredByUserId; }
	public ShipmentActorType getRegisteredActorType() { return registeredActorType; }
	public long getVersion() { return version; }
	public Instant getCreatedAt() { return createdAt; }

	public boolean isPortal() { return idempotencyKey != null; }
	public boolean isVoided() { return status == ShipmentStatus.VOIDED; }
	public boolean countsTowardAllocation() { return !isVoided(); }
	public boolean isPortalDeliveryEvidence() {
		return isPortal() && status == ShipmentStatus.DELIVERED && deliveryEvidenceObservedAt != null;
	}

	public boolean markDeliveredByTracking(Instant syncedAt) {
		if (isPortal()) {
			return false;
		}
		this.trackingSyncedAt = syncedAt;
		this.trackingSyncFailureReason = null;
		if (status != ShipmentStatus.SHIPPED) {
			return false;
		}
		this.status = ShipmentStatus.DELIVERED;
		this.deliveredAt = syncedAt;
		return true;
	}

	public void markTrackingSynced(Instant syncedAt) {
		if (isPortal()) {
			throw new IllegalStateException("Portal shipments do not use carrier tracking sync");
		}
		this.trackingSyncedAt = syncedAt;
		this.trackingSyncFailureReason = null;
	}

	public void recordTrackingSyncFailure(Instant syncedAt, String failureReason) {
		if (isPortal()) {
			throw new IllegalStateException("Portal shipments do not use carrier tracking sync");
		}
		this.trackingSyncedAt = syncedAt;
		this.trackingSyncFailureReason = failureReason;
	}

	public boolean markDeliveredByManualCorrection(Instant correctedAt, UUID adminUserId, String reason) {
		if (isPortal()) {
			throw new IllegalStateException("Portal shipment delivery uses evidence-based admin commands");
		}
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
