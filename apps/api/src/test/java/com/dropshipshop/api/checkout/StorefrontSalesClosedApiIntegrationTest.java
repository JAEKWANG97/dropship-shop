package com.dropshipshop.api.checkout;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.security.TestAuthentication;

@SpringBootTest(properties = "app.sales.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorefrontSalesClosedApiIntegrationTest {

	private static final String SALES_NOTICE = "판매 준비 중입니다.";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void blocksCartAddAndCheckoutCreation() throws Exception {
		UUID customerId = UUID.randomUUID();

		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer(customerId)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "productOptionId": "%s",
					  "quantity": 1
					}
					""".formatted(UUID.randomUUID())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message", is(SALES_NOTICE)));

		mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(customerId)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "recipientName": "구매자",
					  "recipientPhone": "01012345678",
					  "postalCode": "05555",
					  "address1": "서울특별시",
					  "address2": "",
					  "depositorName": "구매자",
					  "clientSubmittedTotalAmount": 10000
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message", is(SALES_NOTICE)));
	}
}
