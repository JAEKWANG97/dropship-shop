package com.dropshipshop.api.supplierfulfillment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.SupplierPortalInputPolicy;
import com.dropshipshop.api.supplierportal.repository.FulfillmentHandoverHistoryRepository;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import tools.jackson.databind.ObjectMapper;

class AdminSupplierPiiGrantServiceLockingTest {

	@Test
	void wrongChannelGrantDoesNotLockLegacyFulfillment() {
		ClaimRepository claimRepository = mock(ClaimRepository.class);
		CustomerOrderRepository orderRepository = mock(CustomerOrderRepository.class);
		FulfillmentRepository fulfillmentRepository = mock(FulfillmentRepository.class);
		SupplierPiiAccessGrantRepository grantRepository = mock(SupplierPiiAccessGrantRepository.class);
		UUID claimId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		UUID adminId = UUID.randomUUID();
		CustomerOrder order = mock(CustomerOrder.class);
		Claim claim = mock(Claim.class);
		Supplier supplier = mock(Supplier.class);
		when(claimRepository.findOrderIdById(claimId)).thenReturn(Optional.of(orderId));
		when(grantRepository.findByClaim_IdAndIdempotencyKey(claimId, "wrong-channel-grant-1"))
			.thenReturn(Optional.empty());
		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
		when(claimRepository.findByIdForUpdate(claimId)).thenReturn(Optional.of(claim));
		when(claim.getOrder()).thenReturn(order);
		when(order.getId()).thenReturn(orderId);
		when(claim.getStatus()).thenReturn(ClaimStatus.APPROVED);
		when(order.getSupplier()).thenReturn(supplier);
		when(supplier.hasTimeValidContract(org.mockito.ArgumentMatchers.any())).thenReturn(true);
		when(fulfillmentRepository.findChannelByOrderId(orderId))
			.thenReturn(Optional.of(FulfillmentChannel.DOMEGGOOK_API));
		AdminSupplierPiiGrantService service = new AdminSupplierPiiGrantService(
			claimRepository,
			orderRepository,
			fulfillmentRepository,
			mock(FulfillmentHandoverHistoryRepository.class),
			grantRepository,
			mock(UserAccountRepository.class),
			new SupplierPortalInputPolicy(),
			mock(SupplierPortalHasher.class),
			mock(ObjectMapper.class)
		);

		assertThatThrownBy(() -> service.grant(
			claimId,
			adminId,
			"wrong-channel-grant-1",
			new AdminFulfillmentPrivacyDtos.GrantRequest(
				SupplierPiiGrantAction.GRANTED,
				null,
				Instant.now().plusSeconds(3600),
				"RETURN_COORDINATION_REQUIRED"
			)
		)).isInstanceOf(ApiErrorException.class);

		verify(fulfillmentRepository).findChannelByOrderId(orderId);
		verify(fulfillmentRepository, never()).findByOrderIdForUpdate(orderId);
		verify(grantRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}
}
