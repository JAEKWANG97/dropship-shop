package com.dropshipshop.api.checkout;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.domain.InventoryMode;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItemReservationStatus;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;

@Service
public class CheckoutExpiryService {

	private final CheckoutLockService checkoutLockService;
	private final CheckoutReservationService reservationService;

	CheckoutExpiryService(
		CheckoutLockService checkoutLockService,
		CheckoutReservationService reservationService
	) {
		this.checkoutLockService = checkoutLockService;
		this.reservationService = reservationService;
	}

	@Transactional
	public boolean expire(UUID paymentGroupId, Instant now) {
		CheckoutLockService.LockedCheckout checkout = checkoutLockService.lock(paymentGroupId);
		if (checkout.paymentGroup().getStatus() != PaymentGroupStatus.PAYMENT_PENDING
			|| checkout.paymentGroup().getExpiresAt().isAfter(now)) {
			return false;
		}
		boolean hasPortalTrackedReservation = checkout.items().stream().anyMatch(item ->
			item.getManagementChannelSnapshot() == ProductManagementChannel.SUPPLIER_PORTAL
				&& item.getInventoryModeSnapshot() == InventoryMode.TRACKED
				&& item.getReservationStatus() == OrderItemReservationStatus.HELD
		);
		if (!hasPortalTrackedReservation) {
			return false;
		}
		if (checkout.orders().stream().anyMatch(order -> order.getStatus() != OrderStatus.PAYMENT_PENDING)) {
			throw new IllegalStateException("Pending checkout has inconsistent order states");
		}
		reservationService.release(checkout, now);
		if (!checkout.paymentGroup().expire()) {
			return false;
		}
		for (CustomerOrder order : checkout.orders()) {
			if (!order.expire()) {
				throw new IllegalStateException("Pending checkout order could not expire");
			}
		}
		return true;
	}
}
