package com.dropshipshop.api.shipment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "shipment_items",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_shipment_items_shipment_order_item",
		columnNames = {"shipment_id", "order_item_id"}
	)
)
public class ShipmentItem {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shipment_id", nullable = false, updatable = false)
	private Shipment shipment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_item_id", nullable = false, updatable = false)
	private OrderItem orderItem;

	@Column(nullable = false, updatable = false)
	private int quantity;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ShipmentItem() {
	}

	public ShipmentItem(Shipment shipment, OrderItem orderItem, int quantity) {
		this.shipment = Objects.requireNonNull(shipment, "shipment");
		this.orderItem = Objects.requireNonNull(orderItem, "orderItem");
		if (quantity <= 0) {
			throw new IllegalArgumentException("Shipment item quantity must be positive");
		}
		if (!sameOrder(shipment.getOrder(), orderItem.getOrder())) {
			throw new IllegalArgumentException("Shipment item must belong to the shipment order");
		}
		this.quantity = quantity;
	}

	private static boolean sameOrder(CustomerOrder shipmentOrder, CustomerOrder itemOrder) {
		if (shipmentOrder == itemOrder) {
			return true;
		}
		if (shipmentOrder == null || itemOrder == null) {
			return false;
		}
		return shipmentOrder.getId() != null && shipmentOrder.getId().equals(itemOrder.getId());
	}

	@PrePersist
	void prePersist() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public UUID getId() { return id; }
	public Shipment getShipment() { return shipment; }
	public OrderItem getOrderItem() { return orderItem; }
	public int getQuantity() { return quantity; }
	public Instant getCreatedAt() { return createdAt; }
}
