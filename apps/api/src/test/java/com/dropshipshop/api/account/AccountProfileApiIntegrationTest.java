package com.dropshipshop.api.account;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountProfileApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private CapturingSmsSender smsSender;

	@Test
	void updatesProfileAndVerifiesPhoneNumber() throws Exception {
		UserAccount customer = createCustomer("profile-customer", "profile-customer@oauth.local");

		mockMvc.perform(get("/api/me/profile-completion")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailRequired", is(true)))
			.andExpect(jsonPath("$.phoneVerified", is(false)))
			.andExpect(jsonPath("$.requiredInfoComplete", is(false)));

		mockMvc.perform(patch("/api/me/profile")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "displayName": "홍길동",
					  "email": "customer@example.com"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailRequired", is(false)))
			.andExpect(jsonPath("$.emailComplete", is(true)));

		mockMvc.perform(post("/api/me/phone-verifications")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "phoneNumber": "010-1234-5678"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.phoneNumber", is("01012345678")))
			.andExpect(jsonPath("$.expiresAt").exists());

		mockMvc.perform(post("/api/me/phone-verifications/confirm")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "phoneNumber": "01012345678",
					  "code": "%s"
					}
					""".formatted(smsSender.codeFor("01012345678"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phoneVerified", is(true)))
			.andExpect(jsonPath("$.phoneNumber", is("01012345678")))
			.andExpect(jsonPath("$.requiredInfoComplete", is(true)));
	}

	@Test
	void rejectsInvalidPhoneCodeAndFastResend() throws Exception {
		UserAccount customer = createCustomer("profile-customer-invalid", "profile-invalid@example.com");

		mockMvc.perform(post("/api/me/phone-verifications")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "phoneNumber": "01012345678"
					}
					"""))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/me/phone-verifications")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "phoneNumber": "01012345678"
					}
					"""))
			.andExpect(status().isTooManyRequests());

		mockMvc.perform(post("/api/me/phone-verifications/confirm")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "phoneNumber": "01012345678",
					  "code": "000000"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", is("Phone verification code does not match")));
	}

	private UserAccount createCustomer(String providerUserId, String email) {
		return userAccountRepository.save(new UserAccount(
			SocialProvider.GOOGLE,
			providerUserId,
			email,
			providerUserId,
			UserRole.CUSTOMER
		));
	}

	@TestConfiguration
	static class SmsTestConfig {

		@Bean
		@Primary
		CapturingSmsSender capturingSmsSender() {
			return new CapturingSmsSender();
		}
	}

	static class CapturingSmsSender implements SmsSender {

		private final Map<String, String> codes = new HashMap<>();

		@Override
		public void sendVerificationCode(String phoneNumber, String code) {
			codes.put(phoneNumber, code);
		}

		String codeFor(String phoneNumber) {
			return codes.get(phoneNumber);
		}
	}
}
