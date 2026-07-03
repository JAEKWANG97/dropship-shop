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
import jakarta.persistence.Version;

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

	@Column(name = "bank_transfer_bank_name", length = 100)
	private String bankTransferBankName;

	@Column(name = "bank_transfer_account_number", length = 100)
	private String bankTransferAccountNumber;

	@Column(name = "bank_transfer_account_holder", length = 100)
	private String bankTransferAccountHolder;

	@Column(name = "bank_transfer_depositor_name", length = 100)
	private String bankTransferDepositorName;

	@Column(name = "bank_transfer_cash_receipt_notice", length = 500)
	private String bankTransferCashReceiptNotice;

	@Column(name = "deposit_confirmed_by_admin_id")
	private UUID depositConfirmedByAdminId;

	@Column(name = "deposit_confirmed_at")
	private Instant depositConfirmedAt;

	@Column(name = "deposit_confirmation_reason", columnDefinition = "TEXT")
	private String depositConfirmationReason;

	@Column(name = "deposit_mismatch_memo", columnDefinition = "TEXT")
	private String depositMismatchMemo;

	@Column(name = "deposit_mismatch_recorded_by_admin_id")
	private UUID depositMismatchRecordedByAdminId;

	@Column(name = "deposit_mismatch_recorded_at")
	private Instant depositMismatchRecordedAt;

	@Column(name = "unpaid_cancelled_by_admin_id")
	private UUID unpaidCancelledByAdminId;

	@Column(name = "unpaid_cancelled_at")
	private Instant unpaidCancelledAt;

	@Column(name = "unpaid_cancel_reason", columnDefinition = "TEXT")
	private String unpaidCancelReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

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

	public void configureBankTransfer(
		String bankName,
		String accountNumber,
		String accountHolder,
		String depositorName,
		String cashReceiptNotice
	) {
		this.bankTransferBankName = bankName;
		this.bankTransferAccountNumber = accountNumber;
		this.bankTransferAccountHolder = accountHolder;
		this.bankTransferDepositorName = depositorName;
		this.bankTransferCashReceiptNotice = cashReceiptNotice;
	}

	public void approve(long approvedAmount, Instant approvedAt) {
		this.status = PaymentGroupStatus.APPROVED;
		this.approvedAmount = approvedAmount;
		this.approvedAt = approvedAt;
	}

	public void confirmBankTransferDeposit(UUID adminUserId, String reason, Instant confirmedAt) {
		if (status != PaymentGroupStatus.PAYMENT_PENDING) {
			throw new IllegalStateException("Deposit can be confirmed only while payment is pending");
		}
		this.status = PaymentGroupStatus.APPROVED;
		this.approvedAmount = totalAmount;
		this.approvedAt = confirmedAt;
		this.depositConfirmedByAdminId = adminUserId;
		this.depositConfirmedAt = confirmedAt;
		this.depositConfirmationReason = reason;
	}

	public void markPaymentException() {
		this.status = PaymentGroupStatus.PAYMENT_EXCEPTION;
	}

	public void cancelUnpaidDeposit(UUID adminUserId, String reason, Instant cancelledAt) {
		if (status != PaymentGroupStatus.PAYMENT_PENDING) {
			throw new IllegalStateException("Unpaid deposit can be cancelled only while payment is pending");
		}
		this.status = PaymentGroupStatus.CANCELLED;
		this.refundableAmount = 0;
		this.unpaidCancelledByAdminId = adminUserId;
		this.unpaidCancelledAt = cancelledAt;
		this.unpaidCancelReason = reason;
	}

	public void recordDepositMismatch(UUID adminUserId, String memo, Instant recordedAt) {
		if (status != PaymentGroupStatus.PAYMENT_PENDING) {
			throw new IllegalStateException("Deposit mismatch can be recorded only while payment is pending");
		}
		this.depositMismatchMemo = memo;
		this.depositMismatchRecordedByAdminId = adminUserId;
		this.depositMismatchRecordedAt = recordedAt;
	}

	public void markCancelled() {
		if (status != PaymentGroupStatus.PAYMENT_EXCEPTION && status != PaymentGroupStatus.CANCEL_FAILED) {
			throw new IllegalStateException("Payment group can be cancelled only from payment exception");
		}
		this.status = PaymentGroupStatus.CANCELLED;
		this.refundableAmount = 0;
	}

	public void markCancelFailed() {
		if (status != PaymentGroupStatus.PAYMENT_EXCEPTION && status != PaymentGroupStatus.CANCEL_FAILED) {
			throw new IllegalStateException("Payment group cancel can fail only from payment exception");
		}
		this.status = PaymentGroupStatus.CANCEL_FAILED;
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

	public String getBankTransferBankName() {
		return bankTransferBankName;
	}

	public String getBankTransferAccountNumber() {
		return bankTransferAccountNumber;
	}

	public String getBankTransferAccountHolder() {
		return bankTransferAccountHolder;
	}

	public String getBankTransferDepositorName() {
		return bankTransferDepositorName;
	}

	public String getBankTransferCashReceiptNotice() {
		return bankTransferCashReceiptNotice;
	}

	public UUID getDepositConfirmedByAdminId() {
		return depositConfirmedByAdminId;
	}

	public Instant getDepositConfirmedAt() {
		return depositConfirmedAt;
	}

	public String getDepositConfirmationReason() {
		return depositConfirmationReason;
	}

	public String getDepositMismatchMemo() {
		return depositMismatchMemo;
	}

	public UUID getDepositMismatchRecordedByAdminId() {
		return depositMismatchRecordedByAdminId;
	}

	public Instant getDepositMismatchRecordedAt() {
		return depositMismatchRecordedAt;
	}

	public UUID getUnpaidCancelledByAdminId() {
		return unpaidCancelledByAdminId;
	}

	public Instant getUnpaidCancelledAt() {
		return unpaidCancelledAt;
	}

	public String getUnpaidCancelReason() {
		return unpaidCancelReason;
	}

	public long getVersion() {
		return version;
	}
}
