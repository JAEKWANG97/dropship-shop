package com.dropshipshop.api.shipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.repository.AdminOrderActionHistoryRepository;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.shipment.ShipmentTrackingDtos.InternalTrackingSyncItem;
import com.dropshipshop.api.shipment.ShipmentTrackingDtos.InternalTrackingSyncRequest;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;

class ShipmentTrackingServiceLockingTest {

	@Test
	void locksOrdersByOrderIdEvenWhenInternalTrackingInputIsReversed() {
		ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
		CustomerOrderRepository orderRepository = mock(CustomerOrderRepository.class);
		FulfillmentRepository fulfillmentRepository = mock(FulfillmentRepository.class);
		UUID lowerOrderId = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID higherOrderId = UUID.fromString("80000000-0000-0000-0000-000000000002");
		UUID lowerShipmentId = UUID.fromString("10000000-0000-0000-0000-000000000001");
		UUID higherShipmentId = UUID.fromString("90000000-0000-0000-0000-000000000002");
		CustomerOrder lowerOrder = mock(CustomerOrder.class);
		CustomerOrder higherOrder = mock(CustomerOrder.class);
		Shipment lowerShipment = mock(Shipment.class);
		Shipment higherShipment = mock(Shipment.class);
		when(lowerOrder.getId()).thenReturn(lowerOrderId);
		when(higherOrder.getId()).thenReturn(higherOrderId);
		when(lowerShipment.getId()).thenReturn(lowerShipmentId);
		when(higherShipment.getId()).thenReturn(higherShipmentId);
		when(lowerShipment.getOrder()).thenReturn(lowerOrder);
		when(higherShipment.getOrder()).thenReturn(higherOrder);
		when(shipmentRepository.findAllByCarrierAndTrackingNumber("HIGH", "HIGH-TRACK"))
			.thenReturn(List.of(higherShipment));
		when(shipmentRepository.findAllByCarrierAndTrackingNumber("LOW", "LOW-TRACK"))
			.thenReturn(List.of(lowerShipment));
		when(shipmentRepository.findOrderIdByShipmentId(lowerShipmentId)).thenReturn(Optional.of(lowerOrderId));
		when(shipmentRepository.findOrderIdByShipmentId(higherShipmentId)).thenReturn(Optional.of(higherOrderId));
		when(orderRepository.findByIdForUpdate(lowerOrderId)).thenReturn(Optional.of(lowerOrder));
		when(orderRepository.findByIdForUpdate(higherOrderId)).thenReturn(Optional.of(higherOrder));
		when(fulfillmentRepository.findByOrderIdForUpdate(lowerOrderId)).thenReturn(Optional.empty());
		when(fulfillmentRepository.findByOrderIdForUpdate(higherOrderId)).thenReturn(Optional.empty());
		when(shipmentRepository.findAllByOrderIdForUpdate(lowerOrderId)).thenReturn(List.of(lowerShipment));
		when(shipmentRepository.findAllByOrderIdForUpdate(higherOrderId)).thenReturn(List.of(higherShipment));
		ShipmentTrackingService service = new ShipmentTrackingService(
			shipmentRepository,
			orderRepository,
			fulfillmentRepository,
			mock(AdminOrderActionHistoryRepository.class),
			mock(OrderStatusHistoryRepository.class),
			mock(NotificationService.class)
		);

		var response = service.syncInternal(new InternalTrackingSyncRequest(List.of(
			new InternalTrackingSyncItem("HIGH", "HIGH-TRACK", null, "timeout"),
			new InternalTrackingSyncItem("LOW", "LOW-TRACK", null, "timeout")
		)));

		assertThat(response.received()).isEqualTo(2);
		assertThat(response.matched()).isEqualTo(2);
		assertThat(response.failed()).isEqualTo(2);
		InOrder locking = inOrder(shipmentRepository, orderRepository, fulfillmentRepository);
		locking.verify(shipmentRepository).findAllByCarrierAndTrackingNumber("HIGH", "HIGH-TRACK");
		locking.verify(shipmentRepository).findAllByCarrierAndTrackingNumber("LOW", "LOW-TRACK");
		locking.verify(shipmentRepository).findOrderIdByShipmentId(lowerShipmentId);
		locking.verify(orderRepository).findByIdForUpdate(lowerOrderId);
		locking.verify(fulfillmentRepository).findByOrderIdForUpdate(lowerOrderId);
		locking.verify(shipmentRepository).findAllByOrderIdForUpdate(lowerOrderId);
		locking.verify(shipmentRepository).findOrderIdByShipmentId(higherShipmentId);
		locking.verify(orderRepository).findByIdForUpdate(higherOrderId);
		locking.verify(fulfillmentRepository).findByOrderIdForUpdate(higherOrderId);
		locking.verify(shipmentRepository).findAllByOrderIdForUpdate(higherOrderId);
	}
}
