package com.dropshipshop.api.common;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.cors.allowed-origins=https://shop.example.com,https://admin.example.com")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductionReadinessIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void exposesActuatorHealthProbesWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/actuator/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("UP")));

		mockMvc.perform(get("/actuator/health/liveness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("UP")));
	}

	@Test
	void allowsConfiguredCorsOriginsForPublicApiPreflight() throws Exception {
		mockMvc.perform(options("/api/products")
				.header(HttpHeaders.ORIGIN, "https://shop.example.com")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://shop.example.com"))
			.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS"));
	}
}
