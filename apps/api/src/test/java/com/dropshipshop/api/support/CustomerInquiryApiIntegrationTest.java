package com.dropshipshop.api.support;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.security.TestAuthentication;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CustomerInquiryApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void createsPublicInquiryAndListsForAdminOnly() throws Exception {
		mockMvc.perform(post("/api/customer-inquiries")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "customerName": "김고객",
					  "email": "customer@example.com",
					  "phone": "010-1111-2222",
					  "subject": "배송 문의",
					  "message": "배송 일정이 궁금합니다."
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.customerName", is("김고객")))
			.andExpect(jsonPath("$.subject", is("배송 문의")));

		mockMvc.perform(get("/api/admin/customer-inquiries"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/admin/customer-inquiries")
				.with(authentication(TestAuthentication.customer())))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/admin/customer-inquiries")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inquiries", hasSize(1)))
			.andExpect(jsonPath("$.inquiries[0].email", is("customer@example.com")));
	}

	@Test
	void rejectsInvalidInquiry() throws Exception {
		mockMvc.perform(post("/api/customer-inquiries")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "customerName": "",
					  "email": "not-email",
					  "subject": "",
					  "message": ""
					}
					"""))
			.andExpect(status().isBadRequest());
	}
}
