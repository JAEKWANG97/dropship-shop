package com.dropshipshop.api.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@Test
	void mapsOptimisticLockingFailureToConflictResponse() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders/order-id/cancel");

		ResponseEntity<ApiErrorResponse> response = handler.handleOptimisticLockingFailure(
			new OptimisticLockingFailureException("stale order"),
			request
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().status()).isEqualTo(409);
		assertThat(response.getBody().code()).isEqualTo("CONFLICT");
		assertThat(response.getBody().message()).isEqualTo("Order state was just changed. Please refresh and try again.");
		assertThat(response.getBody().path()).isEqualTo("/api/orders/order-id/cancel");
	}
}
