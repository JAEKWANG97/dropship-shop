package com.dropshipshop.api.support.domain;

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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CustomerInquiryStatus status;

	@Column(name = "consent_policy_version", length = 100)
	private String consentPolicyVersion;

	@Column(name = "consented_at")
	private Instant consentedAt;

	@Column(name = "retention_expires_at", nullable = false)
	private Instant retentionExpiresAt;

	@Column(name = "admin_memo", columnDefinition = "TEXT")
	private String adminMemo;

	@Column(name = "answer", columnDefinition = "TEXT")
	private String answer;

	@Column(name = "handled_by_admin_id")
	private UUID handledByAdminId;

	@Column(name = "answered_at")
	private Instant answeredAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CustomerInquiry() {
	}

	public CustomerInquiry(
		String customerName,
		String email,
		String phone,
		String subject,
		String message,
		String consentPolicyVersion,
		Instant consentedAt,
		Instant retentionExpiresAt
	) {
		this.customerName = customerName;
		this.email = email;
		this.phone = phone;
		this.subject = subject;
		this.message = message;
		this.status = CustomerInquiryStatus.RECEIVED;
		this.consentPolicyVersion = consentPolicyVersion;
		this.consentedAt = consentedAt;
		this.retentionExpiresAt = retentionExpiresAt;
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		createdAt = createdAt == null ? now : createdAt;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
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

	public CustomerInquiryStatus getStatus() {
		return status;
	}

	public String getConsentPolicyVersion() {
		return consentPolicyVersion;
	}

	public Instant getConsentedAt() {
		return consentedAt;
	}

	public Instant getRetentionExpiresAt() {
		return retentionExpiresAt;
	}

	public String getAdminMemo() {
		return adminMemo;
	}

	public String getAnswer() {
		return answer;
	}

	public UUID getHandledByAdminId() {
		return handledByAdminId;
	}

	public Instant getAnsweredAt() {
		return answeredAt;
	}

	public Instant getClosedAt() {
		return closedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void changeStatus(CustomerInquiryStatus nextStatus, String adminMemo, UUID adminUserId, Instant now) {
		if (nextStatus == CustomerInquiryStatus.ANSWERED) {
			throw new IllegalStateException("Answer must be registered through the answer action");
		}
		if (status != nextStatus && !canTransitionTo(nextStatus)) {
			throw new IllegalStateException("Inquiry status cannot change from %s to %s".formatted(status, nextStatus));
		}
		status = nextStatus;
		this.adminMemo = blankToNull(adminMemo);
		handledByAdminId = adminUserId;
		closedAt = nextStatus == CustomerInquiryStatus.CLOSED ? now : null;
	}

	public void answer(String answer, String adminMemo, UUID adminUserId, Instant now) {
		if (status == CustomerInquiryStatus.CLOSED) {
			throw new IllegalStateException("Closed inquiry must be reopened before answering");
		}
		this.answer = answer;
		this.adminMemo = blankToNull(adminMemo);
		this.handledByAdminId = adminUserId;
		this.answeredAt = now;
		this.closedAt = null;
		this.status = CustomerInquiryStatus.ANSWERED;
	}

	private boolean canTransitionTo(CustomerInquiryStatus nextStatus) {
		return switch (status) {
			case RECEIVED -> nextStatus == CustomerInquiryStatus.IN_PROGRESS || nextStatus == CustomerInquiryStatus.CLOSED;
			case IN_PROGRESS -> nextStatus == CustomerInquiryStatus.CLOSED;
			case ANSWERED -> nextStatus == CustomerInquiryStatus.IN_PROGRESS || nextStatus == CustomerInquiryStatus.CLOSED;
			case CLOSED -> nextStatus == CustomerInquiryStatus.IN_PROGRESS;
		};
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
