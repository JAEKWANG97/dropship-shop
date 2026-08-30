package com.dropshipshop.api.procurement;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.SupplierPurchaseAttempt;
import com.dropshipshop.api.fulfillment.domain.SupplierPurchaseStatus;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.fulfillment.repository.SupplierPurchaseAttemptRepository;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.repository.AdminOrderActionHistoryRepository;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.repository.ShipmentItemRepository;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;
import com.dropshipshop.api.user.domain.UserAccount;

class DomeggookPurchaseServiceTest {

	private final DomeggookPurchaseClient client = mock(DomeggookPurchaseClient.class);
	private final DomeggookProperties properties = mock(DomeggookProperties.class);
	private final CustomerOrderRepository orderRepository = mock(CustomerOrderRepository.class);
	private final OrderItemRepository itemRepository = mock(OrderItemRepository.class);
	private final FulfillmentRepository fulfillmentRepository = mock(FulfillmentRepository.class);
	private final SupplierPurchaseAttemptRepository attemptRepository = mock(SupplierPurchaseAttemptRepository.class);
	private final ShipmentRepository shipmentRepository = mock(ShipmentRepository.class);
	private final ShipmentItemRepository shipmentItemRepository = mock(ShipmentItemRepository.class);
	private final AdminOrderActionHistoryRepository actionRepository = mock(AdminOrderActionHistoryRepository.class);
	private final OrderStatusHistoryRepository statusRepository = mock(OrderStatusHistoryRepository.class);
	private final CustomerOrder order = mock(CustomerOrder.class);
	private final Fulfillment fulfillment = mock(Fulfillment.class);
	private final SupplierPurchaseAttempt attempt = mock(SupplierPurchaseAttempt.class);
	private final UUID orderId = UUID.randomUUID();
	private final UUID fulfillmentId = UUID.randomUUID();
	private final UUID attemptId = UUID.randomUUID();
	private DomeggookPurchaseService service;

	@BeforeEach
	void setUp() {
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		service = new DomeggookPurchaseService(
			properties,
			client,
			orderRepository,
			itemRepository,
			fulfillmentRepository,
			attemptRepository,
			shipmentRepository,
			shipmentItemRepository,
			actionRepository,
			statusRepository,
			mock(NotificationService.class),
			new TransactionTemplate(transactionManager)
		);

		UserAccount user = mock(UserAccount.class);
		OrderItem item = mock(OrderItem.class);
		when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
		when(fulfillmentRepository.findByOrder_Id(orderId)).thenReturn(Optional.of(fulfillment));
		when(fulfillmentRepository.findByIdForUpdate(fulfillmentId)).thenReturn(Optional.of(fulfillment));
		when(fulfillment.getId()).thenReturn(fulfillmentId);
		when(fulfillment.getOrder()).thenReturn(order);
		when(fulfillment.getPurchaseStatus()).thenReturn(SupplierPurchaseStatus.RECONCILIATION_REQUIRED);
		when(fulfillment.getExpectedSourceAmount()).thenReturn(3450L);
		when(fulfillment.getActualSourceAmount()).thenReturn(null);
		when(fulfillment.getRequestFingerprint()).thenReturn("fingerprint");
		when(order.getId()).thenReturn(orderId);
		when(order.getOrderNumber()).thenReturn("OD-TEST");
		when(order.getRecipientName()).thenReturn("Receiver");
		when(order.getRecipientPhone()).thenReturn("010-1111-2222");
		when(order.getPostalCode()).thenReturn("12345");
		when(order.getAddress1()).thenReturn("Seoul");
		when(order.getAddress2()).thenReturn("101");
		when(order.getUser()).thenReturn(user);
		when(user.getEmail()).thenReturn("customer@example.com");
		when(item.getSourceItemNo()).thenReturn("63511465");
		when(item.getSourceOptionCode()).thenReturn("01");
		when(item.getSourceUnitPrice()).thenReturn(450L);
		when(item.getQuantity()).thenReturn(1);
		when(itemRepository.findAllByOrder_IdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(item));
		when(attemptRepository.save(any(SupplierPurchaseAttempt.class))).thenReturn(attempt);
		when(attempt.getId()).thenReturn(attemptId);
		when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
		when(client.recentOrders()).thenReturn(List.of(
			new DomeggookPurchaseClient.PurchaseListItem("12345", "63511465", "결제완료")
		));
	}

	@Test
	void reconcilesOnlyAnOrderWithTheExactCoreableOrderMemo() {
		when(client.orderView("12345")).thenReturn(new DomeggookPurchaseClient.OrderView(
			"12345", "결제완료", 3450, "", "", "OD-TEST", "63511465"
		));

		service.reconcile(orderId);

		verify(order).markSupplierOrdered();
		verify(fulfillment).markPurchaseOrdered(eq("12345"), eq(3450L), any(), any(), any());
		verify(attempt).succeed(eq("12345"), eq(3450L), any());
	}

	@Test
	void allowsRetryOnlyAfterNoMatchingOrderMemoIsConfirmed() {
		when(client.orderView("12345")).thenReturn(new DomeggookPurchaseClient.OrderView(
			"12345", "결제완료", 3450, "", "", "ANOTHER-ORDER", "63511465"
		));

		service.reconcile(orderId);

		verify(fulfillment).markPurchaseFailed("No matching supplier order was found; retry is allowed");
		verify(attempt).fail(eq("ORDER_NOT_FOUND_AFTER_RECONCILIATION"), any(), any());
	}

	@Test
	void rejectsPortalShipmentDuringLegacyDomeggookTrackingSync() {
		Shipment portalShipment = mock(Shipment.class);
		when(properties.enabled()).thenReturn(true);
		when(fulfillment.getPurchaseStatus()).thenReturn(SupplierPurchaseStatus.ORDERED);
		when(fulfillment.getSupplierOrderNumber()).thenReturn("12345");
		when(fulfillmentRepository.findById(fulfillmentId)).thenReturn(Optional.of(fulfillment));
		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
		when(fulfillmentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(fulfillment));
		when(client.orderView("12345")).thenReturn(new DomeggookPurchaseClient.OrderView(
			"12345", "배송중", 3450, "CJ_LOGISTICS", "1234567890", "OD-TEST", "63511465"
		));
		when(shipmentRepository.findAllByOrderIdForUpdate(orderId)).thenReturn(List.of(portalShipment));
		when(portalShipment.isPortal()).thenReturn(true);

		assertThatThrownBy(() -> service.sync(fulfillmentId))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Portal shipments cannot be synchronized through Domeggook tracking");
	}
}
