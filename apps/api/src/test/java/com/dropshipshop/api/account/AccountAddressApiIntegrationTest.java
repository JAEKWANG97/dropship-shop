package com.dropshipshop.api.account;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountAddressApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Test
	void rejectsAnonymousAndAdminAddressAccess() throws Exception {
		mockMvc.perform(get("/api/me/addresses"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/me/addresses")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isForbidden());
	}

	@Test
	void managesCustomerAddressesAndDefaultAddress() throws Exception {
		UserAccount customer = createCustomer("address-customer-1");

		mockMvc.perform(get("/api/me/addresses")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.addresses", hasSize(0)));

		MvcResult firstResult = mockMvc.perform(post("/api/me/addresses")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(addressRequest("Receiver A", "010-1111-1111", "12345", "Address A", "101", false)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.defaultAddress", is(true)))
			.andReturn();
		String firstAddressId = fieldFrom(firstResult, "id");

		MvcResult secondResult = mockMvc.perform(post("/api/me/addresses")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(addressRequest("Receiver B", "010-2222-2222", "23456", "Address B", "202", true)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.defaultAddress", is(true)))
			.andReturn();
		String secondAddressId = fieldFrom(secondResult, "id");

		mockMvc.perform(get("/api/me/addresses")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.addresses", hasSize(2)))
			.andExpect(jsonPath("$.addresses[0].id", is(secondAddressId)))
			.andExpect(jsonPath("$.addresses[0].defaultAddress", is(true)))
			.andExpect(jsonPath("$.addresses[1].id", is(firstAddressId)))
			.andExpect(jsonPath("$.addresses[1].defaultAddress", is(false)));

		mockMvc.perform(patch("/api/me/addresses/{addressId}", firstAddressId)
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(addressRequest("Receiver A Updated", "010-3333-3333", "34567", "Address A Updated", "303", true)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.defaultAddress", is(true)))
			.andExpect(jsonPath("$.recipientName", is("Receiver A Updated")));

		mockMvc.perform(get("/api/me/addresses")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.addresses[0].id", is(firstAddressId)))
			.andExpect(jsonPath("$.addresses[0].defaultAddress", is(true)))
			.andExpect(jsonPath("$.addresses[1].id", is(secondAddressId)))
			.andExpect(jsonPath("$.addresses[1].defaultAddress", is(false)));

		mockMvc.perform(delete("/api/me/addresses/{addressId}", firstAddressId)
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/me/addresses")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.addresses", hasSize(1)))
			.andExpect(jsonPath("$.addresses[0].id", is(secondAddressId)))
			.andExpect(jsonPath("$.addresses[0].defaultAddress", is(true)));
	}

	@Test
	void protectsAddressOwnershipAndValidatesAddressRequests() throws Exception {
		UserAccount owner = createCustomer("address-customer-2");
		UserAccount other = createCustomer("address-customer-3");

		String addressId = fieldFrom(mockMvc.perform(post("/api/me/addresses")
				.with(authentication(TestAuthentication.customer(owner.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(addressRequest("Owner", "010-1111-1111", "12345", "Owner Address", null, true)))
			.andExpect(status().isCreated())
			.andReturn(), "id");

		mockMvc.perform(patch("/api/me/addresses/{addressId}", addressId)
				.with(authentication(TestAuthentication.customer(other.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(addressRequest("Other", "010-2222-2222", "23456", "Other Address", null, true)))
			.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/me/addresses/{addressId}", addressId)
				.with(authentication(TestAuthentication.customer(other.getId()))))
			.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/me/addresses")
				.with(authentication(TestAuthentication.customer(owner.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(addressRequest("", "010-1111-1111", "12345", "Owner Address", null, false)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
	}

	private UserAccount createCustomer(String providerUserId) {
		return userAccountRepository.save(new UserAccount(
			SocialProvider.GOOGLE,
			providerUserId,
			providerUserId + "@example.com",
			providerUserId,
			UserRole.CUSTOMER
		));
	}

	private String addressRequest(
		String recipientName,
		String recipientPhone,
		String postalCode,
		String address1,
		String address2,
		boolean defaultAddress
	) {
		return """
			{
			  "recipientName": "%s",
			  "recipientPhone": "%s",
			  "postalCode": "%s",
			  "address1": "%s",
			  "address2": %s,
			  "defaultAddress": %s
			}
			""".formatted(
			recipientName,
			recipientPhone,
			postalCode,
			address1,
			address2 == null ? "null" : "\"" + address2 + "\"",
			defaultAddress
		);
	}

	private String fieldFrom(MvcResult result, String fieldName) throws Exception {
		String json = result.getResponse().getContentAsString();
		String key = "\"" + fieldName + "\":\"";
		int keyIndex = json.indexOf(key);
		int start = keyIndex + key.length();
		int end = json.indexOf('"', start);
		return json.substring(start, end);
	}
}
