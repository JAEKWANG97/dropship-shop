package com.dropshipshop.api.account;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.regex.Pattern;

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
class AccountReferralApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Test
	void createsReferralCodeLazilyAndKeepsItStable() throws Exception {
		UserAccount customer = createCustomer("referral-lazy");

		MvcResult firstResult = mockMvc.perform(get("/api/me/referral")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myReferralCode", matchesPattern("[2-9A-HJ-NP-Z]{8}")))
			.andExpect(jsonPath("$.myReferralCode", not(containsString("0"))))
			.andExpect(jsonPath("$.myReferralCode", not(containsString("O"))))
			.andExpect(jsonPath("$.myReferralCode", not(containsString("1"))))
			.andExpect(jsonPath("$.myReferralCode", not(containsString("I"))))
			.andExpect(jsonPath("$.referrerRegistered", is(false)))
			.andReturn();
		String code = read(firstResult, "myReferralCode");

		mockMvc.perform(get("/api/me/referral")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.myReferralCode", is(code)));
	}

	@Test
	void registersReferrerAndShowsItToAdmin() throws Exception {
		UserAccount referrer = createCustomer("referral-owner");
		UserAccount referred = createCustomer("referral-referred");
		String code = referralCodeFor(referrer);

		mockMvc.perform(post("/api/me/referral")
				.with(authentication(TestAuthentication.customer(referred.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "code": "%s" }
					""".formatted(code.toLowerCase())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.referrerRegistered", is(true)));

		mockMvc.perform(get("/api/me/referral")
				.with(authentication(TestAuthentication.customer(referred.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.referrerRegistered", is(true)));

		mockMvc.perform(get("/api/admin/referrals")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.referrals[0].referrerDisplayName", is(referrer.getDisplayName())))
			.andExpect(jsonPath("$.referrals[0].referralCode", is(code)))
			.andExpect(jsonPath("$.referrals[0].referredDisplayName", is(referred.getDisplayName())))
			.andExpect(jsonPath("$.referrals[0].referredAt").exists());
	}

	@Test
	void rejectsSelfUnknownDuplicateAndInactiveReferrer() throws Exception {
		UserAccount user = createCustomer("referral-user");
		String selfCode = referralCodeFor(user);

		mockMvc.perform(post("/api/me/referral")
				.with(authentication(TestAuthentication.customer(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "code": "%s" }
					""".formatted(selfCode)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", is("Self referral is not allowed")));

		mockMvc.perform(post("/api/me/referral")
				.with(authentication(TestAuthentication.customer(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "code": "UNKNOWN2" }
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", is("Referral code is not found")));

		UserAccount referrer = createCustomer("referral-owner-duplicate");
		String referrerCode = referralCodeFor(referrer);
		mockMvc.perform(post("/api/me/referral")
				.with(authentication(TestAuthentication.customer(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "code": "%s" }
					""".formatted(referrerCode)))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/me/referral")
				.with(authentication(TestAuthentication.customer(user.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "code": "%s" }
					""".formatted(referrerCode)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message", is("Referrer is already registered")));

		UserAccount inactive = createCustomer("referral-inactive");
		String inactiveCode = referralCodeFor(inactive);
		inactive = userAccountRepository.findById(inactive.getId()).orElseThrow();
		inactive.deleteAndAnonymize(Instant.now());
		userAccountRepository.saveAndFlush(inactive);

		UserAccount other = createCustomer("referral-inactive-target");
		mockMvc.perform(post("/api/me/referral")
				.with(authentication(TestAuthentication.customer(other.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{ "code": "%s" }
					""".formatted(inactiveCode)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", is("Referral code is not available")));
	}

	private String referralCodeFor(UserAccount user) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/me/referral")
				.with(authentication(TestAuthentication.customer(user.getId()))))
			.andExpect(status().isOk())
			.andReturn();
		return read(result, "myReferralCode");
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

	private String read(MvcResult result, String field) throws Exception {
		String body = result.getResponse().getContentAsString();
		java.util.regex.Matcher matcher = Pattern
			.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]+)\"")
			.matcher(body);
		if (!matcher.find()) {
			throw new IllegalStateException("Field not found in response: " + field);
		}
		return matcher.group(1);
	}
}
