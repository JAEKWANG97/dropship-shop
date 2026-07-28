package com.dropshipshop.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OAuthLoginApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FakeOAuthProviderClient fakeOAuthProviderClient;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@BeforeEach
	void resetFakeClient() {
		fakeOAuthProviderClient.reset();
	}

	@Test
	void redirectsToProviderAuthorizeUrlWithStateCookie() throws Exception {
		mockMvc.perform(get("/api/auth/oauth2/google/authorize"))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("https://accounts.google.com/o/oauth2/v2/auth")))
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("client_id=test-google-client")))
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("response_type=code")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("OAUTH2_STATE=")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
	}

	@Test
	void kakaoAuthorizeRequestsNicknameAndEmailByDefault() throws Exception {
		mockMvc.perform(get("/api/auth/oauth2/kakao/authorize"))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("https://kauth.kakao.com/oauth/authorize")))
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("profile_nickname")))
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("account_email")));
	}

	@Test
	void naverAuthorizeRedirectsToProvider() throws Exception {
		mockMvc.perform(get("/api/auth/oauth2/naver/authorize"))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("https://nid.naver.com/oauth2.0/authorize")))
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("client_id=test-naver-client")));
	}

	@Test
	void createsCustomerAndAuthenticatesWithJwtCookieAfterCallback() throws Exception {
		fakeOAuthProviderClient.profile(
			SocialProvider.GOOGLE,
			"new-customer-code",
			new OAuthProfile("google-user-1", "customer@example.com", "Customer")
		);

		Cookie stateCookie = stateCookie();

		MvcResult callbackResult = mockMvc.perform(get("/api/auth/oauth2/google/callback")
				.param("code", "new-customer-code")
				.param("state", stateCookie.getValue())
				.cookie(stateCookie))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, is("http://localhost:3000/auth/callback/success?onboarding=1")))
			.andExpect(cookie().exists("ACCESS_TOKEN"))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("ACCESS_TOKEN=")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
			.andReturn();

		UserAccount saved = userAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-user-1")
			.orElseThrow();
		assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
		assertThat(saved.getEmail()).isEqualTo("customer@example.com");

		mockMvc.perform(get("/api/me")
				.cookie(callbackResult.getResponse().getCookie("ACCESS_TOKEN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId", is(saved.getId().toString())));

		mockMvc.perform(get("/api/admin/me")
				.cookie(callbackResult.getResponse().getCookie("ACCESS_TOKEN")))
			.andExpect(status().isForbidden());
	}

	@Test
	void redirectsToRequestedInternalPathAfterCallback() throws Exception {
		fakeOAuthProviderClient.profile(
			SocialProvider.GOOGLE,
			"redirect-code",
			new OAuthProfile("google-redirect-user", "redirect@example.com", "Redirect")
		);

		MvcResult authorizeResult = mockMvc.perform(get("/api/auth/oauth2/google/authorize")
				.param("redirectTo", "/products/product-1?from=detail"))
			.andExpect(status().isFound())
			.andExpect(cookie().exists("OAUTH2_STATE"))
			.andExpect(cookie().exists("OAUTH2_REDIRECT_TO"))
			.andReturn();

		Cookie stateCookie = authorizeResult.getResponse().getCookie("OAUTH2_STATE");
		Cookie redirectToCookie = authorizeResult.getResponse().getCookie("OAUTH2_REDIRECT_TO");

		mockMvc.perform(get("/api/auth/oauth2/google/callback")
				.param("code", "redirect-code")
				.param("state", stateCookie.getValue())
				.cookie(stateCookie, redirectToCookie))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("http://localhost:3000/auth/callback/success")))
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("onboarding=1")))
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("redirectTo=")))
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("products")))
			.andExpect(cookie().maxAge("OAUTH2_REDIRECT_TO", 0));
	}

	@Test
	void rejectsBackslashAndControlCharactersInRedirectTo() throws Exception {
		mockMvc.perform(get("/api/auth/oauth2/google/authorize")
				.param("redirectTo", "/\\evil.com"))
			.andExpect(status().isFound())
			.andExpect(cookie().maxAge("OAUTH2_REDIRECT_TO", 0));

		mockMvc.perform(get("/api/auth/oauth2/google/authorize?redirectTo=/%5Cevil.com"))
			.andExpect(status().isFound())
			.andExpect(cookie().maxAge("OAUTH2_REDIRECT_TO", 0));

		mockMvc.perform(get("/api/auth/oauth2/google/authorize")
				.param("redirectTo", "/products\r\nLocation: https://evil.com"))
			.andExpect(status().isFound())
			.andExpect(cookie().maxAge("OAUTH2_REDIRECT_TO", 0));

		mockMvc.perform(get("/api/auth/oauth2/google/authorize")
				.param("redirectTo", "/products/123"))
			.andExpect(status().isFound())
			.andExpect(cookie().exists("OAUTH2_REDIRECT_TO"));
	}

	@Test
	void preservesAdminRoleFromDatabaseAfterSocialLogin() throws Exception {
		UserAccount admin = userAccountRepository.save(new UserAccount(
			SocialProvider.NAVER,
			"naver-admin-1",
			"admin@example.com",
			"Admin",
			UserRole.ADMIN
		));
		fakeOAuthProviderClient.profile(
			SocialProvider.NAVER,
			"admin-code",
			new OAuthProfile("naver-admin-1", "admin@example.com", "Admin")
		);

		Cookie stateCookie = stateCookie();

		MvcResult callbackResult = mockMvc.perform(get("/api/auth/oauth2/naver/callback")
				.param("code", "admin-code")
				.param("state", stateCookie.getValue())
				.cookie(stateCookie))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, not(containsString("onboarding=1"))))
			.andExpect(cookie().exists("ACCESS_TOKEN"))
			.andReturn();

		mockMvc.perform(get("/api/admin/me")
				.cookie(callbackResult.getResponse().getCookie("ACCESS_TOKEN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId", is(admin.getId().toString())));
	}

	@Test
	void replacesPlaceholderEmailWithProviderEmailOnNextLogin() throws Exception {
		UserAccount customer = userAccountRepository.save(new UserAccount(
			SocialProvider.KAKAO,
			"kakao-email-user",
			"kakao-kakao-email-user@oauth.local",
			"Kakao",
			UserRole.CUSTOMER
		));
		fakeOAuthProviderClient.profile(
			SocialProvider.KAKAO,
			"kakao-email-code",
			new OAuthProfile("kakao-email-user", "customer@kakao.com", "Kakao")
		);

		Cookie stateCookie = stateCookie();
		mockMvc.perform(get("/api/auth/oauth2/kakao/callback")
				.param("code", "kakao-email-code")
				.param("state", stateCookie.getValue())
				.cookie(stateCookie))
			.andExpect(status().isFound());

		UserAccount updated = userAccountRepository.findById(customer.getId()).orElseThrow();
		assertThat(updated.getEmail()).isEqualTo("customer@kakao.com");
	}

	@Test
	void rejoiningWithDeletedSocialAccountCreatesNewCustomer() throws Exception {
		UserAccount deleted = userAccountRepository.save(new UserAccount(
			SocialProvider.GOOGLE,
			"deleted-google-user",
			"deleted-before@example.com",
			"Deleted",
			UserRole.CUSTOMER
		));
		deleted.deleteAndAnonymize(Instant.now());
		userAccountRepository.saveAndFlush(deleted);
		fakeOAuthProviderClient.profile(
			SocialProvider.GOOGLE,
			"rejoin-code",
			new OAuthProfile("deleted-google-user", "rejoin@example.com", "Rejoin")
		);

		Cookie stateCookie = stateCookie();

		MvcResult callbackResult = mockMvc.perform(get("/api/auth/oauth2/google/callback")
				.param("code", "rejoin-code")
				.param("state", stateCookie.getValue())
				.cookie(stateCookie))
			.andExpect(status().isFound())
			.andExpect(cookie().exists("ACCESS_TOKEN"))
			.andReturn();

		UserAccount rejoined = userAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "deleted-google-user")
			.orElseThrow();
		assertThat(rejoined.getId()).isNotEqualTo(deleted.getId());
		assertThat(rejoined.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(rejoined.getEmail()).isEqualTo("rejoin@example.com");

		mockMvc.perform(get("/api/me")
				.cookie(callbackResult.getResponse().getCookie("ACCESS_TOKEN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId", is(rejoined.getId().toString())));
	}

	@Test
	void rejectsInvalidCallbackStateAndProviderErrors() throws Exception {
		mockMvc.perform(get("/api/auth/oauth2/kakao/callback")
				.param("code", "code")
				.param("state", "bad-state"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("BUSINESS_RULE_VIOLATION")))
			.andExpect(jsonPath("$.message", is("OAuth state is invalid")));

		Cookie stateCookie = stateCookie();

		mockMvc.perform(get("/api/auth/oauth2/kakao/callback")
				.param("error", "access_denied")
				.param("state", stateCookie.getValue())
				.cookie(stateCookie))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("BUSINESS_RULE_VIOLATION")))
			.andExpect(jsonPath("$.message", is("OAuth provider returned error")));
	}

	@Test
	void logsOutByClearingAccessTokenCookie() throws Exception {
		fakeOAuthProviderClient.profile(
			SocialProvider.KAKAO,
			"logout-code",
			new OAuthProfile("kakao-user-1", "logout@example.com", "Logout")
		);

		Cookie stateCookie = stateCookie();
		MvcResult callbackResult = mockMvc.perform(get("/api/auth/oauth2/kakao/callback")
				.param("code", "logout-code")
				.param("state", stateCookie.getValue())
				.cookie(stateCookie))
			.andExpect(status().isFound())
			.andReturn();

		mockMvc.perform(post("/api/auth/logout")
				.cookie(callbackResult.getResponse().getCookie("ACCESS_TOKEN")))
			.andExpect(status().isNoContent())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("ACCESS_TOKEN=;")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
	}

	private Cookie stateCookie() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/auth/oauth2/google/authorize"))
			.andExpect(status().isFound())
			.andExpect(cookie().exists("OAUTH2_STATE"))
			.andReturn();
		return result.getResponse().getCookie("OAUTH2_STATE");
	}

	@TestConfiguration
	static class FakeOAuthProviderConfiguration {

		@Bean
		@Primary
		FakeOAuthProviderClient fakeOAuthProviderClient() {
			return new FakeOAuthProviderClient();
		}
	}

	static class FakeOAuthProviderClient implements OAuthProviderClient {

		private final Map<SocialProvider, Map<String, OAuthProfile>> profiles = new EnumMap<>(SocialProvider.class);

		@Override
		public OAuthProfile fetchProfile(SocialProvider provider, String code) {
			OAuthProfile profile = profiles.getOrDefault(provider, Map.of()).get(code);
			if (profile == null) {
				throw new OAuthProviderException("No fake OAuth profile");
			}
			return profile;
		}

		void profile(SocialProvider provider, String code, OAuthProfile profile) {
			profiles.computeIfAbsent(provider, ignored -> new java.util.HashMap<>()).put(code, profile);
		}

		void reset() {
			profiles.clear();
		}
	}
}
