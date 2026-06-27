package com.dropshipshop.api.payment.domain;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.order.domain.CustomerOrder;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_events")
public class PaymentEvent {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_id")
	private Payment payment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payment_group_id", nullable = false)
	private PaymentGroup paymentGroup;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private CustomerOrder order;

	@Column(name = "provider_payment_key", length = 200)
	private String providerPaymentKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 40)
	private PaymentEventType eventType;

	@Column(name = "idempotency_key", length = 200)
	private String idempotencyKey;

	@Column(name = "raw_payload", columnDefinition = "TEXT")
	private String rawPayload;

	@Column(name = "result_message", length = 1000)
	private String resultMessage;

	@Column(name = "received_at", nullable = false)
	private Instant receivedAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PaymentEvent() {
	}

	public PaymentEvent(
		Payment payment,
		PaymentGroup paymentGroup,
		String providerPaymentKey,
		PaymentEventType eventType,
		String resultMessage,
		Instant now
	) {
		this.payment = payment;
		this.paymentGroup = paymentGroup;
		this.providerPaymentKey = providerPaymentKey;
		this.eventType = eventType;
		this.resultMessage = resultMessage;
		this.receivedAt = now;
		this.processedAt = now;
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}
}
