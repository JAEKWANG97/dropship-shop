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
@Table(name = "admin_order_action_histories")
public class AdminOrderActionHistory {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private CustomerOrder order;

	@Column(name = "admin_user_id", nullable = false)
	private UUID adminUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action_type", nullable = false, length = 50)
	private AdminOrderActionType actionType;

	@Enumerated(EnumType.STRING)
	@Column(name = "before_status", nullable = false, length = 30)
	private OrderStatus beforeStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "after_status", nullable = false, length = 30)
	private OrderStatus afterStatus;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AdminOrderActionHistory() {
	}

	public AdminOrderActionHistory(
		CustomerOrder order,
		UUID adminUserId,
		AdminOrderActionType actionType,
		OrderStatus beforeStatus,
		OrderStatus afterStatus,
		String reason
	) {
		this.order = order;
		this.adminUserId = adminUserId;
		this.actionType = actionType;
		this.beforeStatus = beforeStatus;
		this.afterStatus = afterStatus;
		this.reason = reason;
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public CustomerOrder getOrder() {
		return order;
	}

	public UUID getAdminUserId() {
		return adminUserId;
	}

	public AdminOrderActionType getActionType() {
		return actionType;
	}

	public OrderStatus getBeforeStatus() {
		return beforeStatus;
	}

	public OrderStatus getAfterStatus() {
		return afterStatus;
	}

	public String getReason() {
		return reason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
