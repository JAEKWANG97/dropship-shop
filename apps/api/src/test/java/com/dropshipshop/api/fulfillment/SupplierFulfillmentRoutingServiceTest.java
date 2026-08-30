package com.dropshipshop.api.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;
import com.dropshipshop.api.user.repository.UserAccountRepository;

class SupplierFulfillmentRoutingServiceTest {

	@Test
	void featureFlagOffRoutesAllPortalSnapshotWorkToCoreableWithoutLockOrEmail() {
		FulfillmentRepository fulfillmentRepository = mock(FulfillmentRepository.class);
		UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
		NotificationService notificationService = mock(NotificationService.class);
		SupplierPortalFeatureGate featureGate = mock(SupplierPortalFeatureGate.class);
		CustomerOrder order = mock(CustomerOrder.class);
		Supplier supplier = mock(Supplier.class);
		OrderItem item = mock(OrderItem.class);
		UUID orderId = UUID.randomUUID();
		when(order.getId()).thenReturn(orderId);
		when(order.getSupplier()).thenReturn(supplier);
		when(item.getManagementChannelSnapshot()).thenReturn(ProductManagementChannel.SUPPLIER_PORTAL);
		when(fulfillmentRepository.findByOrder_Id(orderId)).thenReturn(Optional.empty());
		when(fulfillmentRepository.save(org.mockito.ArgumentMatchers.any(Fulfillment.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		when(featureGate.isEnabled()).thenReturn(false);
		SupplierFulfillmentRoutingService service = new SupplierFulfillmentRoutingService(
			fulfillmentRepository, userAccountRepository, notificationService, featureGate
		);

		Fulfillment fulfillment = service.routePaidOrder(order, List.of(item), Instant.now());

		assertThat(fulfillment.getChannel()).isEqualTo(FulfillmentChannel.COREABLE_MANUAL);
		assertThat(fulfillment.getOperationalOwner()).isEqualTo(FulfillmentOperationalOwner.COREABLE);
		assertThat(fulfillment.getRequestedAt()).isNull();
		verify(order, never()).lockAddressForSupplierPortal(org.mockito.ArgumentMatchers.any());
		verify(notificationService, never()).supplierFulfillmentRequested(
			org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}
}
