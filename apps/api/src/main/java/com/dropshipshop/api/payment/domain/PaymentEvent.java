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

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

	@Enumerated(EnumType.STRING)
	@Column(name = "command_type", length = 60)
	private PaymentCommandType commandType;

	@Column(name = "request_hash", length = 128)
	private String requestHash;

	@Column(name = "result_snapshot", columnDefinition = "jsonb")
	@JdbcTypeCode(SqlTypes.JSON)
	private String resultSnapshot;

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
		this(payment, paymentGroup, null, providerPaymentKey, eventType, null, null, resultMessage, now);
	}

	public PaymentEvent(
		Payment payment,
		PaymentGroup paymentGroup,
		CustomerOrder order,
		String providerPaymentKey,
		PaymentEventType eventType,
		String resultMessage,
		Instant now
	) {
		this(payment, paymentGroup, order, providerPaymentKey, eventType, null, null, resultMessage, now);
	}

	public PaymentEvent(
		Payment payment,
		PaymentGroup paymentGroup,
		String providerPaymentKey,
		PaymentEventType eventType,
		String idempotencyKey,
		String rawPayload,
		String resultMessage,
		Instant now
	) {
		this(payment, paymentGroup, null, providerPaymentKey, eventType, idempotencyKey, rawPayload, resultMessage, now);
	}

	private PaymentEvent(
		Payment payment,
		PaymentGroup paymentGroup,
		CustomerOrder order,
		String providerPaymentKey,
		PaymentEventType eventType,
		String idempotencyKey,
		String rawPayload,
		String resultMessage,
		Instant now
	) {
		this.payment = payment;
		this.paymentGroup = paymentGroup;
		this.order = order;
		this.providerPaymentKey = providerPaymentKey;
		this.eventType = eventType;
		this.idempotencyKey = idempotencyKey;
		this.rawPayload = rawPayload;
		this.resultMessage = resultMessage;
		this.receivedAt = now;
		this.processedAt = now;
	}

	public static PaymentEvent command(
		Payment payment,
		PaymentGroup paymentGroup,
		CustomerOrder order,
		String providerPaymentKey,
		PaymentEventType eventType,
		PaymentCommandType commandType,
		String idempotencyKey,
		String requestHash,
		String resultSnapshot,
		String resultMessage,
		Instant now
	) {
		PaymentEvent event = new PaymentEvent(
			payment,
			paymentGroup,
			order,
			providerPaymentKey,
			eventType,
			idempotencyKey,
			null,
			resultMessage,
			now
		);
		event.commandType = commandType;
		event.requestHash = requestHash;
		event.resultSnapshot = resultSnapshot;
		return event;
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}

	public boolean matchesCommand(PaymentCommandType commandType, String requestHash) {
		return this.commandType == commandType && java.util.Objects.equals(this.requestHash, requestHash);
	}

	public String getResultSnapshot() {
		return resultSnapshot;
	}

	public UUID getOrderId() {
		return order == null ? null : order.getId();
	}

	public PaymentCommandType getCommandType() {
		return commandType;
	}

	public String getRequestHash() {
		return requestHash;
	}
}
