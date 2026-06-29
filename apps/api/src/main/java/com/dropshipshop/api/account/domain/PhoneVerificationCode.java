package com.dropshipshop.api.account.domain;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.user.domain.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "phone_verification_codes")
public class PhoneVerificationCode {

	private static final int MAX_ATTEMPTS = 5;

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(name = "phone_number", nullable = false, length = 30)
	private String phoneNumber;

	@Column(name = "code_hash", nullable = false, length = 64)
	private String codeHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PhoneVerificationCode() {
	}

	public PhoneVerificationCode(UserAccount user, String phoneNumber, String codeHash, Instant expiresAt) {
		this.user = user;
		this.phoneNumber = phoneNumber;
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}

	public boolean isVerified() {
		return verifiedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public boolean hasAttemptsLeft() {
		return attemptCount < MAX_ATTEMPTS;
	}

	public void recordFailedAttempt() {
		attemptCount++;
	}

	public void verify(Instant now) {
		verifiedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public String getCodeHash() {
		return codeHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getVerifiedAt() {
		return verifiedAt;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
