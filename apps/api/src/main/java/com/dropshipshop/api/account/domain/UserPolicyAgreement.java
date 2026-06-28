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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "user_policy_agreements",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_user_policy_agreements_user_versions",
			columnNames = {"user_id", "terms_version", "privacy_version"}
		)
	}
)
public class UserPolicyAgreement {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(name = "terms_version", nullable = false, length = 50)
	private String termsVersion;

	@Column(name = "privacy_version", nullable = false, length = 50)
	private String privacyVersion;

	@Column(name = "agreed_at", nullable = false)
	private Instant agreedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected UserPolicyAgreement() {
	}

	public UserPolicyAgreement(
		UserAccount user,
		String termsVersion,
		String privacyVersion,
		Instant agreedAt
	) {
		this.user = user;
		this.termsVersion = termsVersion;
		this.privacyVersion = privacyVersion;
		this.agreedAt = agreedAt;
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UserAccount getUser() {
		return user;
	}

	public String getTermsVersion() {
		return termsVersion;
	}

	public String getPrivacyVersion() {
		return privacyVersion;
	}

	public Instant getAgreedAt() {
		return agreedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
