package com.dropshipshop.api.supplierportal;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.JwtAccessTokenService;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierSalesAction;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:supplier_portal_security;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierPortalSecurityApiIntegrationTest {

	private static final String ALLOWED_ORIGIN = "http://localhost:3000";
	private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtAccessTokenService jwtAccessTokenService;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Test
	void removesDynamicSupplierAuthorityFromTheSameJwtAfterContractExpires() throws Exception {
		ManagerFixture fixture = activeManager("contract-expiry");
		Instant now = Instant.now();
		fixture.supplier().verifyPortalContract(
			"contract-current",
			now.minusSeconds(60),
			now.plusSeconds(3600),
			now,
			ADMIN_ID
		);
		supplierRepository.saveAndFlush(fixture.supplier());
		Cookie existingJwt = accessToken(fixture.user());

		mockMvc.perform(get("/api/supplier/me").cookie(existingJwt))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplierId", is(fixture.supplier().getId().toString())))
			.andExpect(jsonPath("$.contractStatus", is("VERIFIED")));

		Supplier overdue = supplierRepository.findById(fixture.supplier().getId()).orElseThrow();
		overdue.verifyPortalContract(
			"contract-overdue",
			now.minusSeconds(7200),
			now.minusSeconds(3600),
			now,
			ADMIN_ID
		);
		supplierRepository.saveAndFlush(overdue);

		mockMvc.perform(get("/api/supplier/me").cookie(existingJwt))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code", is("FORBIDDEN")));
	}

	@Test
	void removesDynamicSupplierAuthorityFromTheSameJwtAfterPortalDisable() throws Exception {
		ManagerFixture fixture = activeManager("portal-disable");
		Cookie existingJwt = accessToken(fixture.user());

		mockMvc.perform(get("/api/supplier/me").cookie(existingJwt))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplierId", is(fixture.supplier().getId().toString())));

		Supplier disabled = supplierRepository.findById(fixture.supplier().getId()).orElseThrow();
		disabled.disablePortal(SupplierSalesAction.KEEP);
		supplierRepository.saveAndFlush(disabled);

		mockMvc.perform(get("/api/supplier/me").cookie(existingJwt))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code", is("FORBIDDEN")));
	}

	@Test
	void allowsOnlyConfiguredOriginOrSameOriginRefererForInviteExchange() throws Exception {
		String unknownToken = "x".repeat(48);
		String requestBody = """
			{"token":"%s"}
			""".formatted(unknownToken);

		mockMvc.perform(post("/api/supplier-invites/session")
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("INVITE_INVALID")));

		mockMvc.perform(post("/api/supplier-invites/session")
				.header(HttpHeaders.ORIGIN, "https://evil.example")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/supplier-invites/session")
				.header(HttpHeaders.REFERER, ALLOWED_ORIGIN + "/supplier/activate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("INVITE_INVALID")));

		mockMvc.perform(post("/api/supplier-invites/session")
				.header(HttpHeaders.REFERER, "https://evil.example/supplier/activate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code", is("ORIGIN_NOT_ALLOWED")));
	}

	@Test
	void allowsIfMatchAndExposesEtagForSupplierProductRequests() throws Exception {
		mockMvc.perform(options("/api/supplier/products/00000000-0000-0000-0000-000000000001")
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "if-match"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "if-match"));

		mockMvc.perform(get("/api/products/00000000-0000-0000-0000-000000000001")
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, ETag"));
	}

	private ManagerFixture activeManager(String suffix) {
		UserAccount user = userAccountRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO,
			"supplier-security-" + suffix,
			suffix + "@user.example",
			"Supplier Manager " + suffix,
			UserRole.CUSTOMER
		));
		Supplier supplier = supplierRepository.saveAndFlush(Supplier.portalApplicant(
			"Supplier " + suffix,
			"Contact " + suffix,
			"010-0000-0000",
			suffix + "@supplier.example",
			null
		));
		supplier.bindManager(user.getId(), Instant.now());
		supplierRepository.saveAndFlush(supplier);
		return new ManagerFixture(user, supplier);
	}

	private Cookie accessToken(UserAccount user) {
		return new Cookie("ACCESS_TOKEN", jwtAccessTokenService.issue(user));
	}

	private record ManagerFixture(UserAccount user, Supplier supplier) {
	}
}
