package com.dropshipshop.api.user;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.AuthProperties;
import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.auth.security.SecurityConfig;
import com.dropshipshop.api.auth.security.TestAuthentication;

@WebMvcTest(UserProfileController.class)
@Import({SecurityConfig.class, CurrentUser.class, UserProfileControllerTest.UserProfileControllerTestConfig.class})
class UserProfileControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rejectsAnonymousUser() throws Exception {
		mockMvc.perform(get("/api/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void returnsAuthenticatedUserScope() throws Exception {
		mockMvc.perform(get("/api/me")
				.with(authentication(TestAuthentication.customer())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId", is(TestAuthentication.CUSTOMER_ID.toString())));
	}

	@TestConfiguration
	static class UserProfileControllerTestConfig {

		@Bean
		AccountDeletionService accountDeletionService() {
			return mock(AccountDeletionService.class);
		}

		@Bean
		AuthProperties authProperties() {
			return mock(AuthProperties.class);
		}
	}
}
