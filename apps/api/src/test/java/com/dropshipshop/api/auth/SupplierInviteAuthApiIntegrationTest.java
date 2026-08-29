package com.dropshipshop.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.domain.SupplierInvite;
import com.dropshipshop.api.supplierportal.domain.SupplierInviteRevocationReasonCode;
import com.dropshipshop.api.supplierportal.repository.SupplierInviteRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:supplier_invite_auth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierInviteAuthApiIntegrationTest {

	private static final String ALLOWED_ORIGIN = "http://localhost:3000";
	private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private SupplierInviteRepository inviteRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private SupplierPortalHasher hasher;

	@Autowired
	private SupplierInviteContextTokenService contextTokenService;

	@Autowired
	private FakeSupplierOAuthProviderClient fakeOAuthProviderClient;

	@BeforeEach
	void resetFakeClient() {
		fakeOAuthProviderClient.reset();
	}

	@Test
	void exchangesRawTokenForDigestBoundHttpOnlyContextWithoutConsumingInvite() throws Exception {
		InviteFixture fixture = openInvite("exchange");

		MvcResult result = exchange(fixture.rawToken())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.next", is("/api/supplier/auth/kakao/authorize")))
			.andExpect(cookie().exists(SupplierInviteAuthService.INVITE_CONTEXT_COOKIE))
			.andExpect(cookie().httpOnly(SupplierInviteAuthService.INVITE_CONTEXT_COOKIE, true))
			.andExpect(cookie().maxAge(SupplierInviteAuthService.INVITE_CONTEXT_COOKIE, 300))
			.andExpect(cookie().maxAge(SupplierInviteAuthService.INVITE_STATE_COOKIE, 0))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
			.andReturn();

		SupplierInvite stored = inviteRepository.findById(fixture.inviteId()).orElseThrow();
		assertThat(stored.getTokenDigest()).isEqualTo(hasher.tokenDigest(fixture.rawToken()));
		assertThat(stored.getTokenDigest()).isNotEqualTo(fixture.rawToken());
		assertThat(stored.getConsumedAt()).isNull();
		assertThat(inviteRepository.findByTokenDigest(fixture.rawToken())).isEmpty();

		String observableResponse = result.getResponse().getContentAsString()
			+ String.join("\n", result.getResponse().getHeaders(HttpHeaders.SET_COOKIE));
		assertThat(observableResponse).doesNotContain(fixture.rawToken());
	}

	@Test
	void rejectsMismatchedStateAndContextWithoutConsumingEitherInvite() throws Exception {
		InviteFixture first = openInvite("state-first");
		InviteFixture second = openInvite("state-second");
		AuthorizationFlow firstFlow = beginAuthorization(first.rawToken());
		AuthorizationFlow secondFlow = beginAuthorization(second.rawToken());

		mockMvc.perform(get("/api/supplier/auth/kakao/callback")
				.param("code", "must-not-be-used")
				.param("state", secondFlow.state())
				.cookie(firstFlow.contextCookie(), secondFlow.stateCookie()))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("error=INVITE_INVALID")))
			.andExpect(cookie().maxAge(SupplierInviteAuthService.INVITE_CONTEXT_COOKIE, 0))
			.andExpect(cookie().maxAge(SupplierInviteAuthService.INVITE_STATE_COOKIE, 0));

		assertThat(inviteRepository.findById(first.inviteId()).orElseThrow().getConsumedAt()).isNull();
		assertThat(inviteRepository.findById(second.inviteId()).orElseThrow().getConsumedAt()).isNull();
		assertThat(supplierRepository.findById(first.supplierId()).orElseThrow().getManagerUserId()).isNull();
		assertThat(supplierRepository.findById(second.supplierId()).orElseThrow().getManagerUserId()).isNull();
	}

	@Test
	void keepsInviteContextRetryableAfterTransientOAuthFailure() throws Exception {
		InviteFixture fixture = openInvite("oauth-retry");
		AuthorizationFlow firstAttempt = beginAuthorization(fixture.rawToken());

		MvcResult failure = mockMvc.perform(get("/api/supplier/auth/kakao/callback")
				.param("code", "temporary-provider-failure")
				.param("state", firstAttempt.state())
				.cookie(firstAttempt.contextCookie(), firstAttempt.stateCookie()))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("error=OAUTH_TEMPORARY_FAILURE")))
			.andExpect(cookie().maxAge(SupplierInviteAuthService.INVITE_STATE_COOKIE, 0))
			.andReturn();

		assertThat(failure.getResponse().getCookie(SupplierInviteAuthService.INVITE_CONTEXT_COOKIE)).isNull();
		assertThat(inviteRepository.findById(fixture.inviteId()).orElseThrow().getConsumedAt()).isNull();

		fakeOAuthProviderClient.profile(
			"oauth-retry-success",
			new OAuthProfile("supplier-oauth-retry", "different-kakao-email@example.com", "Supplier Manager")
		);
		AuthorizationFlow retry = authorize(firstAttempt.contextCookie());
		MvcResult success = callback(retry, "oauth-retry-success")
			.andExpect(status().isFound())
			.andExpect(cookie().exists("ACCESS_TOKEN"))
			.andReturn();

		assertThat(success.getResponse().getCookie("ACCESS_TOKEN")).isNotNull();
		assertThat(inviteRepository.findById(fixture.inviteId()).orElseThrow().getConsumedAt()).isNotNull();
	}

	@Test
	void activatesSupplierConsumesInviteAndIssuesJwtWithDynamicSupplierAuthority() throws Exception {
		InviteFixture fixture = openInvite("activation-success");
		AuthorizationFlow flow = beginAuthorization(fixture.rawToken());
		fakeOAuthProviderClient.profile(
			"activation-code",
			new OAuthProfile("supplier-kakao-user", "kakao-email-does-not-need-to-match@example.com", "Supplier Manager")
		);

		MvcResult callback = callback(flow, "activation-code")
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, is("http://localhost:3000/supplier")))
			.andExpect(cookie().exists("ACCESS_TOKEN"))
			.andExpect(cookie().maxAge(SupplierInviteAuthService.INVITE_CONTEXT_COOKIE, 0))
			.andExpect(cookie().maxAge(SupplierInviteAuthService.INVITE_STATE_COOKIE, 0))
			.andReturn();

		UserAccount user = userAccountRepository.findByProviderAndProviderUserId(
			SocialProvider.KAKAO,
			"supplier-kakao-user"
		).orElseThrow();
		Supplier supplier = supplierRepository.findById(fixture.supplierId()).orElseThrow();
		SupplierInvite invite = inviteRepository.findById(fixture.inviteId()).orElseThrow();
		assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
		assertThat(supplier.getManagerUserId()).isEqualTo(user.getId());
		assertThat(supplier.getPortalStatus()).isEqualTo(SupplierPortalStatus.ACTIVE);
		assertThat(supplier.getContactEmailVerifiedAt()).isNotNull();
		assertThat(invite.getConsumedByUserId()).isEqualTo(user.getId());
		assertThat(invite.getConsumedAt()).isNotNull();

		Cookie accessToken = callback.getResponse().getCookie("ACCESS_TOKEN");
		mockMvc.perform(get("/api/supplier/me").cookie(accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId", is(user.getId().toString())))
			.andExpect(jsonPath("$.supplierId", is(supplier.getId().toString())))
			.andExpect(jsonPath("$.portalStatus", is("ACTIVE")));
	}

	@Test
	void returnsOnlySafeGenericDetailsForExpiredRevokedAndUsedInvites() throws Exception {
		Instant now = Instant.now();
		InviteFixture expired = invite(
			"expired",
			now.minus(8, ChronoUnit.DAYS),
			now.minus(1, ChronoUnit.DAYS)
		);
		InviteFixture revoked = openInvite("revoked");
		SupplierInvite revokedInvite = inviteRepository.findById(revoked.inviteId()).orElseThrow();
		revokedInvite.revoke(ADMIN_ID, SupplierInviteRevocationReasonCode.ADMIN_REISSUE, now);
		inviteRepository.saveAndFlush(revokedInvite);

		InviteFixture used = openInvite("used");
		UserAccount consumedBy = userAccountRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO,
			"already-consumed-user",
			"already-consumed@example.com",
			"Already Consumed",
			UserRole.CUSTOMER
		));
		SupplierInvite usedInvite = inviteRepository.findById(used.inviteId()).orElseThrow();
		usedInvite.consume(consumedBy.getId(), now);
		inviteRepository.saveAndFlush(usedInvite);

		assertSafeInviteFailure(expired, "INVITE_EXPIRED");
		assertSafeInviteFailure(revoked, "INVITE_REVOKED");
		assertSafeInviteFailure(used, "INVITE_ALREADY_USED");
	}

	@Test
	void revalidatesExpiredRevokedAndUsedInvitesAtCallbackWithSafeRedirects() throws Exception {
		Instant now = Instant.now();
		InviteFixture expired = invite(
			"callback-expired",
			now.minus(8, ChronoUnit.DAYS),
			now.minus(1, ChronoUnit.DAYS)
		);
		AuthorizationFlow expiredFlow = authorize(signedContextCookie(expired));
		fakeOAuthProviderClient.profile(
			"callback-expired-code",
			new OAuthProfile("callback-expired-user", "expired@example.com", "Expired")
		);
		assertSafeCallbackFailure(expired, expiredFlow, "callback-expired-code", "INVITE_EXPIRED");

		InviteFixture revoked = openInvite("callback-revoked");
		AuthorizationFlow revokedFlow = beginAuthorization(revoked.rawToken());
		SupplierInvite revokedInvite = inviteRepository.findById(revoked.inviteId()).orElseThrow();
		revokedInvite.revoke(ADMIN_ID, SupplierInviteRevocationReasonCode.ADMIN_REISSUE, now);
		inviteRepository.saveAndFlush(revokedInvite);
		fakeOAuthProviderClient.profile(
			"callback-revoked-code",
			new OAuthProfile("callback-revoked-user", "revoked@example.com", "Revoked")
		);
		assertSafeCallbackFailure(revoked, revokedFlow, "callback-revoked-code", "INVITE_REVOKED");

		InviteFixture used = openInvite("callback-used");
		AuthorizationFlow usedFlow = beginAuthorization(used.rawToken());
		UserAccount consumedBy = userAccountRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO,
			"callback-existing-consumer",
			"callback-existing-consumer@example.com",
			"Existing Consumer",
			UserRole.CUSTOMER
		));
		SupplierInvite usedInvite = inviteRepository.findById(used.inviteId()).orElseThrow();
		usedInvite.consume(consumedBy.getId(), now);
		inviteRepository.saveAndFlush(usedInvite);
		fakeOAuthProviderClient.profile(
			"callback-used-code",
			new OAuthProfile("callback-used-user", "used@example.com", "Used")
		);
		assertSafeCallbackFailure(used, usedFlow, "callback-used-code", "INVITE_ALREADY_USED");
	}

	private org.springframework.test.web.servlet.ResultActions exchange(String rawToken) throws Exception {
		return mockMvc.perform(post("/api/supplier-invites/session")
			.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"token":"%s"}
				""".formatted(rawToken)));
	}

	private AuthorizationFlow beginAuthorization(String rawToken) throws Exception {
		MvcResult exchange = exchange(rawToken)
			.andExpect(status().isOk())
			.andReturn();
		return authorize(exchange.getResponse().getCookie(SupplierInviteAuthService.INVITE_CONTEXT_COOKIE));
	}

	private AuthorizationFlow authorize(Cookie contextCookie) throws Exception {
		MvcResult authorize = mockMvc.perform(get("/api/supplier/auth/kakao/authorize")
				.cookie(contextCookie))
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("https://kauth.kakao.com/oauth/authorize")))
			.andExpect(cookie().exists(SupplierInviteAuthService.INVITE_STATE_COOKIE))
			.andReturn();
		String state = UriComponentsBuilder.fromUriString(authorize.getResponse().getRedirectedUrl())
			.build()
			.getQueryParams()
			.getFirst("state");
		assertThat(state).isNotBlank();
		return new AuthorizationFlow(
			contextCookie,
			authorize.getResponse().getCookie(SupplierInviteAuthService.INVITE_STATE_COOKIE),
			state
		);
	}

	private org.springframework.test.web.servlet.ResultActions callback(
		AuthorizationFlow flow,
		String code
	) throws Exception {
		return mockMvc.perform(get("/api/supplier/auth/kakao/callback")
			.param("code", code)
			.param("state", flow.state())
			.cookie(flow.contextCookie(), flow.stateCookie()));
	}

	private void assertSafeInviteFailure(InviteFixture fixture, String code) throws Exception {
		MvcResult result = exchange(fixture.rawToken())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is(code)))
			.andExpect(jsonPath("$.message", is("Supplier invitation cannot be used")))
			.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(fixture.rawToken());
		assertThat(result.getResponse().getContentAsString()).doesNotContain(fixture.supplierId().toString());
	}

	private void assertSafeCallbackFailure(
		InviteFixture fixture,
		AuthorizationFlow flow,
		String oauthCode,
		String expectedError
	) throws Exception {
		MvcResult result = callback(flow, oauthCode)
			.andExpect(status().isFound())
			.andExpect(header().string(HttpHeaders.LOCATION, containsString("error=" + expectedError)))
			.andExpect(cookie().maxAge(SupplierInviteAuthService.INVITE_CONTEXT_COOKIE, 0))
			.andExpect(cookie().maxAge(SupplierInviteAuthService.INVITE_STATE_COOKIE, 0))
			.andReturn();
		assertThat(result.getResponse().getRedirectedUrl()).doesNotContain(fixture.supplierId().toString());
		assertThat(supplierRepository.findById(fixture.supplierId()).orElseThrow().getManagerUserId()).isNull();
	}

	private Cookie signedContextCookie(InviteFixture fixture) {
		return new Cookie(
			SupplierInviteAuthService.INVITE_CONTEXT_COOKIE,
			contextTokenService.issueInviteContext(fixture.inviteId(), hasher.tokenDigest(fixture.rawToken()))
		);
	}

	private InviteFixture openInvite(String suffix) {
		Instant now = Instant.now();
		return invite(suffix, now.minusSeconds(5), now.plus(1, ChronoUnit.HOURS));
	}

	private InviteFixture invite(String suffix, Instant issuedAt, Instant expiresAt) {
		String rawToken = "token_" + suffix + "_" + "x".repeat(48);
		Supplier supplier = supplierRepository.saveAndFlush(Supplier.portalApplicant(
			"Supplier " + suffix,
			"Contact " + suffix,
			"010-0000-0000",
			suffix + "@supplier.example",
			null
		));
		SupplierInvite invite = inviteRepository.saveAndFlush(SupplierInvite.issue(
			supplier,
			supplier.getEmail(),
			hasher.tokenDigest(rawToken),
			"test-issuance-" + suffix,
			hasher.hmac("test-invite-issuance", suffix),
			expiresAt,
			ADMIN_ID,
			issuedAt
		));
		return new InviteFixture(invite.getId(), supplier.getId(), rawToken);
	}

	private record InviteFixture(UUID inviteId, UUID supplierId, String rawToken) {
	}

	private record AuthorizationFlow(Cookie contextCookie, Cookie stateCookie, String state) {
	}

	@TestConfiguration
	static class FakeOAuthProviderConfiguration {

		@Bean
		@Primary
		FakeSupplierOAuthProviderClient fakeSupplierOAuthProviderClient() {
			return new FakeSupplierOAuthProviderClient();
		}
	}

	static class FakeSupplierOAuthProviderClient implements OAuthProviderClient {

		private final Map<String, OAuthProfile> profiles = new HashMap<>();

		@Override
		public OAuthProfile fetchProfile(SocialProvider provider, String code) {
			if (provider != SocialProvider.KAKAO || !profiles.containsKey(code)) {
				throw new OAuthProviderException("No fake Kakao profile");
			}
			return profiles.get(code);
		}

		void profile(String code, OAuthProfile profile) {
			profiles.put(code, profile);
		}

		void reset() {
			profiles.clear();
		}
	}
}
