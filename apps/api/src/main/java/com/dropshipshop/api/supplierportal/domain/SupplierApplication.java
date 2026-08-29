package com.dropshipshop.api.supplierportal.domain;

import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.dropshipshop.api.catalog.domain.Supplier;

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
@Table(name = "supplier_applications")
public class SupplierApplication {

	private static final long APPLICATION_RETENTION_DAYS = 90;

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "supplier_name", length = 100)
	private String supplierName;

	@Column(name = "contact_name", length = 100)
	private String contactName;

	@Column(name = "contact_email", length = 320)
	private String contactEmail;

	@Column(name = "normalized_contact_email", length = 320)
	private String normalizedContactEmail;

	@Column(name = "contact_phone", length = 30)
	private String contactPhone;

	@Column(columnDefinition = "TEXT")
	private String memo;

	@Column(name = "idempotency_key", length = 200)
	private String idempotencyKey;

	@Column(name = "request_hash", length = 128)
	private String requestHash;

	@Column(name = "consent_policy_version", nullable = false, length = 50)
	private String consentPolicyVersion;

	@Column(name = "consented_at", nullable = false)
	private Instant consentedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private SupplierApplicationStatus status = SupplierApplicationStatus.SUBMITTED;

	@Column(name = "reviewed_by_admin_id")
	private UUID reviewedByAdminId;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_reason_code", length = 60)
	private SupplierApplicationReviewReasonCode reviewReasonCode;

	@Column(name = "review_reason", length = 500)
	private String reviewReason;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "approved_supplier_id", unique = true)
	private Supplier approvedSupplier;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_action", length = 20)
	private SupplierApplicationReviewAction reviewAction;

	@Enumerated(EnumType.STRING)
	@Column(name = "approval_mode", length = 30)
	private SupplierApplicationApprovalMode approvalMode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requested_existing_supplier_id")
	private Supplier requestedExistingSupplier;

	@Column(name = "review_idempotency_key", length = 200)
	private String reviewIdempotencyKey;

	@Column(name = "review_request_hash", length = 128)
	private String reviewRequestHash;

	@Column(name = "review_result_snapshot", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String reviewResultSnapshot;

	@Column(name = "retention_expires_at")
	private Instant retentionExpiresAt;

	@Column(name = "anonymized_at")
	private Instant anonymizedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SupplierApplication() {
	}

	private SupplierApplication(
		String supplierName,
		String contactName,
		String contactEmail,
		String normalizedContactEmail,
		String contactPhone,
		String memo,
		String idempotencyKey,
		String requestHash,
		String consentPolicyVersion,
		Instant now,
		Duration retention
	) {
		if (retention.isZero() || retention.isNegative()) {
			throw new IllegalArgumentException("Application retention must be positive");
		}
		this.supplierName = supplierName;
		this.contactName = contactName;
		this.contactEmail = contactEmail;
		this.normalizedContactEmail = normalizedContactEmail;
		this.contactPhone = contactPhone;
		this.memo = memo;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.consentPolicyVersion = consentPolicyVersion;
		this.consentedAt = now;
		this.retentionExpiresAt = now.plus(retention);
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static SupplierApplication submit(
		String supplierName,
		String contactName,
		String contactEmail,
		String normalizedContactEmail,
		String contactPhone,
		String memo,
		String idempotencyKey,
		String requestHash,
		String consentPolicyVersion,
		Instant now
	) {
		return submit(
			supplierName,
			contactName,
			contactEmail,
			normalizedContactEmail,
			contactPhone,
			memo,
			idempotencyKey,
			requestHash,
			consentPolicyVersion,
			now,
			Duration.ofDays(APPLICATION_RETENTION_DAYS)
		);
	}

	public static SupplierApplication submit(
		String supplierName,
		String contactName,
		String contactEmail,
		String normalizedContactEmail,
		String contactPhone,
		String memo,
		String idempotencyKey,
		String requestHash,
		String consentPolicyVersion,
		Instant now,
		Duration retention
	) {
		return new SupplierApplication(
			Objects.requireNonNull(supplierName, "supplierName"),
			Objects.requireNonNull(contactName, "contactName"),
			Objects.requireNonNull(contactEmail, "contactEmail"),
			Objects.requireNonNull(normalizedContactEmail, "normalizedContactEmail"),
			contactPhone,
			memo,
			Objects.requireNonNull(idempotencyKey, "idempotencyKey"),
			Objects.requireNonNull(requestHash, "requestHash"),
			Objects.requireNonNull(consentPolicyVersion, "consentPolicyVersion"),
			Objects.requireNonNull(now, "now"),
			Objects.requireNonNull(retention, "retention")
		);
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
		if (retentionExpiresAt == null && status == SupplierApplicationStatus.SUBMITTED) {
			retentionExpiresAt = createdAt.plus(APPLICATION_RETENTION_DAYS, ChronoUnit.DAYS);
		}
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}

	public void approve(
		Supplier approvedSupplier,
		SupplierApplicationApprovalMode approvalMode,
		Supplier requestedExistingSupplier,
		UUID reviewedByAdminId,
		SupplierApplicationReviewReasonCode reasonCode,
		String reviewReason,
		String reviewIdempotencyKey,
		String reviewRequestHash,
		String reviewResultSnapshot,
		Instant now
	) {
		ensureReviewable(now);
		if (reasonCode != SupplierApplicationReviewReasonCode.APPLICATION_APPROVED) {
			throw new IllegalArgumentException("Approval requires APPLICATION_APPROVED reason code");
		}
		if (approvalMode == SupplierApplicationApprovalMode.CREATE_NEW && requestedExistingSupplier != null) {
			throw new IllegalArgumentException("CREATE_NEW cannot target an existing supplier");
		}
		if (approvalMode == SupplierApplicationApprovalMode.LINK_EXISTING && requestedExistingSupplier == null) {
			throw new IllegalArgumentException("LINK_EXISTING requires an existing supplier");
		}
		status = SupplierApplicationStatus.APPROVED;
		this.approvedSupplier = Objects.requireNonNull(approvedSupplier, "approvedSupplier");
		this.approvalMode = Objects.requireNonNull(approvalMode, "approvalMode");
		this.requestedExistingSupplier = requestedExistingSupplier;
		this.reviewedByAdminId = Objects.requireNonNull(reviewedByAdminId, "reviewedByAdminId");
		this.reviewReasonCode = reasonCode;
		this.reviewReason = reviewReason;
		this.reviewedAt = now;
		this.reviewAction = SupplierApplicationReviewAction.APPROVE;
		this.reviewIdempotencyKey = Objects.requireNonNull(reviewIdempotencyKey, "reviewIdempotencyKey");
		this.reviewRequestHash = Objects.requireNonNull(reviewRequestHash, "reviewRequestHash");
		this.reviewResultSnapshot = Objects.requireNonNull(reviewResultSnapshot, "reviewResultSnapshot");
		this.retentionExpiresAt = null;
	}

	public void reject(
		UUID reviewedByAdminId,
		SupplierApplicationReviewReasonCode reasonCode,
		String reviewReason,
		String reviewIdempotencyKey,
		String reviewRequestHash,
		String reviewResultSnapshot,
		Instant now
	) {
		reject(
			reviewedByAdminId,
			reasonCode,
			reviewReason,
			reviewIdempotencyKey,
			reviewRequestHash,
			reviewResultSnapshot,
			now,
			Duration.ofDays(APPLICATION_RETENTION_DAYS)
		);
	}

	public void reject(
		UUID reviewedByAdminId,
		SupplierApplicationReviewReasonCode reasonCode,
		String reviewReason,
		String reviewIdempotencyKey,
		String reviewRequestHash,
		String reviewResultSnapshot,
		Instant now,
		Duration retention
	) {
		ensureReviewable(now);
		Objects.requireNonNull(retention, "retention");
		if (retention.isZero() || retention.isNegative()) {
			throw new IllegalArgumentException("Application retention must be positive");
		}
		if (reasonCode == SupplierApplicationReviewReasonCode.APPLICATION_APPROVED) {
			throw new IllegalArgumentException("Rejection cannot use APPLICATION_APPROVED reason code");
		}
		status = SupplierApplicationStatus.REJECTED;
		this.reviewedByAdminId = Objects.requireNonNull(reviewedByAdminId, "reviewedByAdminId");
		this.reviewReasonCode = Objects.requireNonNull(reasonCode, "reasonCode");
		this.reviewReason = reviewReason;
		this.reviewedAt = now;
		this.reviewAction = SupplierApplicationReviewAction.REJECT;
		this.reviewIdempotencyKey = Objects.requireNonNull(reviewIdempotencyKey, "reviewIdempotencyKey");
		this.reviewRequestHash = Objects.requireNonNull(reviewRequestHash, "reviewRequestHash");
		this.reviewResultSnapshot = Objects.requireNonNull(reviewResultSnapshot, "reviewResultSnapshot");
		this.retentionExpiresAt = now.plus(retention);
	}

	public boolean expireAndAnonymize(Instant now) {
		if (status != SupplierApplicationStatus.SUBMITTED || retentionExpiresAt == null || now.isBefore(retentionExpiresAt)) {
			return false;
		}
		status = SupplierApplicationStatus.EXPIRED;
		clearRetainedPersonalData(now);
		return true;
	}

	public boolean anonymizeRejected(Instant now) {
		if (status != SupplierApplicationStatus.REJECTED
			|| anonymizedAt != null
			|| retentionExpiresAt == null
			|| now.isBefore(retentionExpiresAt)) {
			return false;
		}
		clearRetainedPersonalData(now);
		return true;
	}

	public void anonymizeApproved(Instant now) {
		if (status != SupplierApplicationStatus.APPROVED) {
			throw new IllegalStateException("Only an approved application follows supplier relationship retention");
		}
		clearRetainedPersonalData(now);
	}

	public boolean matchesSubmissionReplay(String idempotencyKey, String requestHash) {
		return Objects.equals(this.idempotencyKey, idempotencyKey)
			&& Objects.equals(this.requestHash, requestHash);
	}

	public boolean matchesReviewReplay(
		SupplierApplicationReviewAction action,
		String idempotencyKey,
		String requestHash
	) {
		return reviewAction == action
			&& Objects.equals(reviewIdempotencyKey, idempotencyKey)
			&& Objects.equals(reviewRequestHash, requestHash);
	}

	public boolean isPastReviewDeadline(Instant now) {
		return status == SupplierApplicationStatus.SUBMITTED
			&& retentionExpiresAt != null
			&& !now.isBefore(retentionExpiresAt);
	}

	private void ensureReviewable(Instant now) {
		Objects.requireNonNull(now, "now");
		if (status != SupplierApplicationStatus.SUBMITTED) {
			throw new IllegalStateException("Supplier application is already terminal");
		}
		if (retentionExpiresAt == null || !now.isBefore(retentionExpiresAt)) {
			throw new IllegalStateException("Supplier application is expired");
		}
	}

	private void clearRetainedPersonalData(Instant now) {
		supplierName = null;
		contactName = null;
		contactEmail = null;
		normalizedContactEmail = null;
		contactPhone = null;
		memo = null;
		idempotencyKey = null;
		requestHash = null;
		reviewReason = null;
		reviewIdempotencyKey = null;
		reviewRequestHash = null;
		reviewResultSnapshot = null;
		anonymizedAt = Objects.requireNonNull(now, "now");
	}

	public UUID getId() {
		return id;
	}

	public String getSupplierName() {
		return supplierName;
	}

	public String getContactName() {
		return contactName;
	}

	public String getContactEmail() {
		return contactEmail;
	}

	public String getNormalizedContactEmail() {
		return normalizedContactEmail;
	}

	public String getContactPhone() {
		return contactPhone;
	}

	public String getMemo() {
		return memo;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getRequestHash() {
		return requestHash;
	}

	public String getConsentPolicyVersion() {
		return consentPolicyVersion;
	}

	public Instant getConsentedAt() {
		return consentedAt;
	}

	public SupplierApplicationStatus getStatus() {
		return status;
	}

	public UUID getReviewedByAdminId() {
		return reviewedByAdminId;
	}

	public SupplierApplicationReviewReasonCode getReviewReasonCode() {
		return reviewReasonCode;
	}

	public String getReviewReason() {
		return reviewReason;
	}

	public Instant getReviewedAt() {
		return reviewedAt;
	}

	public Supplier getApprovedSupplier() {
		return approvedSupplier;
	}

	public SupplierApplicationReviewAction getReviewAction() {
		return reviewAction;
	}

	public SupplierApplicationApprovalMode getApprovalMode() {
		return approvalMode;
	}

	public Supplier getRequestedExistingSupplier() {
		return requestedExistingSupplier;
	}

	public String getReviewIdempotencyKey() {
		return reviewIdempotencyKey;
	}

	public String getReviewRequestHash() {
		return reviewRequestHash;
	}

	public String getReviewResultSnapshot() {
		return reviewResultSnapshot;
	}

	public Instant getRetentionExpiresAt() {
		return retentionExpiresAt;
	}

	public Instant getAnonymizedAt() {
		return anonymizedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
