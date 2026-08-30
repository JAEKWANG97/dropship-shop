package com.dropshipshop.api.order.domain;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.common.money.MoneyMath;
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
import jakarta.persistence.Version;

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

	@Column(name = "delivery_memo", length = 300)
	private String deliveryMemo;

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

	@Version
	@Column(nullable = false)
	private long version;

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
		this.deliveryMemo = normalizedDeliveryMemo(address.deliveryMemo());
		this.subtotalAmount = MoneyMath.requirePositive(subtotalAmount, "subtotalAmount");
		this.shippingFee = 0;
		this.discountAmount = 0;
		this.totalAmount = this.subtotalAmount;
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

	public void confirmBankTransferDeposit() {
		if (status != OrderStatus.PAYMENT_PENDING) {
			throw new IllegalStateException("Deposit can be confirmed only while payment is pending");
		}
		this.status = OrderStatus.SUPPLIER_ORDER_PENDING;
	}

	public void confirmLateBankTransferDeposit() {
		if (status != OrderStatus.EXPIRED) {
			throw new IllegalStateException("Late deposit can be confirmed only after checkout expiry");
		}
		this.status = OrderStatus.SUPPLIER_ORDER_PENDING;
	}

	public void cancelUnpaidDeposit() {
		if (status != OrderStatus.PAYMENT_PENDING) {
			throw new IllegalStateException("Unpaid deposit can be cancelled only while payment is pending");
		}
		this.status = OrderStatus.CANCELLED;
	}

	public void startSupplierOrderWork(UUID adminUserId, Instant startedAt) {
		if (status != OrderStatus.SUPPLIER_ORDER_PENDING || supplierOrderStartedAt != null || addressLockedAt != null) {
			throw new IllegalStateException("Supplier order work can start only once from supplier order pending");
		}
		this.supplierOrderStartedAt = startedAt;
		this.addressLockedAt = startedAt;
		this.addressLockedByAdminId = adminUserId;
	}

	public void lockAddressForSupplierPortal(Instant lockedAt) {
		if (status != OrderStatus.SUPPLIER_ORDER_PENDING || supplierOrderStartedAt != null || addressLockedAt != null) {
			throw new IllegalStateException("Supplier portal address can be locked only once after payment approval");
		}
		this.addressLockedAt = java.util.Objects.requireNonNull(lockedAt, "lockedAt");
		this.addressLockedByAdminId = null;
	}

	public void markSupplierOrdered() {
		if (status != OrderStatus.SUPPLIER_ORDER_PENDING || supplierOrderStartedAt == null || addressLockedAt == null) {
			throw new IllegalStateException("Supplier order can be completed only after supplier work has started");
		}
		this.status = OrderStatus.SUPPLIER_ORDERED;
	}

	public void markOutOfStock() {
		if (status != OrderStatus.SUPPLIER_ORDER_PENDING && status != OrderStatus.SUPPLIER_ORDERED) {
			throw new IllegalStateException("Out of stock can be marked only before shipment");
		}
		this.status = OrderStatus.OUT_OF_STOCK;
	}

	public void markShipped() {
		if (status != OrderStatus.SUPPLIER_ORDERED) {
			throw new IllegalStateException("Shipment can be entered only after supplier order completion");
		}
		this.status = OrderStatus.SHIPPED;
	}

	public void markDeliveredByTracking() {
		if (status != OrderStatus.SHIPPED) {
			throw new IllegalStateException("Order can be delivered only after shipment");
		}
		this.status = OrderStatus.DELIVERED;
	}

	public void markDeliveredByAdminCorrection() {
		if (status != OrderStatus.SHIPPED) {
			throw new IllegalStateException("Order can be manually delivered only after shipment");
		}
		this.status = OrderStatus.DELIVERED;
	}

	public boolean isSelfServiceCancellable() {
		return status == OrderStatus.SUPPLIER_ORDER_PENDING
			&& supplierOrderStartedAt == null
			&& addressLockedAt == null;
	}

	public boolean canRequestCancellationClaim() {
		return (status == OrderStatus.SUPPLIER_ORDER_PENDING && (supplierOrderStartedAt != null || addressLockedAt != null))
			|| status == OrderStatus.SUPPLIER_ORDERED;
	}

	public void markRefundRequested() {
		if (status == OrderStatus.REFUND_REQUESTED) {
			return;
		}
		if (status != OrderStatus.SUPPLIER_ORDER_PENDING
			&& status != OrderStatus.SUPPLIER_ORDERED
			&& status != OrderStatus.OUT_OF_STOCK
			&& status != OrderStatus.DELIVERED
			&& status != OrderStatus.PAYMENT_EXCEPTION) {
			throw new IllegalStateException("Refund can be requested only before refund completion");
		}
		this.status = OrderStatus.REFUND_REQUESTED;
	}

	public void markRefunded() {
		if (status != OrderStatus.REFUND_REQUESTED) {
			throw new IllegalStateException("Order can be refunded only after refund request");
		}
		this.status = OrderStatus.REFUNDED;
	}

	public void markPaymentException() {
		if (status == OrderStatus.PAYMENT_EXCEPTION) {
			return;
		}
		if (status != OrderStatus.PAYMENT_PENDING
			&& status != OrderStatus.EXPIRED
			&& status != OrderStatus.CANCELLED) {
			throw new IllegalStateException("Payment exception cannot be recorded from the current order state");
		}
		this.status = OrderStatus.PAYMENT_EXCEPTION;
	}

	public void markCancelledFromPaymentException() {
		if (status != OrderStatus.PAYMENT_EXCEPTION) {
			throw new IllegalStateException("Payment exception order can be cancelled only from payment exception");
		}
		this.status = OrderStatus.CANCELLED;
	}

	public boolean expire() {
		if (status != OrderStatus.PAYMENT_PENDING) {
			return false;
		}
		this.status = OrderStatus.EXPIRED;
		return true;
	}

	public void updatePaymentPendingAddress(ShippingAddressSnapshot address) {
		if (status != OrderStatus.PAYMENT_PENDING) {
			throw new IllegalStateException("Checkout address can be changed only before payment confirmation");
		}
		updateAddress(address);
	}

	public void updateCustomerAddressBeforeSupplierWork(ShippingAddressSnapshot address) {
		if (status != OrderStatus.SUPPLIER_ORDER_PENDING || supplierOrderStartedAt != null || addressLockedAt != null) {
			throw new IllegalStateException("Order address can be changed only before supplier work starts");
		}
		updateAddress(address);
	}

	private void updateAddress(ShippingAddressSnapshot address) {
		this.recipientName = address.recipientName();
		this.recipientPhone = address.recipientPhone();
		this.postalCode = address.postalCode();
		this.address1 = address.address1();
		this.address2 = address.address2();
		this.deliveryMemo = normalizedDeliveryMemo(address.deliveryMemo());
	}

	private String normalizedDeliveryMemo(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.trim();
		if (normalized.length() > 300) {
			throw new IllegalArgumentException("Delivery memo must be at most 300 characters");
		}
		return normalized;
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

	public String getDeliveryMemo() {
		return deliveryMemo;
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

	public Instant getSupplierOrderStartedAt() {
		return supplierOrderStartedAt;
	}

	public Instant getAddressLockedAt() {
		return addressLockedAt;
	}

	public UUID getAddressLockedByAdminId() {
		return addressLockedByAdminId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public long getVersion() {
		return version;
	}
}
