package com.dropshipshop.api.shipment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;

class ShipmentPortalDomainTest {

	@Test
	void keepsCreationReplayImmutableAndSupportsTrackingCorrectionAndVoid() {
		Instant registeredAt = Instant.now().minusSeconds(120);
		Shipment shipment = portalShipment(mock(CustomerOrder.class), registeredAt);

		assertThat(shipment.isPortal()).isTrue();
		assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.TRACKING_REGISTERED);
		assertThat(shipment.getShippedAt()).isNull();
		assertThat(shipment.matchesCreationReplay("create-key", "create-hash")).isTrue();

		shipment.storeCreationResult("{\"shipmentId\":\"safe\"}");
		assertThatThrownBy(() -> shipment.storeCreationResult("{}"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("immutable");

		shipment.correctTracking("LOTTE", "롯데택배", "9876543210");
		assertThat(shipment.getCarrierCode()).isEqualTo("LOTTE");
		assertThat(shipment.getCarrier()).isEqualTo("롯데택배");
		shipment.voidShipment();
		assertThat(shipment.isVoided()).isTrue();
		assertThat(shipment.countsTowardAllocation()).isFalse();
		assertThatThrownBy(() -> shipment.correctTracking("HANJIN", "한진택배", "1"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void completesCorrectsAndReopensOnlyOrderedDeliveryEvidence() {
		Instant registeredAt = Instant.now().minusSeconds(300);
		Shipment shipment = portalShipment(mock(CustomerOrder.class), registeredAt);
		Instant deliveredAt = registeredAt.plusSeconds(60);
		Instant observedAt = deliveredAt.plusSeconds(60);

		shipment.completePortalDelivery(deliveredAt, observedAt);
		assertThat(shipment.isPortalDeliveryEvidence()).isTrue();
		assertThat(shipment.getDeliveredAt()).isEqualTo(deliveredAt);

		Instant correctedDeliveredAt = deliveredAt.plusSeconds(10);
		shipment.correctPortalDeliveredAt(correctedDeliveredAt, observedAt);
		assertThat(shipment.getDeliveredAt()).isEqualTo(correctedDeliveredAt);
		shipment.reopenPortalDelivery();
		assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.TRACKING_REGISTERED);
		assertThat(shipment.getDeliveredAt()).isNull();
		assertThat(shipment.getDeliveryEvidenceObservedAt()).isNull();

		assertThatThrownBy(() -> shipment.completePortalDelivery(
			registeredAt.minusSeconds(1), observedAt
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void allocationIsPositiveImmutableAndScopedToTheShipmentOrder() {
		CustomerOrder order = mock(CustomerOrder.class);
		CustomerOrder anotherOrder = mock(CustomerOrder.class);
		OrderItem orderItem = mock(OrderItem.class);
		OrderItem anotherOrderItem = mock(OrderItem.class);
		when(orderItem.getOrder()).thenReturn(order);
		when(anotherOrderItem.getOrder()).thenReturn(anotherOrder);
		Shipment shipment = portalShipment(order, Instant.now().minusSeconds(60));

		ShipmentItem allocation = new ShipmentItem(shipment, orderItem, 2);
		assertThat(allocation.getQuantity()).isEqualTo(2);
		assertThat(allocation.getShipment()).isSameAs(shipment);
		assertThatThrownBy(() -> new ShipmentItem(shipment, orderItem, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ShipmentItem(shipment, anotherOrderItem, 1))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("shipment order");
	}

	@Test
	void historyValidatesActorAndEvidenceAndStoresOneReplayResult() {
		Shipment shipment = portalShipment(mock(CustomerOrder.class), Instant.now().minusSeconds(120));
		UUID actorId = UUID.randomUUID();
		ShipmentChangeHistory history = ShipmentChangeHistory.command(
			shipment,
			actorId,
			ShipmentActorType.SUPPLIER,
			ShipmentChangeAction.SUPPLIER_CORRECTED,
			"{\"carrierCode\":\"CJ_LOGISTICS\"}",
			"{\"carrierCode\":\"LOTTE\"}",
			"송장번호 오타",
			null,
			"action-hash",
			"action-key"
		);
		history.storeResult("{\"status\":\"TRACKING_REGISTERED\"}");

		assertThat(history.matchesReplay("action-hash")).isTrue();
		assertThat(history.getActorUserId()).isEqualTo(actorId);
		assertThatThrownBy(() -> history.storeResult("{}"))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> ShipmentChangeHistory.command(
			shipment, actorId, ShipmentActorType.SUPPLIER, ShipmentChangeAction.ADMIN_VOIDED,
			"{}", "{}", "invalid actor", null, "hash", "key"
		)).isInstanceOf(IllegalArgumentException.class);
	}

	private Shipment portalShipment(CustomerOrder order, Instant registeredAt) {
		return Shipment.portal(
			order,
			"CJ_LOGISTICS",
			"CJ대한통운",
			"1234567890",
			registeredAt,
			UUID.randomUUID(),
			ShipmentActorType.SUPPLIER,
			"create-key",
			"create-hash"
		);
	}
}
