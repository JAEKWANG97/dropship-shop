package com.dropshipshop.api.supplierfulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.fulfillment.SupplierFulfillmentHandoverService;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.supplierportal.repository.FulfillmentHandoverHistoryRepository;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

class SupplierOrderServiceLockingTest {

	@Test
	void locksSupplierThenOrderThenFulfillmentBeforeReturningFullPii() {
		SupplierRepository supplierRepository = mock(SupplierRepository.class);
		UserAccountRepository userRepository = mock(UserAccountRepository.class);
		FulfillmentRepository fulfillmentRepository = mock(FulfillmentRepository.class);
		CustomerOrderRepository orderRepository = mock(CustomerOrderRepository.class);
		OrderItemRepository itemRepository = mock(OrderItemRepository.class);
		FulfillmentHandoverHistoryRepository historyRepository = mock(FulfillmentHandoverHistoryRepository.class);
		SupplierPiiAccessGrantRepository grantRepository = mock(SupplierPiiAccessGrantRepository.class);
		SupplierPiiAccessLogRepository logRepository = mock(SupplierPiiAccessLogRepository.class);
		SupplierFulfillmentHandoverService handoverService = mock(SupplierFulfillmentHandoverService.class);
		UUID actorId = UUID.randomUUID();
		UUID supplierId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		Supplier supplier = mock(Supplier.class);
		UserAccount actor = mock(UserAccount.class);
		CustomerOrder order = mock(CustomerOrder.class);
		Fulfillment fulfillment = mock(Fulfillment.class);
		when(supplier.getId()).thenReturn(supplierId);
		when(supplier.getPortalStatus()).thenReturn(SupplierPortalStatus.ACTIVE);
		when(supplier.getManagerUserId()).thenReturn(actorId);
		when(supplier.hasTimeValidContract(org.mockito.ArgumentMatchers.any())).thenReturn(true);
		when(supplierRepository.findByManagerUserIdForUpdate(actorId)).thenReturn(Optional.of(supplier));
		when(userRepository.findByIdAndStatus(actorId, UserStatus.ACTIVE)).thenReturn(Optional.of(actor));
		when(fulfillmentRepository.findSupplierDetailOrderId(supplierId, "ORDER-1")).thenReturn(Optional.of(orderId));
		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
		when(fulfillmentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(fulfillment));
		when(order.getId()).thenReturn(orderId);
		when(order.getOrderNumber()).thenReturn("ORDER-1");
		when(order.getSupplier()).thenReturn(supplier);
		when(order.getStatus()).thenReturn(OrderStatus.SUPPLIER_ORDER_PENDING);
		when(order.getRecipientName()).thenReturn("Receiver");
		when(order.getRecipientPhone()).thenReturn("010-1111-2222");
		when(fulfillment.getId()).thenReturn(UUID.randomUUID());
		when(fulfillment.getSupplier()).thenReturn(supplier);
		when(fulfillment.getOrder()).thenReturn(order);
		when(fulfillment.getChannel()).thenReturn(FulfillmentChannel.SUPPLIER_PORTAL);
		when(fulfillment.getOperationalOwner()).thenReturn(FulfillmentOperationalOwner.SUPPLIER);
		when(fulfillment.getPiiAccessCutoffAt()).thenReturn(Instant.now().plusSeconds(3600));
		when(itemRepository.findAllByOrder_IdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());
		SupplierOrderService service = new SupplierOrderService(
			supplierRepository, userRepository, fulfillmentRepository, orderRepository, itemRepository,
			historyRepository, grantRepository, logRepository, handoverService
		);

		SupplierOrderDtos.OrderDetailResponse response = service.detail(actorId, "ORDER-1");

		assertThat(response.piiAccessLevel()).isEqualTo("FULL");
		InOrder locks = inOrder(supplierRepository, fulfillmentRepository, orderRepository);
		locks.verify(supplierRepository).findByManagerUserIdForUpdate(actorId);
		locks.verify(fulfillmentRepository).findSupplierDetailOrderId(supplierId, "ORDER-1");
		locks.verify(orderRepository).findByIdForUpdate(orderId);
		locks.verify(fulfillmentRepository).findByOrderIdForUpdate(orderId);
	}
}
