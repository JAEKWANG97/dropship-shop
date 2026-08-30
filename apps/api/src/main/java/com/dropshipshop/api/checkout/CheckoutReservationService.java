package com.dropshipshop.api.checkout;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.dropshipshop.api.catalog.domain.InventoryMode;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderItemReservationStatus;

@Service
public class CheckoutReservationService {

	public void consume(CheckoutLockService.LockedCheckout checkout, Instant now) {
		for (OrderItem item : checkout.items()) {
			if (item.getInventoryModeSnapshot() != InventoryMode.TRACKED) {
				continue;
			}
			if (item.getReservationStatus() != OrderItemReservationStatus.HELD
				|| !item.consumeReservation(now)) {
				throw new IllegalStateException("Deposit confirmation requires a held reservation");
			}
			trackedOption(checkout, item).consumeReservation(item.getQuantity());
		}
	}

	public void release(CheckoutLockService.LockedCheckout checkout, Instant now) {
		for (OrderItem item : checkout.items()) {
			if (item.getInventoryModeSnapshot() != InventoryMode.TRACKED) {
				if (item.getReservationStatus() != OrderItemReservationStatus.NOT_APPLICABLE) {
					throw new IllegalStateException("Untracked checkout has reservation evidence");
				}
				continue;
			}
			if (item.getReservationStatus() == OrderItemReservationStatus.RELEASED) {
				continue;
			}
			if (item.getReservationStatus() != OrderItemReservationStatus.HELD
				|| !item.releaseReservation(now)) {
				throw new IllegalStateException("Checkout expiry requires a held reservation");
			}
			trackedOption(checkout, item).releaseReservation(item.getQuantity());
		}
	}

	public boolean canReacquire(CheckoutLockService.LockedCheckout checkout) {
		Map<UUID, Integer> quantities = reacquireQuantities(checkout);
		for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
			ProductOption option = checkout.optionsById().get(entry.getKey());
			if (option == null || !option.isTracked() || !option.canReserve(entry.getValue())) {
				return false;
			}
		}
		return true;
	}

	public void reacquireAndConsume(CheckoutLockService.LockedCheckout checkout, Instant now) {
		Map<UUID, Integer> quantities = reacquireQuantities(checkout);
		if (!canReacquire(checkout)) {
			throw new IllegalStateException("Checkout inventory cannot be reacquired");
		}
		for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
			checkout.optionsById().get(entry.getKey()).reacquireAndConsume(entry.getValue());
		}
		for (OrderItem item : checkout.items()) {
			if (item.getInventoryModeSnapshot() == InventoryMode.TRACKED
				&& !item.reacquireAndConsumeReservation(now)) {
				throw new IllegalStateException("Checkout reservation is not released");
			}
		}
	}

	private Map<UUID, Integer> reacquireQuantities(CheckoutLockService.LockedCheckout checkout) {
		Map<UUID, Integer> quantities = new LinkedHashMap<>();
		for (OrderItem item : checkout.items()) {
			if (item.getInventoryModeSnapshot() != InventoryMode.TRACKED) {
				continue;
			}
			if (item.getReservationStatus() != OrderItemReservationStatus.RELEASED) {
				throw new IllegalStateException("Late deposit requires released reservation evidence");
			}
			ProductOption option = trackedOption(checkout, item);
			quantities.merge(option.getId(), item.getQuantity(), Math::addExact);
		}
		return quantities;
	}

	private ProductOption trackedOption(CheckoutLockService.LockedCheckout checkout, OrderItem item) {
		ProductOption option = checkout.optionsById().get(item.getProductOption().getId());
		if (option == null || !option.isTracked() || item.getInventoryModeSnapshot() != InventoryMode.TRACKED) {
			throw new IllegalStateException("Checkout inventory mode no longer matches its snapshot");
		}
		return option;
	}
}
