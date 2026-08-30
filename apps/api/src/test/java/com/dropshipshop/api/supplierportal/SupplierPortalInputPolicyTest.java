package com.dropshipshop.api.supplierportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.dropshipshop.api.common.error.ApiErrorException;

class SupplierPortalInputPolicyTest {

	private final SupplierPortalInputPolicy policy = new SupplierPortalInputPolicy();

	@Test
	void acceptsOperationalReasonAndRejectsAddressOrDeliveryMemoIdentifiers() {
		assertThat(policy.requirePiiFreeReason("  Coreable will complete fulfillment  ", 200))
			.isEqualTo("Coreable will complete fulfillment");

		for (String unsafe : List.of(
			"Address: 123 Main Street Apt 4B",
			"123 Main Street Apt 4B",
			"서울특별시 강남구 테헤란로 123",
			"shipping address needs correction",
			"delivery memo says leave at door",
			"배송 메모를 확인해 주세요",
			"고객 주소를 다시 확인해 주세요"
		)) {
			assertThatThrownBy(() -> policy.requirePiiFreeReason(unsafe, 200))
				.isInstanceOf(ApiErrorException.class);
		}
	}

	@Test
	void fulfillmentPrivacyCommandsAcceptOnlyStableReasonCodes() {
		assertThat(policy.requirePortalTakeoverReasonCode(" COREABLE_FULFILLMENT_TAKEOVER "))
			.isEqualTo("COREABLE_FULFILLMENT_TAKEOVER");
		assertThat(policy.requirePiiGrantReasonCode("RETURN_COORDINATION_REQUIRED"))
			.isEqualTo("RETURN_COORDINATION_REQUIRED");
		assertThat(policy.requirePiiRevokeReasonCode("CLAIM_ACCESS_NO_LONGER_REQUIRED"))
			.isEqualTo("CLAIM_ACCESS_NO_LONGER_REQUIRED");

		for (String unsafe : List.of("강남대로 396", "221B Baker Street", "Different safe reason")) {
			assertThatThrownBy(() -> policy.requirePortalTakeoverReasonCode(unsafe))
				.isInstanceOf(ApiErrorException.class);
			assertThatThrownBy(() -> policy.requirePiiGrantReasonCode(unsafe))
				.isInstanceOf(ApiErrorException.class);
		}
		assertThatThrownBy(() -> policy.requirePiiRevokeReasonCode("RETURN_COORDINATION_REQUIRED"))
			.isInstanceOf(ApiErrorException.class);
	}
}
