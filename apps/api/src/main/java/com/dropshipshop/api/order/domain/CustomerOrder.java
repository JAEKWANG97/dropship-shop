package com.dropshipshop.api.order.domain;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.payment.domain.PaymentGroup;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class CustomerOrder {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "order_number", nullable = false, length = 40, unique = true)
	private String orderNumber;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserAccount user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false)
	private Supplier supplier;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "payment_group_id", nullable = false)
	private PaymentGroup paymentGroup;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private OrderStatus status = OrderStatus.PAYMENT_PENDING;

	@Column(name = "recipient_name", nullable = false, length = 100)
	private String recipientName;

	@Column(name = "recipient_phone", nullable = false, length = 30)
	private String recipientPhone;

	@Column(name = "postal_code", nullable = false, length = 20)
	private String postalCode;

	@Column(nullable = false, length = 300)
	private String address1;

	@Column(length = 300)
	private String address2;

	@Column(name = "subtotal_amount", nullable = false)
	private long subtotalAmount;

	@Column(name = "shipping_fee", nullable = false)
	private long shippingFee;

	@Column(name = "discount_amount", nullable = false)
	private long discountAmount;

	@Column(name = "total_amount", nullable = false)
	private long totalAmount;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "supplier_order_started_at")
	private Instant supplierOrderStartedAt;

	@Column(name = "address_locked_at")
	private Instant addressLockedAt;

	@Column(name = "address_locked_by_admin_id")
	private UUID addressLockedByAdminId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CustomerOrder() {
	}

	public CustomerOrder(
		String orderNumber,
		UserAccount user,
		Supplier supplier,
		PaymentGroup paymentGroup,
		ShippingAddressSnapshot address,
		long subtotalAmount,
		Instant expiresAt
	) {
		this.orderNumber = orderNumber;
		this.user = user;
		this.supplier = supplier;
		this.paymentGroup = paymentGroup;
		this.recipientName = address.recipientName();
		this.recipientPhone = address.recipientPhone();
		this.postalCode = address.postalCode();
		this.address1 = address.address1();
		this.address2 = address.address2();
		this.subtotalAmount = subtotalAmount;
		this.shippingFee = 0;
		this.discountAmount = 0;
		this.totalAmount = subtotalAmount;
		this.expiresAt = expiresAt;
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

	public void markSupplierOrderPending() {
		this.status = OrderStatus.SUPPLIER_ORDER_PENDING;
	}

	public void markPaymentException() {
		this.status = OrderStatus.PAYMENT_EXCEPTION;
	}

	public void expire() {
		this.status = OrderStatus.EXPIRED;
	}

	public UUID getId() {
		return id;
	}

	public String getOrderNumber() {
		return orderNumber;
	}

	public UserAccount getUser() {
		return user;
	}

	public Supplier getSupplier() {
		return supplier;
	}

	public PaymentGroup getPaymentGroup() {
		return paymentGroup;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public String getRecipientName() {
		return recipientName;
	}

	public String getRecipientPhone() {
		return recipientPhone;
	}

	public String getPostalCode() {
		return postalCode;
	}

	public String getAddress1() {
		return address1;
	}

	public String getAddress2() {
		return address2;
	}

	public long getSubtotalAmount() {
		return subtotalAmount;
	}

	public long getShippingFee() {
		return shippingFee;
	}

	public long getDiscountAmount() {
		return discountAmount;
	}

	public long getTotalAmount() {
		return totalAmount;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
