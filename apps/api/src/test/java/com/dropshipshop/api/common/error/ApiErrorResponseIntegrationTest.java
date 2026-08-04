package com.dropshipshop.api.common.error;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.account.domain.UserPolicyAgreement;
import com.dropshipshop.api.account.repository.UserPolicyAgreementRepository;
import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApiErrorResponseIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private UserPolicyAgreementRepository userPolicyAgreementRepository;

	@Test
	void returnsStandardAuthenticationAndAuthorizationErrors() throws Exception {
		mockMvc.perform(get("/api/cart"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status", is(401)))
			.andExpect(jsonPath("$.code", is("UNAUTHORIZED")))
			.andExpect(jsonPath("$.message", is("Authentication is required")))
			.andExpect(jsonPath("$.path", is("/api/cart")))
			.andExpect(jsonPath("$.fields", hasSize(0)));

		mockMvc.perform(get("/api/admin/me")
				.with(authentication(TestAuthentication.customer())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status", is(403)))
			.andExpect(jsonPath("$.code", is("FORBIDDEN")))
			.andExpect(jsonPath("$.message", is("Access is denied")))
			.andExpect(jsonPath("$.path", is("/api/admin/me")))
			.andExpect(jsonPath("$.fields", hasSize(0)));
	}

	@Test
	void returnsStandardValidationAndMalformedRequestErrors() throws Exception {
		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "quantity": 0
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status", is(400)))
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")))
			.andExpect(jsonPath("$.message", is("Request validation failed")))
			.andExpect(jsonPath("$.path", is("/api/cart/items")))
			.andExpect(jsonPath("$.fields", hasSize(2)))
			.andExpect(jsonPath("$.fields[?(@.field == 'productOptionId')]", hasSize(1)))
			.andExpect(jsonPath("$.fields[?(@.field == 'quantity')]", hasSize(1)));

		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status", is(400)))
			.andExpect(jsonPath("$.code", is("MALFORMED_REQUEST")))
			.andExpect(jsonPath("$.message", is("Malformed request body")))
			.andExpect(jsonPath("$.path", is("/api/cart/items")))
			.andExpect(jsonPath("$.fields", hasSize(0)));
	}

	@Test
	void returnsStandardNotFoundAndBusinessRuleErrors() throws Exception {
		mockMvc.perform(get("/api/policies/not-found"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status", is(404)))
			.andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")))
			.andExpect(jsonPath("$.message", is("Policy not found")))
			.andExpect(jsonPath("$.path", is("/api/policies/not-found")));

		UserAccount customer = userAccountRepository.save(new UserAccount(
			SocialProvider.GOOGLE,
			"api-error-customer",
			"api-error-customer@example.com",
			"Api Error Customer",
			UserRole.CUSTOMER
		));
		customer.verifyPhone("01011112222", Instant.now());
		userAccountRepository.save(customer);
		userPolicyAgreementRepository.save(new UserPolicyAgreement(
			customer,
			"2026-08-02",
			"2026-08-04",
			Instant.now()
		));

		mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "recipientName": "Receiver",
					  "recipientPhone": "010-1111-2222",
					  "postalCode": "12345",
					  "address1": "Seoul test road",
					  "address2": "101"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status", is(400)))
			.andExpect(jsonPath("$.code", is("BUSINESS_RULE_VIOLATION")))
			.andExpect(jsonPath("$.message", is("Cart is empty")))
			.andExpect(jsonPath("$.path", is("/api/checkouts")))
			.andExpect(jsonPath("$.fields", hasSize(0)));
	}
}
