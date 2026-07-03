package com.dropshipshop.api.dev;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.MOCK,
	properties = {
	"app.seed.enabled=true",
	"spring.datasource.url=jdbc:h2:mem:dev_login_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"app.storage.local.upload-dir=build/test-product-images-dev-login",
	"app.catalog.image-storage-path=build/test-product-images-dev-login"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DevLoginApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Test
	void logsInSeedCustomerByRoleWithStandardAccessTokenCookie() throws Exception {
		String customerId = userAccountRepository.findByProviderAndProviderUserId(
				SocialProvider.GOOGLE,
				"local-b003-customer"
			)
			.orElseThrow()
			.getId()
			.toString();

		MvcResult result = mockMvc.perform(get("/api/dev/login").param("role", "CUSTOMER"))
			.andExpect(status().isOk())
			.andExpect(cookie().exists("ACCESS_TOKEN"))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("ACCESS_TOKEN=")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
			.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")))
			.andExpect(jsonPath("$.provider", is("GOOGLE")))
			.andExpect(jsonPath("$.providerUserId", is("local-b003-customer")))
			.andExpect(jsonPath("$.role", is("CUSTOMER")))
			.andReturn();

		mockMvc.perform(get("/api/me")
				.cookie(result.getResponse().getCookie("ACCESS_TOKEN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId", is(customerId)));
	}

	@Test
	void logsInSeedAdminByProviderUserId() throws Exception {
		String adminId = userAccountRepository.findByProviderAndProviderUserId(
				SocialProvider.GOOGLE,
				"local-b003-admin"
			)
			.orElseThrow()
			.getId()
			.toString();

		MvcResult result = mockMvc.perform(post("/api/dev/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"providerUserId\":\"local-b003-admin\"}"))
			.andExpect(status().isOk())
			.andExpect(cookie().exists("ACCESS_TOKEN"))
			.andExpect(jsonPath("$.providerUserId", is("local-b003-admin")))
			.andExpect(jsonPath("$.role", is("ADMIN")))
			.andReturn();

		mockMvc.perform(get("/api/admin/me")
				.cookie(result.getResponse().getCookie("ACCESS_TOKEN")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId", is(adminId)));
	}

	@Test
	void rejectsMissingSeedTarget() throws Exception {
		mockMvc.perform(post("/api/dev/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}
}
