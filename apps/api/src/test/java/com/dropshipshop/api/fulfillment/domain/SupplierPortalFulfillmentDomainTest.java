package com.dropshipshop.api.fulfillment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.user.domain.UserAccount;

class SupplierPortalFulfillmentDomainTest {

	@Test
	void deliveryMemoKeepsFiveArgumentCompatibilityAndNormalizesBlank() {
		assertThat(new ShippingAddressSnapshot("Name", "010", "12345", "A", "B").deliveryMemo()).isNull();
		CustomerOrder order = orderWithSupplier();
		order.updatePaymentPendingAddress(new ShippingAddressSnapshot("Name", "010", "12345", "A", "B", "  "));
		assertThat(order.getDeliveryMemo()).isNull();
		order.updatePaymentPendingAddress(new ShippingAddressSnapshot(
			"Name", "010", "12345", "A", "B", "  Leave at the door  "));
		assertThat(order.getDeliveryMemo()).isEqualTo("Leave at the door");
		order.updatePaymentPendingAddress(new ShippingAddressSnapshot(
			"Name", "010", "12345", "A", "B", "x".repeat(300)));
		assertThat(order.getDeliveryMemo()).hasSize(300);
		assertThatThrownBy(() -> order.updatePaymentPendingAddress(new ShippingAddressSnapshot(
			"Name", "010", "12345", "A", "B", "x".repeat(301))))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void portalCutoffOnlyShortensAndTerminalHandoverWorksAfterFulfillmentCloses() {
		Instant requestedAt = Instant.parse("2026-08-30T00:00:00Z");
		Fulfillment fulfillment = new Fulfillment(orderWithSupplier());
		fulfillment.routeToSupplierPortal(requestedAt, requestedAt.plusSeconds(60));
		assertThat(fulfillment.shortenPiiAccessCutoffAt(requestedAt.plusSeconds(30))).isTrue();
		assertThat(fulfillment.shortenPiiAccessCutoffAt(requestedAt.plusSeconds(45))).isFalse();
		assertThat(fulfillment.getPiiAccessCutoffAt()).isEqualTo(requestedAt.plusSeconds(30));

		fulfillment.markOutOfStock("Unavailable");
		assertThat(fulfillment.handOverTerminalToCoreable(requestedAt.plusSeconds(31))).isTrue();
		assertThat(fulfillment.getOperationalOwner()).isEqualTo(FulfillmentOperationalOwner.COREABLE);
		assertThat(fulfillment.getHandedOverReason()).isEqualTo("TERMINAL_STATE");
		assertThat(fulfillment.handOverTerminalToCoreable(requestedAt.plusSeconds(32))).isFalse();
	}

	private CustomerOrder orderWithSupplier() {
		Supplier supplier = mock(Supplier.class);
		return new CustomerOrder(
			"O-" + UUID.randomUUID(), mock(UserAccount.class), supplier, mock(PaymentGroup.class),
			new ShippingAddressSnapshot("Name", "010", "12345", "A", "B"),
			1_000, Instant.now().plusSeconds(3600)
		);
	}
}
