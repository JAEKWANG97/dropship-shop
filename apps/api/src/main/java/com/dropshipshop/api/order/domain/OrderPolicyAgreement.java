package com.dropshipshop.api.order.domain;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.user.domain.UserAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_policy_agreements")
public class OrderPolicyAgreement {

	@Id
	@GeneratedValue
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payment_group_id", nullable = false)
	private PaymentGroup paymentGroup;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@Column(name = "terms_version", nullable = false, length = 50)
	private String termsVersion;

	@Column(name = "privacy_version", nullable = false, length = 50)
	private String privacyVersion;

	@Column(name = "order_policy_version", nullable = false, length = 50)
	private String orderPolicyVersion;

	@Column(name = "cancellation_refund_policy_version", nullable = false, length = 50)
	private String cancellationRefundPolicyVersion;

	@Column(name = "out_of_stock_notice_version", nullable = false, length = 50)
	private String outOfStockNoticeVersion;

	@Column(name = "confirmed_notice_text", nullable = false, columnDefinition = "TEXT")
	private String confirmedNoticeText;

	@Column(name = "confirmed_at", nullable = false)
	private Instant confirmedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected OrderPolicyAgreement() {
	}

	public OrderPolicyAgreement(
		PaymentGroup paymentGroup,
		UserAccount user,
		String termsVersion,
		String privacyVersion,
		String orderPolicyVersion,
		String cancellationRefundPolicyVersion,
		String outOfStockNoticeVersion,
		String confirmedNoticeText,
		Instant confirmedAt
	) {
		this.paymentGroup = paymentGroup;
		this.user = user;
		this.termsVersion = termsVersion;
		this.privacyVersion = privacyVersion;
		this.orderPolicyVersion = orderPolicyVersion;
		this.cancellationRefundPolicyVersion = cancellationRefundPolicyVersion;
		this.outOfStockNoticeVersion = outOfStockNoticeVersion;
		this.confirmedNoticeText = confirmedNoticeText;
		this.confirmedAt = confirmedAt;
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}

	public String getTermsVersion() {
		return termsVersion;
	}

	public String getPrivacyVersion() {
		return privacyVersion;
	}

	public String getOrderPolicyVersion() {
		return orderPolicyVersion;
	}

	public String getCancellationRefundPolicyVersion() {
		return cancellationRefundPolicyVersion;
	}

	public String getOutOfStockNoticeVersion() {
		return outOfStockNoticeVersion;
	}

	public String getConfirmedNoticeText() {
		return confirmedNoticeText;
	}
}
