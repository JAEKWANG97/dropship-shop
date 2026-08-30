package com.dropshipshop.api.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.dropshipshop.api.catalog.domain.InventoryMode;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.Supplier;

class OrderItemReservationTest {

	@Test
	void trackedReservationRecordsReleaseAndReacquireWithoutLosingEvidence() {
		Instant reservedAt = Instant.parse("2026-08-30T00:00:00Z");
		Instant releasedAt = reservedAt.plusSeconds(60);
		Instant reacquiredAt = releasedAt.plusSeconds(60);
		OrderItem item = item(InventoryMode.TRACKED, reservedAt);

		assertThat(item.getManagementChannelSnapshot()).isEqualTo(ProductManagementChannel.SUPPLIER_PORTAL);
		assertThat(item.getInventoryModeSnapshot()).isEqualTo(InventoryMode.TRACKED);
		assertThat(item.getReservationStatus()).isEqualTo(OrderItemReservationStatus.HELD);
		assertThat(item.getReservedAt()).isEqualTo(reservedAt);

		assertThat(item.releaseReservation(releasedAt)).isTrue();
		assertThat(item.releaseReservation(releasedAt.plusSeconds(1))).isFalse();
		assertThat(item.reacquireAndConsumeReservation(reacquiredAt)).isTrue();
		assertThat(item.reacquireAndConsumeReservation(reacquiredAt.plusSeconds(1))).isFalse();
		assertThat(item.getReservationStatus()).isEqualTo(OrderItemReservationStatus.CONSUMED);
		assertThat(item.getReleasedAt()).isEqualTo(releasedAt);
		assertThat(item.getReacquiredAt()).isEqualTo(reacquiredAt);
		assertThat(item.getConsumedAt()).isEqualTo(reacquiredAt);
	}

	@Test
	void untrackedReservationIsNotApplicableAndTransitionsAreNoOps() {
		Instant now = Instant.parse("2026-08-30T00:00:00Z");
		OrderItem item = item(InventoryMode.UNTRACKED, now);

		assertThat(item.getReservationStatus()).isEqualTo(OrderItemReservationStatus.NOT_APPLICABLE);
		assertThat(item.getReservedAt()).isNull();
		assertThat(item.consumeReservation(now)).isFalse();
		assertThat(item.releaseReservation(now)).isFalse();
		assertThat(item.reacquireAndConsumeReservation(now)).isFalse();
	}

	private OrderItem item(InventoryMode inventoryMode, Instant reservationTime) {
		CustomerOrder order = mock(CustomerOrder.class);
		Product product = mock(Product.class);
		ProductOption option = mock(ProductOption.class);
		when(product.getSupplier()).thenReturn(mock(Supplier.class));
		when(product.getName()).thenReturn("Product");
		when(product.getSummary()).thenReturn("Summary");
		when(product.getDetailVersion()).thenReturn(1);
		when(product.getBasePrice()).thenReturn(1_000L);
		when(product.getSourcePrice()).thenReturn(800L);
		when(product.getManagementChannel()).thenReturn(ProductManagementChannel.SUPPLIER_PORTAL);
		when(option.getName()).thenReturn("Option");
		when(option.getInventoryMode()).thenReturn(inventoryMode);
		return new OrderItem(order, product, option, null, 1, reservationTime);
	}
}
