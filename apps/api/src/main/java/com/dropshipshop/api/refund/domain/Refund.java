package com.dropshipshop.api.refund.domain;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentGroup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "refunds")
public class Refund {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payment_group_id", nullable = false)
	private PaymentGroup paymentGroup;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false, unique = true)
	private CustomerOrder order;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_id")
	private Payment payment;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private RefundReason reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RefundStatus status = RefundStatus.REQUESTED;

	@Column(name = "refund_amount", nullable = false)
	private long refundAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "refund_scope", nullable = false, length = 30)
	private RefundScope refundScope;

	@Column(name = "provider_payment_key", length = 200)
	private String providerPaymentKey;

	@Column(name = "provider_cancel_transaction_key", length = 200)
	private String providerCancelTransactionKey;

	@Column(name = "idempotency_key", length = 200)
	private String idempotencyKey;

	@Column(name = "failure_code", length = 100)
	private String failureCode;

	@Column(name = "failure_message", length = 1000)
	private String failureMessage;

	@Column(name = "raw_provider_status", length = 100)
	private String rawProviderStatus;

	@Column(name = "reviewed_by_admin_id")
	private UUID reviewedByAdminId;

	@Column(name = "admin_review_reason", columnDefinition = "TEXT")
	private String adminReviewReason;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "manual_refunded_by_admin_id")
	private UUID manualRefundedByAdminId;

	@Column(name = "manual_refunded_at")
	private Instant manualRefundedAt;

	@Column(name = "manual_refund_reason", columnDefinition = "TEXT")
	private String manualRefundReason;

	@Column(name = "manual_refund_bank_name", length = 100)
	private String manualRefundBankName;

	@Column(name = "manual_refund_account_number", length = 100)
	private String manualRefundAccountNumber;

	@Column(name = "manual_refund_account_holder", length = 100)
	private String manualRefundAccountHolder;

	@Column(name = "requested_at")
	private Instant requestedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "failed_at")
	private Instant failedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Refund() {
	}

	public Refund(CustomerOrder order, RefundReason reason) {
		this.paymentGroup = order.getPaymentGroup();
		this.order = order;
		this.reason = reason;
		this.refundAmount = order.getTotalAmount();
		this.refundScope = RefundScope.DELIVERY_GROUP_ORDER;
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

	public void approve(UUID adminUserId, String reason, Instant reviewedAt) {
		if (status != RefundStatus.REQUESTED && status != RefundStatus.MANUAL_REVIEW_REQUIRED) {
			throw new IllegalStateException("Refund can be approved only from requested or manual review required");
		}
		this.status = RefundStatus.APPROVED;
		this.reviewedByAdminId = adminUserId;
		this.adminReviewReason = reason;
		this.reviewedAt = reviewedAt;
	}

	public void reject(UUID adminUserId, String reason, Instant reviewedAt) {
		if (status != RefundStatus.REQUESTED && status != RefundStatus.MANUAL_REVIEW_REQUIRED) {
			throw new IllegalStateException("Refund can be rejected only from requested or manual review required");
		}
		this.status = RefundStatus.REJECTED;
		this.reviewedByAdminId = adminUserId;
		this.adminReviewReason = reason;
		this.reviewedAt = reviewedAt;
	}

	public void requestPgCancel(Payment payment, String idempotencyKey, Instant requestedAt, boolean retry) {
		if (retry) {
			if (status != RefundStatus.RETRY_REQUIRED && status != RefundStatus.FAILED) {
				throw new IllegalStateException("Refund is not retryable");
			}
		} else if (status != RefundStatus.APPROVED) {
			throw new IllegalStateException("Refund is not ready for PG cancel request");
		}
		this.payment = payment;
		this.providerPaymentKey = payment.getProviderPaymentKey();
		this.idempotencyKey = idempotencyKey;
		this.status = RefundStatus.PG_CANCEL_REQUESTED;
		this.requestedAt = requestedAt;
	}

	public void complete(String providerCancelTransactionKey, String rawProviderStatus, Instant completedAt) {
		if (status != RefundStatus.PG_CANCEL_REQUESTED) {
			throw new IllegalStateException("Refund has not requested PG cancel");
		}
		this.providerCancelTransactionKey = providerCancelTransactionKey;
		this.rawProviderStatus = rawProviderStatus;
		this.status = RefundStatus.COMPLETED;
		this.completedAt = completedAt;
		this.failureCode = null;
		this.failureMessage = null;
		this.failedAt = null;
	}

	public void completeManualBankTransfer(
		Payment payment,
		UUID adminUserId,
		String reason,
		String bankName,
		String accountNumber,
		String accountHolder,
		Instant completedAt
	) {
		if (status != RefundStatus.APPROVED) {
			throw new IllegalStateException("Manual refund can be completed only after admin approval");
		}
		this.payment = payment;
		this.providerPaymentKey = payment.getProviderPaymentKey();
		this.status = RefundStatus.COMPLETED;
		this.manualRefundedByAdminId = adminUserId;
		this.manualRefundedAt = completedAt;
		this.manualRefundReason = reason;
		this.manualRefundBankName = bankName;
		this.manualRefundAccountNumber = accountNumber;
		this.manualRefundAccountHolder = accountHolder;
		this.completedAt = completedAt;
		this.failureCode = null;
		this.failureMessage = null;
		this.failedAt = null;
	}

	public void markRetryRequired(String failureCode, String failureMessage, Instant failedAt) {
		this.status = RefundStatus.RETRY_REQUIRED;
		this.failureCode = failureCode;
		this.failureMessage = failureMessage;
		this.failedAt = failedAt;
	}

	public void markManualReviewRequired(String failureCode, String failureMessage, Instant failedAt) {
		this.status = RefundStatus.MANUAL_REVIEW_REQUIRED;
		this.failureCode = failureCode;
		this.failureMessage = failureMessage;
		this.failedAt = failedAt;
	}

	public UUID getId() {
		return id;
	}

	public PaymentGroup getPaymentGroup() {
		return paymentGroup;
	}

	public CustomerOrder getOrder() {
		return order;
	}

	public Payment getPayment() {
		return payment;
	}

	public RefundReason getReason() {
		return reason;
	}

	public RefundStatus getStatus() {
		return status;
	}

	public long getRefundAmount() {
		return refundAmount;
	}

	public RefundScope getRefundScope() {
		return refundScope;
	}

	public String getProviderPaymentKey() {
		return providerPaymentKey;
	}

	public String getProviderCancelTransactionKey() {
		return providerCancelTransactionKey;
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

	public String getRawProviderStatus() {
		return rawProviderStatus;
	}

	public UUID getReviewedByAdminId() {
		return reviewedByAdminId;
	}

	public String getAdminReviewReason() {
		return adminReviewReason;
	}

	public Instant getReviewedAt() {
		return reviewedAt;
	}

	public UUID getManualRefundedByAdminId() {
		return manualRefundedByAdminId;
	}

	public Instant getManualRefundedAt() {
		return manualRefundedAt;
	}

	public String getManualRefundReason() {
		return manualRefundReason;
	}

	public String getManualRefundBankName() {
		return manualRefundBankName;
	}

	public String getManualRefundAccountNumber() {
		return manualRefundAccountNumber;
	}

	public String getManualRefundAccountHolder() {
		return manualRefundAccountHolder;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public Instant getFailedAt() {
		return failedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
