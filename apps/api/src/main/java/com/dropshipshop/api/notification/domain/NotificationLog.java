package com.dropshipshop.api.notification.domain;

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
@Table(name = "notification_logs")
public class NotificationLog {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "order_id")
	private UUID orderId;

	@Column(name = "payment_group_id")
	private UUID paymentGroupId;

	@Column(name = "claim_id")
	private UUID claimId;

	@Column(name = "refund_id")
	private UUID refundId;

	@Column(name = "customer_inquiry_id")
	private UUID customerInquiryId;

	@Column(name = "supplier_id")
	private UUID supplierId;

	@Column(name = "supplier_invite_id")
	private UUID supplierInviteId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private NotificationType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationChannel channel;

	@Column(nullable = false)
	private boolean transactional;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationStatus status;

	@Column(length = 320)
	private String recipient;

	@Column(name = "recipient_retention_expires_at")
	private Instant recipientRetentionExpiresAt;

	@Column(name = "recipient_anonymized_at")
	private Instant recipientAnonymizedAt;

	@Column(name = "template_key", nullable = false, length = 100)
	private String templateKey;

	@Column(name = "payload_snapshot", nullable = false, columnDefinition = "TEXT")
	private String payloadSnapshot;

	@Column(name = "failure_reason", columnDefinition = "TEXT")
	private String failureReason;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected NotificationLog() {
	}

	public NotificationLog(
		UUID userId,
		UUID orderId,
		UUID paymentGroupId,
		UUID claimId,
		UUID refundId,
		UUID customerInquiryId,
		NotificationType type,
		NotificationChannel channel,
		String recipient,
		String templateKey,
		String payloadSnapshot
	) {
		this.userId = userId;
		this.orderId = orderId;
		this.paymentGroupId = paymentGroupId;
		this.claimId = claimId;
		this.refundId = refundId;
		this.customerInquiryId = customerInquiryId;
		this.type = type;
		this.channel = channel;
		this.transactional = true;
		this.status = NotificationStatus.PENDING;
		this.recipient = recipient;
		this.templateKey = templateKey;
		this.payloadSnapshot = payloadSnapshot;
	}

	public static NotificationLog supplierInvitation(
		UUID supplierId,
		UUID supplierInviteId,
		String recipient,
		String payloadSnapshot
	) {
		NotificationLog log = new NotificationLog(
			null,
			null,
			null,
			null,
			null,
			null,
			NotificationType.SUPPLIER_INVITATION,
			NotificationChannel.EMAIL,
			recipient,
			"supplier_invitation",
			payloadSnapshot
		);
		log.supplierId = supplierId;
		log.supplierInviteId = supplierInviteId;
		return log;
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

	public NotificationType getType() {
		return type;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getOrderId() {
		return orderId;
	}

	public UUID getPaymentGroupId() {
		return paymentGroupId;
	}

	public UUID getClaimId() {
		return claimId;
	}

	public UUID getRefundId() {
		return refundId;
	}

	public UUID getCustomerInquiryId() {
		return customerInquiryId;
	}

	public UUID getSupplierId() {
		return supplierId;
	}

	public UUID getSupplierInviteId() {
		return supplierInviteId;
	}

	public NotificationChannel getChannel() {
		return channel;
	}

	public boolean isTransactional() {
		return transactional;
	}

	public NotificationStatus getStatus() {
		return status;
	}

	public String getRecipient() {
		return recipient;
	}

	public String getTemplateKey() {
		return templateKey;
	}

	public String getPayloadSnapshot() {
		return payloadSnapshot;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getRecipientRetentionExpiresAt() {
		return recipientRetentionExpiresAt;
	}

	public Instant getRecipientAnonymizedAt() {
		return recipientAnonymizedAt;
	}

	public void markSent(Instant sentAt) {
		this.status = NotificationStatus.SENT;
		this.failureReason = null;
		this.sentAt = sentAt;
	}

	public void markSkipped(String reason) {
		this.status = NotificationStatus.SKIPPED;
		this.failureReason = reason;
		this.sentAt = null;
	}

	public void markFailed(String reason) {
		this.status = NotificationStatus.FAILED;
		this.failureReason = reason;
		this.sentAt = null;
	}

	public void markPendingForRetry() {
		this.status = NotificationStatus.PENDING;
		this.failureReason = null;
		this.sentAt = null;
	}

	public void scheduleRecipientCleanup(Instant expiresAt) {
		this.recipientRetentionExpiresAt = expiresAt;
	}

	public void anonymizeRecipient(Instant now) {
		this.recipient = null;
		this.failureReason = null;
		this.recipientAnonymizedAt = now;
	}
}
