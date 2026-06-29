package com.dropshipshop.api.user.domain;

import java.time.Instant;
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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "users",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_users_provider_provider_user_id",
			columnNames = {"provider", "provider_user_id"}
		)
	}
)
public class UserAccount {

	@Id
	@GeneratedValue
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SocialProvider provider;

	@Column(name = "provider_user_id", nullable = false, length = 191)
	private String providerUserId;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(name = "phone_number", length = 30)
	private String phoneNumber;

	@Column(name = "phone_verified_at")
	private Instant phoneVerifiedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role = UserRole.CUSTOMER;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status = UserStatus.ACTIVE;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserAccount() {
	}

	public UserAccount(
		SocialProvider provider,
		String providerUserId,
		String email,
		String displayName,
		UserRole role
	) {
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.email = email;
		this.displayName = displayName;
		this.role = role;
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

	public SocialProvider getProvider() {
		return provider;
	}

	public String getProviderUserId() {
		return providerUserId;
	}

	public String getEmail() {
		return email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public UserRole getRole() {
		return role;
	}

	public UserStatus getStatus() {
		return status;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public Instant getPhoneVerifiedAt() {
		return phoneVerifiedAt;
	}

	public void updateProfile(String displayName, String email) {
		this.displayName = displayName;
		this.email = email;
	}

	public void verifyPhone(String phoneNumber, Instant verifiedAt) {
		this.phoneNumber = phoneNumber;
		this.phoneVerifiedAt = verifiedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
