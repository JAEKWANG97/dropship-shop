package com.dropshipshop.api.common.health;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.security.SecurityConfig;

@WebMvcTest(HealthCheckController.class)
@Import(SecurityConfig.class)
class HealthCheckControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsOk() throws Exception {
		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("ok")));
	}
}
