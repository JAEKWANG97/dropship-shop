package com.dropshipshop.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
	properties = {
		"app.auth.jwt-secret=postgres-smoke-jwt-secret",
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate"
	}
)
@AutoConfigureMockMvc
class PostgresMigrationSmokeTest {

	@Container
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void appliesFlywayMigrationsAndServesPublicCatalogWithoutSourcePrice() throws Exception {
		Integer migrationCount = jdbcTemplate.queryForObject(
			"select count(*) from flyway_schema_history where success = true",
			Integer.class
		);
		Integer pricingPolicyCount = jdbcTemplate.queryForObject(
			"select count(*) from pricing_policies where active = true",
			Integer.class
		);

		assertThat(migrationCount).isNotNull().isGreaterThanOrEqualTo(22);
		assertThat(pricingPolicyCount).isEqualTo(1);

		Supplier supplier = supplierRepository.saveAndFlush(new Supplier(
			"Postgres smoke supplier",
			null,
			null,
			null,
			null
		));
		productRepository.saveAndFlush(new Product(
			supplier,
			"Postgres smoke product",
			"Smoke product for public API contract",
			10_000,
			12_500,
			ProductCategory.PPE_SAFETY_HELMET,
			ProductStatus.ACTIVE
		));

		mockMvc.perform(get("/actuator/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));

		MvcResult result = mockMvc.perform(get("/api/products"))
			.andExpect(status().isOk())
			.andReturn();
		String products = result.getResponse().getContentAsString();

		assertThat(products)
			.contains("Postgres smoke product")
			.contains("\"basePrice\":12500")
			.doesNotContain("sourcePrice");
	}
}
