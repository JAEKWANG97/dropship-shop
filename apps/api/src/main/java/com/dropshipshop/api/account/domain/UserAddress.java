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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_addresses")
public class UserAddress {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(name = "recipient_name", nullable = false, length = 100)
	private String recipientName;

	@Column(name = "recipient_phone", nullable = false, length = 30)
	private String recipientPhone;

	@Column(name = "postal_code", nullable = false, length = 20)
	private String postalCode;

	@Column(nullable = false, length = 300)
	private String address1;

	@Column(length = 300)
	private String address2;

	@Column(name = "default_address", nullable = false)
	private boolean defaultAddress;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserAddress() {
	}

	public UserAddress(
		UserAccount user,
		String recipientName,
		String recipientPhone,
		String postalCode,
		String address1,
		String address2,
		boolean defaultAddress
	) {
		this.user = user;
		this.recipientName = recipientName;
		this.recipientPhone = recipientPhone;
		this.postalCode = postalCode;
		this.address1 = address1;
		this.address2 = address2;
		this.defaultAddress = defaultAddress;
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

	public void update(
		String recipientName,
		String recipientPhone,
		String postalCode,
		String address1,
		String address2,
		boolean defaultAddress
	) {
		this.recipientName = recipientName;
		this.recipientPhone = recipientPhone;
		this.postalCode = postalCode;
		this.address1 = address1;
		this.address2 = address2;
		this.defaultAddress = defaultAddress;
	}

	public void setDefaultAddress(boolean defaultAddress) {
		this.defaultAddress = defaultAddress;
	}

	public UUID getId() {
		return id;
	}

	public UserAccount getUser() {
		return user;
	}

	public String getRecipientName() {
		return recipientName;
	}

	public String getRecipientPhone() {
		return recipientPhone;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public String getAddress1() {
		return address1;
	}

	public String getAddress2() {
		return address2;
	}

	public boolean isDefaultAddress() {
		return defaultAddress;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
