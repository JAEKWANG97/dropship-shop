package com.dropshipshop.api.payment;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.security.TestAuthentication;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankTransferOnlyPaymentApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void doesNotExposeTossOrPgCancellationEndpoints() throws Exception {
		mockMvc.perform(post("/api/payments/toss/webhook")
				.with(authentication(TestAuthentication.customer())))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/payments/toss/confirm")
				.with(authentication(TestAuthentication.customer(TestAuthentication.CUSTOMER_ID))))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/admin/payment-exceptions")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/admin/refunds/{refundId}/request-pg-cancel", TestAuthentication.CUSTOMER_ID)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isNotFound());
	}
}
