package com.dropshipshop.api.auth.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest(properties = {
	"app.supplier-portal.enabled=false",
	"spring.datasource.url=jdbc:h2:mem:supplier_portal_feature_gate;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierPortalFeatureGateApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void hidesPublicAndAuthenticatedSupplierRoutesWithTheSameCentralNotFoundResponse() throws Exception {
		mockMvc.perform(post("/api/supplier-invites/session")
				.header(HttpHeaders.ORIGIN, "http://localhost:3000")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"token":"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")))
			.andExpect(jsonPath("$.path", is("/api/supplier-invites/session")));

		mockMvc.perform(get("/api/supplier/auth/kakao/authorize"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")))
			.andExpect(jsonPath("$.path", is("/api/supplier/auth/kakao/authorize")));

		mockMvc.perform(get("/api/supplier/me"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")))
			.andExpect(jsonPath("$.path", is("/api/supplier/me")));
	}
}
