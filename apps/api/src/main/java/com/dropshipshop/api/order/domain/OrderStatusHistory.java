package com.dropshipshop.api.order.domain;

import java.time.Instant;
import java.util.UUID;

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
@Table(name = "order_status_histories")
public class OrderStatusHistory {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private CustomerOrder order;

	@Column(name = "actor_user_id")
	private UUID actorUserId;

	@Column(name = "action_type", nullable = false, length = 80)
	private String actionType;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", nullable = false, length = 30)
	private OrderStatus fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, length = 30)
	private OrderStatus toStatus;

	@Column(name = "guard_result", nullable = false, length = 100)
	private String guardResult;

	@Column(name = "side_effect_summary", nullable = false, columnDefinition = "TEXT")
	private String sideEffectSummary;

	@Column(columnDefinition = "TEXT")
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected OrderStatusHistory() {
	}

	public OrderStatusHistory(
		CustomerOrder order,
		UUID actorUserId,
		String actionType,
		OrderStatus fromStatus,
		OrderStatus toStatus,
		String guardResult,
		String sideEffectSummary,
		String reason
	) {
		this.order = order;
		this.actorUserId = actorUserId;
		this.actionType = actionType;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.guardResult = guardResult;
		this.sideEffectSummary = sideEffectSummary;
		this.reason = reason;
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getActionType() {
		return actionType;
	}

	public OrderStatus getFromStatus() {
		return fromStatus;
	}

	public OrderStatus getToStatus() {
		return toStatus;
	}

	public String getReason() {
		return reason;
	}
}
