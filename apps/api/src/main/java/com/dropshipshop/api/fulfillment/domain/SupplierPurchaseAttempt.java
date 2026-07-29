package com.dropshipshop.api.fulfillment.domain;

import java.time.Instant;
import java.util.UUID;

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
@Table(name = "supplier_purchase_attempts")
public class SupplierPurchaseAttempt {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "fulfillment_id", nullable = false)
	private Fulfillment fulfillment;

	@Column(nullable = false, length = 20)
	private String action;

	@Column(nullable = false, length = 30)
	private String status;

	@Column(name = "request_fingerprint", nullable = false, length = 64)
	private String requestFingerprint;

	@Column(name = "external_order_number", length = 100)
	private String externalOrderNumber;

	@Column(name = "expected_amount")
	private Long expectedAmount;

	@Column(name = "actual_amount")
	private Long actualAmount;

	@Column(name = "failure_code", length = 100)
	private String failureCode;

	@Column(name = "failure_message", columnDefinition = "TEXT")
	private String failureMessage;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected SupplierPurchaseAttempt() {
	}

	public SupplierPurchaseAttempt(
		Fulfillment fulfillment,
		String action,
		String requestFingerprint,
		Long expectedAmount
	) {
		this.fulfillment = fulfillment;
		this.action = action;
		this.status = "STARTED";
		this.requestFingerprint = requestFingerprint;
		this.expectedAmount = expectedAmount;
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}

	public void succeed(String externalOrderNumber, Long actualAmount, Instant completedAt) {
		this.status = "SUCCEEDED";
		this.externalOrderNumber = externalOrderNumber;
		this.actualAmount = actualAmount;
		this.completedAt = completedAt;
	}

	public void fail(String code, String message, Instant completedAt) {
		this.status = "FAILED";
		this.failureCode = code;
		this.failureMessage = message;
		this.completedAt = completedAt;
	}

	public void markUnknown(String message, Instant completedAt) {
		this.status = "UNKNOWN";
		this.failureCode = "RESPONSE_UNKNOWN";
		this.failureMessage = message;
		this.completedAt = completedAt;
	}

	public UUID getId() {
		return id;
	}
}
