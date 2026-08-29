package com.dropshipshop.api.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "suppliers")
public class Supplier {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "contact_name", length = 100)
	private String contactName;

	@Column(length = 30)
	private String phone;

	@Column(length = 320)
	private String email;

	@Column(columnDefinition = "TEXT")
	private String memo;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SupplierStatus status = SupplierStatus.ACTIVE;

	@Column(name = "manager_user_id")
	private UUID managerUserId;

	@Column(name = "portal_enrolled_at")
	private Instant portalEnrolledAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "portal_status", nullable = false, length = 30)
	private SupplierPortalStatus portalStatus = SupplierPortalStatus.DISABLED;

	@Column(name = "contact_email_verified_at")
	private Instant contactEmailVerifiedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "portal_contract_status", nullable = false, length = 30)
	private SupplierPortalContractStatus portalContractStatus = SupplierPortalContractStatus.UNVERIFIED;

	@Column(name = "portal_contract_version", length = 100)
	private String portalContractVersion;

	@Column(name = "portal_contract_effective_at")
	private Instant portalContractEffectiveAt;

	@Column(name = "portal_contract_expires_at")
	private Instant portalContractExpiresAt;

	@Column(name = "portal_contract_verified_at")
	private Instant portalContractVerifiedAt;

	@Column(name = "portal_contract_verified_by_admin_id")
	private UUID portalContractVerifiedByAdminId;

	@Column(name = "contact_retention_expires_at")
	private Instant contactRetentionExpiresAt;

	@Column(name = "contact_anonymized_at")
	private Instant contactAnonymizedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Supplier() {
	}

	public Supplier(String name, String contactName, String phone, String email, String memo) {
		this.name = name;
		this.contactName = contactName;
		this.phone = phone;
		this.email = email;
		this.memo = memo;
	}

	public static Supplier portalApplicant(
		String name,
		String contactName,
		String phone,
		String email,
		String memo
	) {
		Supplier supplier = new Supplier(name, contactName, phone, email, memo);
		supplier.status = SupplierStatus.INACTIVE;
		supplier.portalEnrolledAt = Instant.now();
		supplier.portalStatus = SupplierPortalStatus.PENDING_ACTIVATION;
		supplier.portalContractStatus = SupplierPortalContractStatus.UNVERIFIED;
		return supplier;
	}

	public void enrollLegacyPortal(String normalizedEmail) {
		if (portalStatus != SupplierPortalStatus.DISABLED || managerUserId != null) {
			throw new IllegalStateException("Only a never-enrolled legacy supplier can enter portal activation");
		}
		email = Objects.requireNonNull(normalizedEmail, "normalizedEmail");
		portalEnrolledAt = Instant.now();
		contactEmailVerifiedAt = null;
		portalStatus = SupplierPortalStatus.PENDING_ACTIVATION;
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

	public void update(String name, String contactName, String phone, String email, String memo, SupplierStatus status) {
		updateLegacy(name, contactName, phone, email, memo, status);
	}

	public void updateLegacy(String name, String contactName, String phone, String email, String memo, SupplierStatus status) {
		if (portalEnrolledAt != null || portalStatus != SupplierPortalStatus.DISABLED || managerUserId != null) {
			throw new IllegalStateException("Portal supplier lifecycle must use dedicated commands");
		}
		this.name = name;
		this.contactName = contactName;
		this.phone = phone;
		this.email = email;
		this.memo = memo;
		this.status = status;
	}

	public void bindManager(UUID userId, Instant verifiedAt) {
		if (portalStatus != SupplierPortalStatus.PENDING_ACTIVATION || managerUserId != null) {
			throw new IllegalStateException("Supplier is not awaiting manager activation");
		}
		if (email == null || email.isBlank()) {
			throw new IllegalStateException("Supplier contact email is required");
		}
		managerUserId = Objects.requireNonNull(userId, "userId");
		contactEmailVerifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt");
		portalStatus = SupplierPortalStatus.ACTIVE;
	}

	public void suspendPortal(SupplierSalesAction salesAction) {
		if (portalStatus != SupplierPortalStatus.ACTIVE) {
			throw new IllegalStateException("Only an active supplier portal can be suspended");
		}
		portalStatus = SupplierPortalStatus.SUSPENDED;
		applySalesAction(salesAction);
	}

	public void reactivatePortal(Instant now) {
		reactivatePortal(now, SupplierSalesAction.KEEP);
	}

	public void reactivatePortal(Instant now, SupplierSalesAction salesAction) {
		if (portalStatus != SupplierPortalStatus.SUSPENDED) {
			throw new IllegalStateException("Only a suspended supplier portal can be reactivated");
		}
		if (managerUserId == null || contactEmailVerifiedAt == null || !hasTimeValidContract(now)) {
			throw new IllegalStateException("Supplier manager, verified email, and current contract are required");
		}
		portalStatus = SupplierPortalStatus.ACTIVE;
		applySalesAction(salesAction);
	}

	public void disablePortal(SupplierSalesAction salesAction) {
		if (portalStatus == SupplierPortalStatus.DISABLED) {
			throw new IllegalStateException("Supplier portal is already disabled");
		}
		managerUserId = null;
		portalStatus = SupplierPortalStatus.DISABLED;
		applySalesAction(salesAction);
	}

	public void disconnectManager(SupplierSalesAction salesAction) {
		if (portalStatus == SupplierPortalStatus.DISABLED) {
			throw new IllegalStateException("Disabled supplier portal cannot disconnect a manager");
		}
		managerUserId = null;
		contactEmailVerifiedAt = null;
		portalStatus = SupplierPortalStatus.PENDING_ACTIVATION;
		applySalesAction(salesAction);
	}

	public void changeContactEmail(String contactEmail, SupplierSalesAction salesAction) {
		if (portalStatus == SupplierPortalStatus.DISABLED) {
			throw new IllegalStateException("Disabled supplier portal cannot change its portal contact");
		}
		email = Objects.requireNonNull(contactEmail, "contactEmail");
		managerUserId = null;
		contactEmailVerifiedAt = null;
		portalStatus = SupplierPortalStatus.PENDING_ACTIVATION;
		applySalesAction(salesAction);
	}

	public void changeSalesStatus(SupplierStatus nextStatus, Instant now) {
		Objects.requireNonNull(nextStatus, "nextStatus");
		if (nextStatus == SupplierStatus.ACTIVE && portalEnrolledAt != null && !hasTimeValidContract(now)) {
			throw new IllegalStateException("A current verified contract is required to activate portal sales");
		}
		status = nextStatus;
	}

	public boolean hasTimeValidContract(Instant now) {
		Objects.requireNonNull(now, "now");
		return portalContractStatus == SupplierPortalContractStatus.VERIFIED
			&& portalContractEffectiveAt != null
			&& !portalContractEffectiveAt.isAfter(now)
			&& (portalContractExpiresAt == null || now.isBefore(portalContractExpiresAt));
	}

	public boolean isPortalAuthorityActive(Instant now) {
		Objects.requireNonNull(now, "now");
		if (portalStatus != SupplierPortalStatus.ACTIVE || managerUserId == null) {
			return false;
		}
		if (portalContractStatus == SupplierPortalContractStatus.EXPIRED
			|| portalContractStatus == SupplierPortalContractStatus.REVOKED) {
			return false;
		}
		return portalContractStatus == SupplierPortalContractStatus.UNVERIFIED
			|| hasTimeValidContract(now);
	}

	public boolean lazilyExpireContract(Instant now) {
		Objects.requireNonNull(now, "now");
		if (portalContractStatus != SupplierPortalContractStatus.VERIFIED
			|| portalContractExpiresAt == null
			|| now.isBefore(portalContractExpiresAt)) {
			return false;
		}
		portalContractStatus = SupplierPortalContractStatus.EXPIRED;
		status = SupplierStatus.INACTIVE;
		if (portalStatus == SupplierPortalStatus.ACTIVE) {
			portalStatus = SupplierPortalStatus.SUSPENDED;
		}
		return true;
	}

	public void verifyPortalContract(
		String contractVersion,
		Instant effectiveAt,
		Instant expiresAt,
		Instant verifiedAt,
		UUID verifiedByAdminId
	) {
		Objects.requireNonNull(contractVersion, "contractVersion");
		Objects.requireNonNull(effectiveAt, "effectiveAt");
		Objects.requireNonNull(verifiedAt, "verifiedAt");
		Objects.requireNonNull(verifiedByAdminId, "verifiedByAdminId");
		if (expiresAt != null && !expiresAt.isAfter(effectiveAt)) {
			throw new IllegalArgumentException("Contract expiry must be after its effective time");
		}
		portalContractStatus = SupplierPortalContractStatus.VERIFIED;
		portalContractVersion = contractVersion;
		portalContractEffectiveAt = effectiveAt;
		portalContractExpiresAt = expiresAt;
		portalContractVerifiedAt = verifiedAt;
		portalContractVerifiedByAdminId = verifiedByAdminId;
	}

	public void setContactRetentionExpiresAt(Instant expiresAt) {
		contactRetentionExpiresAt = expiresAt;
	}

	public void clearContactRetentionExpiresAt() {
		contactRetentionExpiresAt = null;
	}

	public void anonymizeContact(Instant anonymizedAt) {
		if (portalStatus != SupplierPortalStatus.DISABLED || status != SupplierStatus.INACTIVE || managerUserId != null) {
			throw new IllegalStateException("Supplier relationship is not eligible for contact anonymization");
		}
		contactName = null;
		phone = null;
		email = null;
		memo = null;
		contactEmailVerifiedAt = null;
		contactAnonymizedAt = Objects.requireNonNull(anonymizedAt, "anonymizedAt");
	}

	private void applySalesAction(SupplierSalesAction salesAction) {
		if (Objects.requireNonNull(salesAction, "salesAction") == SupplierSalesAction.PAUSE) {
			status = SupplierStatus.INACTIVE;
		}
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getContactName() {
		return contactName;
	}

	public String getPhone() {
		return phone;
	}

	public String getEmail() {
		return email;
	}

	public String getMemo() {
		return memo;
	}

	public SupplierStatus getStatus() {
		return status;
	}

	public UUID getManagerUserId() {
		return managerUserId;
	}

	public Instant getPortalEnrolledAt() {
		return portalEnrolledAt;
	}

	public boolean isPortalEnrolled() {
		return portalEnrolledAt != null;
	}

	public SupplierPortalStatus getPortalStatus() {
		return portalStatus;
	}

	public Instant getContactEmailVerifiedAt() {
		return contactEmailVerifiedAt;
	}

	public SupplierPortalContractStatus getPortalContractStatus() {
		return portalContractStatus;
	}

	public String getPortalContractVersion() {
		return portalContractVersion;
	}

	public Instant getPortalContractEffectiveAt() {
		return portalContractEffectiveAt;
	}

	public Instant getPortalContractExpiresAt() {
		return portalContractExpiresAt;
	}

	public Instant getPortalContractVerifiedAt() {
		return portalContractVerifiedAt;
	}

	public UUID getPortalContractVerifiedByAdminId() {
		return portalContractVerifiedByAdminId;
	}

	public Instant getContactRetentionExpiresAt() {
		return contactRetentionExpiresAt;
	}

	public Instant getContactAnonymizedAt() {
		return contactAnonymizedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
