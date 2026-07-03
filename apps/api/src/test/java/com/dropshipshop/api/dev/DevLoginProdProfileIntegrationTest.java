package com.dropshipshop.api.dev;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.MOCK,
	properties = {
	"app.dev-login.enabled=true",
	"spring.datasource.url=jdbc:h2:mem:dev_login_prod_guard;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"payments.toss.secret-key=test-secret",
	"sms.sens.access-key=test-access",
	"sms.sens.secret-key=test-secret",
	"sms.sens.service-id=test-service",
	"sms.sens.from-number=01000000000",
	"app.storage.local.upload-dir=build/test-product-images-dev-login-prod",
	"app.internal.sync-token=test-internal-sync-token",
	"app.cors.allowed-origins=http://localhost:3000",
	"app.auth.jwt-secret=test-prod-jwt-secret-for-dev-login-guard",
	"app.auth.success-redirect-uri=http://localhost:3000/auth/callback/success",
	"app.oauth.google.client-id=test-google-client",
	"app.oauth.google.client-secret=test-google-secret",
	"app.oauth.google.redirect-uri=http://localhost/api/auth/oauth2/google/callback",
	"app.oauth.kakao.client-id=test-kakao-client",
	"app.oauth.kakao.client-secret=test-kakao-secret",
	"app.oauth.kakao.redirect-uri=http://localhost/api/auth/oauth2/kakao/callback",
	"app.oauth.naver.client-id=test-naver-client",
	"app.oauth.naver.client-secret=test-naver-secret",
	"app.oauth.naver.redirect-uri=http://localhost/api/auth/oauth2/naver/callback"
})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DevLoginProdProfileIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void devLoginEndpointIsNotMappedInProdEvenWhenPropertyIsEnabled() throws Exception {
		mockMvc.perform(get("/api/dev/login").param("role", "ADMIN"))
			.andExpect(status().isNotFound());
	}
}
