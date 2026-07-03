package com.dropshipshop.api.claim.domain;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.refund.domain.Refund;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "claims")
public class Claim {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private CustomerOrder order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Enumerated(EnumType.STRING)
	@Column(name = "claim_type", nullable = false, length = 30)
	private ClaimType claimType;

	@Enumerated(EnumType.STRING)
	@Column(name = "claim_reason", nullable = false, length = 50)
	private ClaimReason claimReason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ClaimStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "requested_action", nullable = false, length = 30)
	private RequestedAction requestedAction;

	@Column(name = "customer_memo", nullable = false, columnDefinition = "TEXT")
	private String customerMemo;

	@Column(name = "reviewed_by_admin_id")
	private UUID reviewedByAdminId;

	@Column(name = "admin_review_reason", columnDefinition = "TEXT")
	private String adminReviewReason;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "return_received_by_admin_id")
	private UUID returnReceivedByAdminId;

	@Column(name = "return_received_at")
	private Instant returnReceivedAt;

	@Column(name = "return_received_memo", columnDefinition = "TEXT")
	private String returnReceivedMemo;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "refund_id")
	private Refund refund;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Claim() {
	}

	public Claim(
		CustomerOrder order,
		UserAccount user,
		ClaimType claimType,
		ClaimReason claimReason,
		ClaimStatus status,
		RequestedAction requestedAction,
		String customerMemo
	) {
		this.order = order;
		this.user = user;
		this.claimType = claimType;
		this.claimReason = claimReason;
		this.status = status;
		this.requestedAction = requestedAction;
		this.customerMemo = customerMemo;
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
		if (status != ClaimStatus.REQUESTED && status != ClaimStatus.UNDER_REVIEW) {
			throw new IllegalStateException("Only requested claims can be approved");
		}
		this.status = ClaimStatus.APPROVED;
		this.reviewedByAdminId = adminUserId;
		this.adminReviewReason = reason;
		this.reviewedAt = reviewedAt;
	}

	public void approveReturn(UUID adminUserId, String reason, Instant reviewedAt) {
		if (claimType != ClaimType.RETURN) {
			throw new IllegalStateException("Only return claims can move to return waiting");
		}
		if (status != ClaimStatus.REQUESTED && status != ClaimStatus.UNDER_REVIEW) {
			throw new IllegalStateException("Only requested return claims can be approved");
		}
		this.status = ClaimStatus.RETURN_WAITING;
		this.reviewedByAdminId = adminUserId;
		this.adminReviewReason = reason;
		this.reviewedAt = reviewedAt;
	}

	public void reject(UUID adminUserId, String reason, Instant reviewedAt) {
		if (status != ClaimStatus.REQUESTED && status != ClaimStatus.UNDER_REVIEW) {
			throw new IllegalStateException("Only requested claims can be rejected");
		}
		this.status = ClaimStatus.REJECTED;
		this.reviewedByAdminId = adminUserId;
		this.adminReviewReason = reason;
		this.reviewedAt = reviewedAt;
	}

	public void markReturnReceived(UUID adminUserId, String memo, Instant receivedAt) {
		if (claimType != ClaimType.RETURN) {
			throw new IllegalStateException("Only return claims can be marked as received");
		}
		if (status != ClaimStatus.RETURN_WAITING) {
			throw new IllegalStateException("Return can be received only from return waiting");
		}
		this.status = ClaimStatus.RETURN_RECEIVED;
		this.returnReceivedByAdminId = adminUserId;
		this.returnReceivedAt = receivedAt;
		this.returnReceivedMemo = memo;
	}

	public void markRefundProcessing(Refund refund, UUID adminUserId, String reason, Instant processedAt) {
		if (claimType != ClaimType.RETURN) {
			throw new IllegalStateException("Only return claims can start refund processing");
		}
		if (status != ClaimStatus.RETURN_RECEIVED) {
			throw new IllegalStateException("Return refund can start only after return received");
		}
		this.status = ClaimStatus.REFUND_PROCESSING;
		this.reviewedByAdminId = adminUserId;
		this.adminReviewReason = reason;
		this.reviewedAt = processedAt;
		this.refund = refund;
	}

	public void complete(Instant completedAt) {
		if (status != ClaimStatus.REFUND_PROCESSING) {
			throw new IllegalStateException("Claim can be completed only from refund processing");
		}
		this.status = ClaimStatus.COMPLETED;
		this.completedAt = completedAt;
	}

	public void rejectReturnAfterApproval(UUID adminUserId, String reason, Instant rejectedAt) {
		if (claimType != ClaimType.RETURN) {
			throw new IllegalStateException("Only return claims can be rejected after approval");
		}
		if (status != ClaimStatus.RETURN_WAITING && status != ClaimStatus.RETURN_RECEIVED) {
			throw new IllegalStateException("Approved return claims can be rejected only before refund processing");
		}
		this.status = ClaimStatus.REJECTED;
		this.reviewedByAdminId = adminUserId;
		this.adminReviewReason = reason;
		this.reviewedAt = rejectedAt;
	}

	public UUID getId() {
		return id;
	}

	public CustomerOrder getOrder() {
		return order;
	}

	public UserAccount getUser() {
		return user;
	}

	public ClaimType getClaimType() {
		return claimType;
	}

	public ClaimReason getClaimReason() {
		return claimReason;
	}

	public ClaimStatus getStatus() {
		return status;
	}

	public RequestedAction getRequestedAction() {
		return requestedAction;
	}

	public String getCustomerMemo() {
		return customerMemo;
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

	public UUID getReturnReceivedByAdminId() {
		return returnReceivedByAdminId;
	}

	public Instant getReturnReceivedAt() {
		return returnReceivedAt;
	}

	public String getReturnReceivedMemo() {
		return returnReceivedMemo;
	}

	public Refund getRefund() {
		return refund;
	}

	public UUID getRefundId() {
		return refund == null ? null : refund.getId();
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
