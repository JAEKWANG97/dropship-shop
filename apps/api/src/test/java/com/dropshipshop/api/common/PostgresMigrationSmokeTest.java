package com.dropshipshop.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import com.dropshipshop.api.catalog.domain.ProductImage;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.auth.JwtAccessTokenService;
import com.dropshipshop.api.supplierportal.domain.SupplierApplication;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationApprovalMode;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationReviewReasonCode;
import com.dropshipshop.api.supplierportal.repository.SupplierApplicationRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.servlet.http.Cookie;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
	properties = {
		"app.auth.jwt-secret=postgres-smoke-jwt-secret",
		"app.cors.allowed-origins=http://localhost:3000",
		"app.supplier-portal.enabled=true",
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
	private ProductImageRepository productImageRepository;

	@Autowired
	private SupplierApplicationRepository supplierApplicationRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private JwtAccessTokenService jwtAccessTokenService;

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

		assertThat(migrationCount).isNotNull().isGreaterThanOrEqualTo(40);
		assertThat(pricingPolicyCount).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
			"select version from pricing_policies where active = true",
			Long.class
		)).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject("""
			select column_default
			from information_schema.columns
			where table_schema = current_schema()
			  and table_name = 'products'
			  and column_name = 'source_auto_sold_out'
			""", String.class)).containsIgnoringCase("false");

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
	void swapsSupplierThumbnailAgainstThePostgresPartialUniqueIndex() throws Exception {
		String suffix = UUID.randomUUID().toString();
		UserAccount manager = userAccountRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO,
			"postgres-thumbnail-" + suffix,
			"postgres-thumbnail-" + suffix + "@user.example",
			"Postgres thumbnail manager",
			UserRole.CUSTOMER
		));
		UserAccount admin = userAccountRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO,
			"postgres-thumbnail-admin-" + suffix,
			"postgres-thumbnail-admin-" + suffix + "@user.example",
			"Postgres thumbnail admin",
			UserRole.ADMIN
		));
		Supplier supplier = Supplier.portalApplicant(
			"Postgres thumbnail supplier",
			"Manager",
			"010-0000-0000",
			"postgres-thumbnail-" + suffix + "@supplier.example",
			null
		);
		Instant now = Instant.now();
		supplier.verifyPortalContract("postgres-thumbnail-v1", now.minusSeconds(60), now.plusSeconds(3600), now,
			admin.getId());
		supplier.changeSalesStatus(SupplierStatus.ACTIVE, now);
		supplier.bindManager(manager.getId(), now);
		supplier = supplierRepository.saveAndFlush(supplier);
		Product product = new Product(
			supplier,
			"Postgres thumbnail product",
			"Exercises the immediate partial unique index",
			1_000,
			1_300,
			ProductCategory.PPE_WORK_GLOVES,
			ProductStatus.HIDDEN,
			ProductManagementChannel.SUPPLIER_PORTAL
		);
		product.updateThumbnailImageUrl("/uploads/products/old-thumbnail.png");
		product = productRepository.saveAndFlush(product);
		ProductImage gallery = productImageRepository.saveAndFlush(new ProductImage(
			product,
			ProductImageType.GALLERY,
			"/uploads/products/new-thumbnail.png",
			0,
			"New thumbnail",
			"products/" + product.getId() + "/new-thumbnail.png"
		));
		ProductImage oldThumbnail = productImageRepository.saveAndFlush(new ProductImage(
			product,
			ProductImageType.THUMBNAIL,
			"/uploads/products/old-thumbnail.png",
			1,
			"Old thumbnail",
			"products/" + product.getId() + "/old-thumbnail.png"
		));

		mockMvc.perform(put("/api/supplier/products/{productId}/images/order", product.getId())
				.cookie(new Cookie("ACCESS_TOKEN", jwtAccessTokenService.issue(manager)))
				.header(HttpHeaders.ORIGIN, "http://localhost:3000")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "expectedVersion": 0,
					  "images": [
					    {"imageId":"%s","type":"THUMBNAIL","sortOrder":0,"altText":"New thumbnail"},
					    {"imageId":"%s","type":"GALLERY","sortOrder":1,"altText":"Old thumbnail"}
					  ]
					}
					""".formatted(gallery.getId(), oldThumbnail.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1))
			.andExpect(jsonPath("$.images[0].type").value("THUMBNAIL"))
			.andExpect(jsonPath("$.images[0].imageUrl").value("/uploads/products/new-thumbnail.png"));

		assertThat(productImageRepository.findById(gallery.getId())).get()
			.extracting(ProductImage::getType)
			.isEqualTo(ProductImageType.THUMBNAIL);
		assertThat(productImageRepository.findById(oldThumbnail.getId())).get()
			.extracting(ProductImage::getType)
			.isEqualTo(ProductImageType.GALLERY);
	}

	@Test
	void upgradesLegacyDuplicateSupplierEmailsThroughLatestMigration() throws Exception {
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

			try (Connection connection = DriverManager.getConnection(
				upgrade.getJdbcUrl(),
				upgrade.getUsername(),
				upgrade.getPassword()
			); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("""
				select count(*)
				from products
					where management_channel <> 'COREABLE'
					   or version <> 0
					   or review_status is not null
					   or source_auto_sold_out
				""")) {
				assertThat(result.next()).isTrue();
				assertThat(result.getInt(1)).isZero();
			}
		}
	}

	@Test
	void blocksV40UpgradeWhenLegacySupplierCostIsNegative() throws Exception {
		try (PostgreSQLContainer<?> upgrade = new PostgreSQLContainer<>("postgres:17-alpine")) {
			upgrade.start();
			Flyway.configure()
				.dataSource(upgrade.getJdbcUrl(), upgrade.getUsername(), upgrade.getPassword())
				.target(MigrationVersion.fromVersion("39"))
				.load()
				.migrate();

			UUID supplierId = UUID.randomUUID();
			try (Connection connection = DriverManager.getConnection(
				upgrade.getJdbcUrl(),
				upgrade.getUsername(),
				upgrade.getPassword()
			); PreparedStatement supplier = connection.prepareStatement("""
				insert into suppliers(id, name, status, created_at, updated_at)
				values (?, 'Negative legacy supplier', 'ACTIVE', now(), now())
				"""); PreparedStatement product = connection.prepareStatement("""
				insert into products(
					id, supplier_id, name, summary, base_price, source_price, category_code,
					status, compliance_status, detail_version, minimum_order_quantity,
					order_quantity_step, created_at, updated_at
				) values (?, ?, 'Negative legacy product', 'Migration preflight fixture',
					1000, -1, 'PPE_WORK_GLOVES', 'HIDDEN', 'PENDING', 1, 1, 1, now(), now())
				""")) {
				supplier.setObject(1, supplierId);
				supplier.executeUpdate();
				product.setObject(1, UUID.randomUUID());
				product.setObject(2, supplierId);
				product.executeUpdate();
			}

			Flyway migration = Flyway.configure()
				.dataSource(upgrade.getJdbcUrl(), upgrade.getUsername(), upgrade.getPassword())
				.load();
			assertThatThrownBy(migration::migrate)
				.hasMessageContaining("V40__add_supplier_product_catalog_foundation.sql");
		}
	}

	@Test
	void blocksV40UpgradeWhenLegacyAggregateCustomerUnitPriceExceedsTheCap() throws Exception {
		try (PostgreSQLContainer<?> upgrade = new PostgreSQLContainer<>("postgres:17-alpine")) {
			upgrade.start();
			Flyway.configure()
				.dataSource(upgrade.getJdbcUrl(), upgrade.getUsername(), upgrade.getPassword())
				.target(MigrationVersion.fromVersion("39"))
				.load()
				.migrate();

			UUID supplierId = UUID.randomUUID();
			UUID productId = UUID.randomUUID();
			try (Connection connection = DriverManager.getConnection(
				upgrade.getJdbcUrl(),
				upgrade.getUsername(),
				upgrade.getPassword()
			); PreparedStatement supplier = connection.prepareStatement("""
				insert into suppliers(id, name, status, created_at, updated_at)
				values (?, 'Aggregate price legacy supplier', 'ACTIVE', now(), now())
				"""); PreparedStatement product = connection.prepareStatement("""
				insert into products(
					id, supplier_id, name, summary, base_price, source_price, category_code,
					status, compliance_status, detail_version, minimum_order_quantity,
					order_quantity_step, created_at, updated_at
				) values (?, ?, 'Aggregate price legacy product', 'Migration preflight fixture',
					900000000, 1000, 'PPE_WORK_GLOVES', 'ACTIVE', 'VERIFIED', 1, 1, 1, now(), now())
				"""); PreparedStatement option = connection.prepareStatement("""
				insert into product_options(
					id, product_id, name, additional_price, status, created_at, updated_at
				) values (?, ?, 'Legacy expensive option', 100000001, 'ACTIVE', now(), now())
				""")) {
				supplier.setObject(1, supplierId);
				supplier.executeUpdate();
				product.setObject(1, productId);
				product.setObject(2, supplierId);
				product.executeUpdate();
				option.setObject(1, UUID.randomUUID());
				option.setObject(2, productId);
				option.executeUpdate();
			}

			Flyway migration = Flyway.configure()
				.dataSource(upgrade.getJdbcUrl(), upgrade.getUsername(), upgrade.getPassword())
				.load();
			assertThatThrownBy(migration::migrate)
				.hasMessageContaining("V40__add_supplier_product_catalog_foundation.sql");
		}
	}
}
