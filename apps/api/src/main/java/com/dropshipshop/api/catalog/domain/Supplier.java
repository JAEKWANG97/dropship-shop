package com.dropshipshop.api.catalog.domain;

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
		this.name = name;
		this.contactName = contactName;
		this.phone = phone;
		this.email = email;
		this.memo = memo;
		this.status = status;
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
}
