package com.dropshipshop.api.admin;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.auth.security.SecurityConfig;
import com.dropshipshop.api.auth.security.TestAuthentication;

@WebMvcTest(AdminProfileController.class)
@Import({SecurityConfig.class, CurrentUser.class})
class AdminProfileControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void rejectsAnonymousUser() throws Exception {
		mockMvc.perform(get("/api/admin/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsCustomerUser() throws Exception {
		mockMvc.perform(get("/api/admin/me")
				.with(authentication(TestAuthentication.customer())))
			.andExpect(status().isForbidden());
	}

	@Test
	void allowsAdminUser() throws Exception {
		mockMvc.perform(get("/api/admin/me")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userId", is(TestAuthentication.ADMIN_ID.toString())));
	}
}
