package com.dropshipshop.api.payment.domain;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.user.domain.UserAccount;

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
@Table(name = "payment_groups")
public class PaymentGroup {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "checkout_number", nullable = false, length = 40, unique = true)
	private String checkoutNumber;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentGroupStatus status = PaymentGroupStatus.PAYMENT_PENDING;

	@Column(name = "total_amount", nullable = false)
	private long totalAmount;

	@Column(name = "approved_amount")
	private Long approvedAmount;

	@Column(name = "refundable_amount", nullable = false)
	private long refundableAmount;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "approved_at")
	private Instant approvedAt;

	@Column(name = "policy_confirmed_at")
	private Instant policyConfirmedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PaymentGroup() {
	}

	public PaymentGroup(String checkoutNumber, UserAccount user, long totalAmount, Instant expiresAt) {
		this.checkoutNumber = checkoutNumber;
		this.user = user;
		this.totalAmount = totalAmount;
		this.refundableAmount = totalAmount;
		this.expiresAt = expiresAt;
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

	public void confirmPolicy(Instant confirmedAt) {
		this.policyConfirmedAt = confirmedAt;
	}

	public void approve(long approvedAmount, Instant approvedAt) {
		this.status = PaymentGroupStatus.APPROVED;
		this.approvedAmount = approvedAmount;
		this.approvedAt = approvedAt;
	}

	public void markPaymentException() {
		this.status = PaymentGroupStatus.PAYMENT_EXCEPTION;
	}

	public void applyRefund(long refundAmount) {
		if (status != PaymentGroupStatus.APPROVED && status != PaymentGroupStatus.PARTIALLY_REFUNDED) {
			throw new IllegalStateException("Payment group is not refundable");
		}
		if (refundAmount <= 0 || refundAmount > refundableAmount) {
			throw new IllegalArgumentException("Refund amount exceeds refundable amount");
		}
		this.refundableAmount -= refundAmount;
		this.status = refundableAmount == 0 ? PaymentGroupStatus.REFUNDED : PaymentGroupStatus.PARTIALLY_REFUNDED;
	}

	public void expire() {
		this.status = PaymentGroupStatus.EXPIRED;
	}

	public UUID getId() {
		return id;
	}

	public String getCheckoutNumber() {
		return checkoutNumber;
	}

	public UserAccount getUser() {
		return user;
	}

	public PaymentGroupStatus getStatus() {
		return status;
	}

	public long getTotalAmount() {
		return totalAmount;
	}

	public Long getApprovedAmount() {
		return approvedAmount;
	}

	public long getRefundableAmount() {
		return refundableAmount;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getApprovedAt() {
		return approvedAt;
	}

	public Instant getPolicyConfirmedAt() {
		return policyConfirmedAt;
	}
}
