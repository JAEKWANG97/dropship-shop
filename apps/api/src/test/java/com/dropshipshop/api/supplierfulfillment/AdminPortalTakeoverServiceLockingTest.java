package com.dropshipshop.api.supplierfulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.SupplierPortalInputPolicy;
import com.dropshipshop.api.supplierportal.repository.FulfillmentHandoverHistoryRepository;

import tools.jackson.databind.ObjectMapper;

class AdminPortalTakeoverServiceLockingTest {

	@Test
	void discoversScalarIdBeforeLoadingFulfillmentOnlyWithWriteLock() throws Exception {
		FulfillmentRepository fulfillmentRepository = mock(FulfillmentRepository.class);
		FulfillmentHandoverHistoryRepository historyRepository = mock(FulfillmentHandoverHistoryRepository.class);
		SupplierPortalHasher hasher = mock(SupplierPortalHasher.class);
		ObjectMapper objectMapper = mock(ObjectMapper.class);
		UUID orderId = UUID.randomUUID();
		UUID fulfillmentId = UUID.randomUUID();
		UUID adminId = UUID.randomUUID();
		Fulfillment fulfillment = mock(Fulfillment.class);
		when(fulfillmentRepository.findIdByOrderId(orderId)).thenReturn(Optional.of(fulfillmentId));
		when(fulfillmentRepository.findByIdForUpdate(fulfillmentId)).thenReturn(Optional.of(fulfillment));
		when(fulfillment.getId()).thenReturn(fulfillmentId);
		when(fulfillment.getOperationalOwner()).thenReturn(FulfillmentOperationalOwner.COREABLE);
		when(fulfillment.handOverToCoreable(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		)).thenReturn(true);
		when(hasher.hmac(org.mockito.ArgumentMatchers.any())).thenReturn("request-hash");
		when(objectMapper.writeValueAsString(org.mockito.ArgumentMatchers.any())).thenReturn("{}");
		AdminPortalTakeoverService service = new AdminPortalTakeoverService(
			fulfillmentRepository,
			historyRepository,
			new SupplierPortalInputPolicy(),
			hasher,
			objectMapper
		);

		var response = service.takeOver(
			orderId,
			adminId,
			"takeover-lock-order-1",
			new AdminFulfillmentPrivacyDtos.PortalTakeoverRequest("COREABLE_FULFILLMENT_TAKEOVER")
		);

		assertThat(response.fulfillmentId()).isEqualTo(fulfillmentId);
		InOrder locking = inOrder(fulfillmentRepository, historyRepository);
		locking.verify(fulfillmentRepository).findIdByOrderId(orderId);
		locking.verify(historyRepository).findByFulfillment_IdAndIdempotencyKey(
			fulfillmentId, "takeover-lock-order-1");
		locking.verify(fulfillmentRepository).findByIdForUpdate(fulfillmentId);
		verify(fulfillmentRepository, never()).findByOrder_Id(orderId);
	}
}
