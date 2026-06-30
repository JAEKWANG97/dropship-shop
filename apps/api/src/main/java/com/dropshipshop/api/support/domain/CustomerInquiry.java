package com.dropshipshop.api.support.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_inquiries")
public class CustomerInquiry {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "customer_name", nullable = false, length = 100)
	private String customerName;

	@Column(name = "email", nullable = false, length = 320)
	private String email;

	@Column(name = "phone", length = 50)
	private String phone;

	@Column(name = "subject", nullable = false, length = 200)
	private String subject;

	@Column(name = "message", nullable = false, columnDefinition = "TEXT")
	private String message;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected CustomerInquiry() {
	}

	public CustomerInquiry(String customerName, String email, String phone, String subject, String message) {
		this.customerName = customerName;
		this.email = email;
		this.phone = phone;
		this.subject = subject;
		this.message = message;
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getCustomerName() {
		return customerName;
	}

	public String getEmail() {
		return email;
	}

	public String getPhone() {
		return phone;
	}

	public String getSubject() {
		return subject;
	}

	public String getMessage() {
		return message;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
