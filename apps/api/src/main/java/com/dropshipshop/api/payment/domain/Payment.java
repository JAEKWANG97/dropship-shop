package com.dropshipshop.api.payment.domain;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.common.money.MoneyMath;

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
@Table(name = "payments")
public class Payment {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payment_group_id", nullable = false)
	private PaymentGroup paymentGroup;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentProvider provider;

	@Column(name = "provider_payment_key", nullable = false, length = 200, unique = true)
	private String providerPaymentKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentMethod method;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentStatus status;

	@Column(name = "requested_amount", nullable = false)
	private long requestedAmount;

	@Column(name = "approved_amount")
	private Long approvedAmount;

	@Column(name = "approved_at")
	private Instant approvedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "exception_reason", length = 60)
	private PaymentExceptionReason exceptionReason;

	@Column(name = "idempotency_key", length = 200)
	private String idempotencyKey;

	@Column(name = "failure_code", length = 100)
	private String failureCode;

	@Column(name = "failure_message", length = 1000)
	private String failureMessage;

	@Column(name = "provider_cancel_transaction_key", length = 200)
	private String providerCancelTransactionKey;

	@Column(name = "cancel_requested_at")
	private Instant cancelRequestedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "raw_provider_status", length = 100)
	private String rawProviderStatus;

	@Column(name = "last_synced_at")
	private Instant lastSyncedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Payment() {
	}

	private Payment(
		PaymentGroup paymentGroup,
		PaymentProvider provider,
		String providerPaymentKey,
		PaymentMethod method,
		PaymentStatus status,
		long requestedAmount
	) {
		this.paymentGroup = paymentGroup;
		this.provider = provider;
		this.providerPaymentKey = providerPaymentKey;
		this.method = method;
		this.status = status;
		this.requestedAmount = requestedAmount;
	}

	public static Payment bankTransferApproved(
		PaymentGroup paymentGroup,
		String providerPaymentKey,
		long approvedAmount,
		Instant approvedAt
	) {
		Payment payment = new Payment(paymentGroup, PaymentProvider.BANK_TRANSFER, providerPaymentKey,
			PaymentMethod.BANK_TRANSFER, PaymentStatus.APPROVED,
			MoneyMath.requirePositive(approvedAmount, "approvedAmount"));
		payment.approvedAmount = approvedAmount;
		payment.approvedAt = approvedAt;
		payment.rawProviderStatus = "MANUAL_DEPOSIT_CONFIRMED";
		payment.lastSyncedAt = approvedAt;
		return payment;
	}

	public static Payment bankTransferException(
		PaymentGroup paymentGroup,
		String providerPaymentKey,
		long requestedAmount,
		PaymentExceptionReason exceptionReason,
		Instant receivedAt
	) {
		Payment payment = new Payment(paymentGroup, PaymentProvider.BANK_TRANSFER, providerPaymentKey,
			PaymentMethod.BANK_TRANSFER, PaymentStatus.PAYMENT_EXCEPTION,
			MoneyMath.requirePositive(requestedAmount, "requestedAmount"));
		payment.exceptionReason = exceptionReason;
		payment.rawProviderStatus = "MANUAL_DEPOSIT_EXCEPTION";
		payment.lastSyncedAt = receivedAt;
		return payment;
	}

	public void markRefundCompleted(boolean fullyRefunded) {
		this.status = fullyRefunded ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
	}

	public UUID getId() {
		return id;
	}

	public PaymentGroup getPaymentGroup() {
		return paymentGroup;
	}

	public PaymentProvider getProvider() {
		return provider;
	}

	public String getProviderPaymentKey() {
		return providerPaymentKey;
	}

	public PaymentMethod getMethod() {
		return method;
	}

	public PaymentStatus getStatus() {
		return status;
	}

	public long getRequestedAmount() {
		return requestedAmount;
	}

	public Long getApprovedAmount() {
		return approvedAmount;
	}

	public Instant getApprovedAt() {
		return approvedAt;
	}

	public PaymentExceptionReason getExceptionReason() {
		return exceptionReason;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public String getFailureCode() {
		return failureCode;
	}

	public String getFailureMessage() {
		return failureMessage;
	}

	public String getProviderCancelTransactionKey() {
		return providerCancelTransactionKey;
	}

	public Instant getCancelRequestedAt() {
		return cancelRequestedAt;
	}

	public Instant getCancelledAt() {
		return cancelledAt;
	}

	public String getRawProviderStatus() {
		return rawProviderStatus;
	}

	public Instant getCreatedAt() {
		return createdAt;
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
}
