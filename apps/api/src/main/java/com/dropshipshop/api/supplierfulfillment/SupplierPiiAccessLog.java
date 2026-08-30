package com.dropshipshop.api.supplierfulfillment;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.dropshipshop.api.order.domain.CustomerOrder;
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
import jakarta.persistence.Table;

@Entity
@Table(name = "supplier_pii_access_logs")
public class SupplierPiiAccessLog {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "actor_user_id", nullable = false)
	private UserAccount actorUser;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private CustomerOrder order;

	@Enumerated(EnumType.STRING)
	@Column(name = "access_reason", nullable = false, length = 30)
	private SupplierPiiAccessReason accessReason;

	@Column(name = "accessed_at", nullable = false, updatable = false)
	private Instant accessedAt;

	protected SupplierPiiAccessLog() {
	}

	public SupplierPiiAccessLog(
		UserAccount actorUser,
		CustomerOrder order,
		SupplierPiiAccessReason accessReason,
		Instant accessedAt
	) {
		this.actorUser = Objects.requireNonNull(actorUser, "actorUser");
		this.order = Objects.requireNonNull(order, "order");
		this.accessReason = Objects.requireNonNull(accessReason, "accessReason");
		this.accessedAt = Objects.requireNonNull(accessedAt, "accessedAt");
	}

	public UUID getId() { return id; }
	public UserAccount getActorUser() { return actorUser; }
	public CustomerOrder getOrder() { return order; }
	public SupplierPiiAccessReason getAccessReason() { return accessReason; }
	public Instant getAccessedAt() { return accessedAt; }
}
