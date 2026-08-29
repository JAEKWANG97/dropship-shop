package com.dropshipshop.api.supplierportal.domain;

import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "supplier_invites")
public class SupplierInvite {

	private static final long RECIPIENT_RETENTION_DAYS = 30;

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false)
	private Supplier supplier;

	@Column(name = "recipient_email", length = 320)
	private String recipientEmail;

	@Column(name = "token_digest", nullable = false, length = 128, unique = true)
	private String tokenDigest;

	@Column(name = "issuance_idempotency_key", length = 200)
	private String issuanceIdempotencyKey;

	@Column(name = "issuance_request_hash", length = 128)
	private String issuanceRequestHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "consumed_by_user_id")
	private UUID consumedByUserId;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoked_by_admin_id")
	private UUID revokedByAdminId;

	@Enumerated(EnumType.STRING)
	@Column(name = "revocation_reason_code", length = 40)
	private SupplierInviteRevocationReasonCode revocationReasonCode;

	@Column(name = "recipient_retention_expires_at", nullable = false)
	private Instant recipientRetentionExpiresAt;

	@Column(name = "recipient_anonymized_at")
	private Instant recipientAnonymizedAt;

	@Column(name = "created_by_admin_id", nullable = false)
	private UUID createdByAdminId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SupplierInvite() {
	}

	private SupplierInvite(
		Supplier supplier,
		String recipientEmail,
		String tokenDigest,
		String issuanceIdempotencyKey,
		String issuanceRequestHash,
		Instant expiresAt,
		UUID createdByAdminId,
		Instant now,
		Duration recipientRetention
	) {
		if (!expiresAt.isAfter(now)) {
			throw new IllegalArgumentException("Invite expiry must be in the future");
		}
		if (recipientRetention.isZero() || recipientRetention.isNegative()) {
			throw new IllegalArgumentException("Invite recipient retention must be positive");
		}
		this.supplier = supplier;
		this.recipientEmail = recipientEmail;
		this.tokenDigest = tokenDigest;
		this.issuanceIdempotencyKey = issuanceIdempotencyKey;
		this.issuanceRequestHash = issuanceRequestHash;
		this.expiresAt = expiresAt;
		this.recipientRetentionExpiresAt = expiresAt.plus(recipientRetention);
		this.createdByAdminId = createdByAdminId;
		this.createdAt = now;
	}

	public static SupplierInvite issue(
		Supplier supplier,
		String recipientEmail,
		String tokenDigest,
		String issuanceIdempotencyKey,
		String issuanceRequestHash,
		Instant expiresAt,
		UUID createdByAdminId,
		Instant now
	) {
		return issue(
			supplier,
			recipientEmail,
			tokenDigest,
			issuanceIdempotencyKey,
			issuanceRequestHash,
			expiresAt,
			createdByAdminId,
			now,
			Duration.ofDays(RECIPIENT_RETENTION_DAYS)
		);
	}

	public static SupplierInvite issue(
		Supplier supplier,
		String recipientEmail,
		String tokenDigest,
		String issuanceIdempotencyKey,
		String issuanceRequestHash,
		Instant expiresAt,
		UUID createdByAdminId,
		Instant now,
		Duration recipientRetention
	) {
		return new SupplierInvite(
			Objects.requireNonNull(supplier, "supplier"),
			Objects.requireNonNull(recipientEmail, "recipientEmail"),
			Objects.requireNonNull(tokenDigest, "tokenDigest"),
			Objects.requireNonNull(issuanceIdempotencyKey, "issuanceIdempotencyKey"),
			Objects.requireNonNull(issuanceRequestHash, "issuanceRequestHash"),
			Objects.requireNonNull(expiresAt, "expiresAt"),
			Objects.requireNonNull(createdByAdminId, "createdByAdminId"),
			Objects.requireNonNull(now, "now"),
			Objects.requireNonNull(recipientRetention, "recipientRetention")
		);
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
		if (recipientRetentionExpiresAt == null && expiresAt != null) {
			recipientRetentionExpiresAt = expiresAt.plus(RECIPIENT_RETENTION_DAYS, ChronoUnit.DAYS);
		}
	}

	public void validateUsable(Instant now) {
		Objects.requireNonNull(now, "now");
		if (consumedAt != null) {
			throw new IllegalStateException("Supplier invite is already consumed");
		}
		if (revokedAt != null) {
			throw new IllegalStateException("Supplier invite is revoked");
		}
		if (!now.isBefore(expiresAt)) {
			throw new IllegalStateException("Supplier invite is expired");
		}
		if (recipientEmail == null) {
			throw new IllegalStateException("Supplier invite recipient is no longer retained");
		}
	}

	public void consume(UUID userId, Instant now) {
		validateUsable(now);
		consumedByUserId = Objects.requireNonNull(userId, "userId");
		consumedAt = now;
		scheduleTerminalRetention(now);
	}

	public void revoke(
		UUID adminId,
		SupplierInviteRevocationReasonCode reasonCode,
		Instant now
	) {
		revokeInternal(adminId, Objects.requireNonNull(reasonCode, "reasonCode"), now);
	}

	public void revokeForLifecycle(UUID adminId, Instant now) {
		revokeInternal(adminId, null, now);
	}

	private void revokeInternal(UUID adminId, SupplierInviteRevocationReasonCode reasonCode, Instant now) {
		Objects.requireNonNull(now, "now");
		if (consumedAt != null || revokedAt != null) {
			throw new IllegalStateException("Only an open supplier invite can be revoked");
		}
		revokedByAdminId = adminId;
		revocationReasonCode = reasonCode;
		revokedAt = now;
		scheduleTerminalRetention(now);
	}

	private void scheduleTerminalRetention(Instant terminalAt) {
		Duration retention = Duration.between(expiresAt, recipientRetentionExpiresAt);
		if (retention.isZero() || retention.isNegative()) {
			retention = Duration.ofDays(RECIPIENT_RETENTION_DAYS);
		}
		Instant candidate = terminalAt.plus(retention);
		if (recipientRetentionExpiresAt == null || candidate.isBefore(recipientRetentionExpiresAt)) {
			recipientRetentionExpiresAt = candidate;
		}
	}

	public boolean anonymizeRecipient(Instant now) {
		Objects.requireNonNull(now, "now");
		if (recipientAnonymizedAt != null
			|| recipientRetentionExpiresAt == null
			|| now.isBefore(recipientRetentionExpiresAt)) {
			return false;
		}
		recipientEmail = null;
		issuanceIdempotencyKey = null;
		issuanceRequestHash = null;
		recipientAnonymizedAt = now;
		return true;
	}

	public void clearConsumedByUser() {
		consumedByUserId = null;
	}

	public boolean matchesIssuanceReplay(String idempotencyKey, String requestHash) {
		return Objects.equals(issuanceIdempotencyKey, idempotencyKey)
			&& Objects.equals(issuanceRequestHash, requestHash);
	}

	public boolean isOpen() {
		return consumedAt == null && revokedAt == null;
	}

	public boolean isExpiredAt(Instant now) {
		return !Objects.requireNonNull(now, "now").isBefore(expiresAt);
	}

	public UUID getId() {
		return id;
	}

	public Supplier getSupplier() {
		return supplier;
	}

	public String getRecipientEmail() {
		return recipientEmail;
	}

	public String getTokenDigest() {
		return tokenDigest;
	}

	public String getIssuanceIdempotencyKey() {
		return issuanceIdempotencyKey;
	}

	public String getIssuanceRequestHash() {
		return issuanceRequestHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getConsumedAt() {
		return consumedAt;
	}

	public UUID getConsumedByUserId() {
		return consumedByUserId;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public UUID getRevokedByAdminId() {
		return revokedByAdminId;
	}

	public SupplierInviteRevocationReasonCode getRevocationReasonCode() {
		return revocationReasonCode;
	}

	public Instant getRecipientRetentionExpiresAt() {
		return recipientRetentionExpiresAt;
	}

	public Instant getRecipientAnonymizedAt() {
		return recipientAnonymizedAt;
	}

	public UUID getCreatedByAdminId() {
		return createdByAdminId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
