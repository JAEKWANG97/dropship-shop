package com.dropshipshop.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
import com.dropshipshop.api.supplierportal.domain.SupplierApplication;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationApprovalMode;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationReviewReasonCode;
import com.dropshipshop.api.supplierportal.repository.SupplierApplicationRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

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
	private SupplierApplicationRepository supplierApplicationRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

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

		assertThat(migrationCount).isNotNull().isGreaterThanOrEqualTo(39);
		assertThat(pricingPolicyCount).isEqualTo(1);

		Supplier supplier = supplierRepository.saveAndFlush(new Supplier(
			"Postgres smoke supplier",
			null,
			null,
			"duplicate.legacy@example.com",
			null
		));
		supplierRepository.saveAndFlush(new Supplier(
			"Postgres duplicate legacy supplier",
			null,
			null,
			" DUPLICATE.LEGACY@example.com ",
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
		UserAccount admin = userAccountRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO,
			"postgres-smoke-admin",
			"postgres-smoke-admin@example.com",
			"Postgres smoke admin",
			UserRole.ADMIN
		));
		Instant now = Instant.now();
		SupplierApplication application = SupplierApplication.submit(
			"Postgres JSONB supplier",
			"Postgres manager",
			"postgres-jsonb@example.com",
			"postgres-jsonb@example.com",
			null,
			null,
			"postgres-jsonb-submit-key",
			"postgres-jsonb-submit-hash",
			"postgres-jsonb-policy",
			now
		);
		application.approve(
			supplier,
			SupplierApplicationApprovalMode.CREATE_NEW,
			null,
			admin.getId(),
			SupplierApplicationReviewReasonCode.APPLICATION_APPROVED,
			"Postgres JSONB smoke",
			"postgres-jsonb-review-key",
			"postgres-jsonb-review-hash",
			"{\"status\":\"APPROVED\"}",
			now
		);
		supplierApplicationRepository.saveAndFlush(application);
		String reviewStatus = jdbcTemplate.queryForObject(
			"select review_result_snapshot ->> 'status' from supplier_applications where id = ?",
			String.class,
			application.getId()
		);
		assertThat(reviewStatus).isEqualTo("APPROVED");

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

	@Test
	void upgradesLegacyDuplicateSupplierEmailsThroughV39() throws Exception {
		try (PostgreSQLContainer<?> upgrade = new PostgreSQLContainer<>("postgres:17-alpine")) {
			upgrade.start();
			Flyway.configure()
				.dataSource(upgrade.getJdbcUrl(), upgrade.getUsername(), upgrade.getPassword())
				.target(MigrationVersion.fromVersion("38"))
				.load()
				.migrate();

			try (Connection connection = DriverManager.getConnection(
				upgrade.getJdbcUrl(),
				upgrade.getUsername(),
				upgrade.getPassword()
			); PreparedStatement statement = connection.prepareStatement("""
				insert into suppliers(id, name, email, status, created_at, updated_at)
				values (?, ?, ?, 'ACTIVE', now(), now())
				""")) {
				for (String email : new String[] {"duplicate.legacy@example.com", " DUPLICATE.LEGACY@example.com "}) {
					statement.setObject(1, UUID.randomUUID());
					statement.setString(2, "Legacy duplicate " + email);
					statement.setString(3, email);
					statement.addBatch();
				}
				statement.executeBatch();
			}

			Flyway.configure()
				.dataSource(upgrade.getJdbcUrl(), upgrade.getUsername(), upgrade.getPassword())
				.load()
				.migrate();

			try (Connection connection = DriverManager.getConnection(
				upgrade.getJdbcUrl(),
				upgrade.getUsername(),
				upgrade.getPassword()
			); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("""
				select count(*)
				from suppliers
				where lower(btrim(email)) = 'duplicate.legacy@example.com'
				  and portal_enrolled_at is null
				""")) {
				assertThat(result.next()).isTrue();
				assertThat(result.getInt(1)).isEqualTo(2);
			}
		}
	}
}
