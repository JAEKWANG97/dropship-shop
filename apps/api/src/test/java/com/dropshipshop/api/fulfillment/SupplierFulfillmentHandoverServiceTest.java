package com.dropshipshop.api.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.supplierportal.repository.FulfillmentHandoverHistoryRepository;

class SupplierFulfillmentHandoverServiceTest {

	@Test
	void terminalTakeoverDoesNotLockLegacyFulfillment() {
		FulfillmentRepository fulfillmentRepository = mock(FulfillmentRepository.class);
		CustomerOrder order = mock(CustomerOrder.class);
		UUID orderId = UUID.randomUUID();
		when(order.getId()).thenReturn(orderId);
		when(fulfillmentRepository.findChannelByOrderId(orderId))
			.thenReturn(Optional.of(FulfillmentChannel.DOMEGGOOK_API));
		SupplierFulfillmentHandoverService service = new SupplierFulfillmentHandoverService(
			fulfillmentRepository, mock(FulfillmentHandoverHistoryRepository.class)
		);

		assertThat(service.takeOverTerminal(order, Instant.now())).isFalse();

		verify(fulfillmentRepository).findChannelByOrderId(orderId);
		verify(fulfillmentRepository, never()).findByOrderIdForUpdate(orderId);
	}
}
