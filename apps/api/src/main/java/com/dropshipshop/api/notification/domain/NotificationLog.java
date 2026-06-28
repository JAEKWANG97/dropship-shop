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

	@Column(nullable = false, length = 320)
	private String recipient;

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
		NotificationType type,
		String recipient,
		String templateKey,
		String payloadSnapshot
	) {
		this.userId = userId;
		this.orderId = orderId;
		this.paymentGroupId = paymentGroupId;
		this.claimId = claimId;
		this.refundId = refundId;
		this.type = type;
		this.channel = NotificationChannel.EMAIL;
		this.transactional = true;
		this.status = NotificationStatus.SENT;
		this.recipient = recipient;
		this.templateKey = templateKey;
		this.payloadSnapshot = payloadSnapshot;
		this.sentAt = Instant.now();
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

	public UUID getOrderId() {
		return orderId;
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

	public Instant getSentAt() {
		return sentAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
