package com.dropshipshop.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.dropshipshop.api.catalog.domain.InventoryMode;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductReviewStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierAvailability;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.account.domain.UserPolicyAgreement;
import com.dropshipshop.api.account.repository.UserPolicyAgreementRepository;
import com.dropshipshop.api.auth.JwtAccessTokenService;
import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.cart.domain.Cart;
import com.dropshipshop.api.cart.domain.CartItem;
import com.dropshipshop.api.cart.repository.CartItemRepository;
import com.dropshipshop.api.cart.repository.CartRepository;
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
		"app.sales.enabled=true",
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
	private ProductOptionRepository productOptionRepository;

	@Autowired
	private ProductImageRepository productImageRepository;

	@Autowired
	private CartRepository cartRepository;

	@Autowired
	private CartItemRepository cartItemRepository;

	@Autowired
	private SupplierApplicationRepository supplierApplicationRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private UserPolicyAgreementRepository userPolicyAgreementRepository;

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

		assertThat(migrationCount).isNotNull().isGreaterThanOrEqualTo(43);
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
		assertThat(jdbcTemplate.queryForObject("""
			select count(*)
			from information_schema.columns
			where table_schema = current_schema()
			  and table_name = 'product_options'
			  and column_name in (
				  'supplier_availability', 'inventory_mode', 'on_hand_quantity',
				  'reserved_quantity', 'inventory_version'
			  )
			""", Integer.class)).isEqualTo(5);
		assertThat(jdbcTemplate.queryForObject("""
			select count(*)
			from information_schema.columns
			where table_schema = current_schema()
			  and table_name = 'order_items'
			  and column_name in (
				  'management_channel_snapshot', 'inventory_mode_snapshot', 'reservation_status',
				  'reserved_at', 'consumed_at', 'released_at', 'reacquired_at'
			  )
			""", Integer.class)).isEqualTo(7);
		assertThat(jdbcTemplate.queryForObject("""
			select count(*)
			from pg_constraint
			where conname in (
				'chk_product_options_inventory_projection',
				'chk_order_items_reservation_evidence',
				'uk_supplier_inventory_history_subject_key'
			)
			""", Integer.class)).isEqualTo(3);
		assertThat(jdbcTemplate.queryForObject("""
			select count(*)
			from information_schema.columns
			where table_schema = current_schema()
			  and table_name = 'orders'
			  and column_name = 'delivery_memo'
				""", Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("""
			select count(*)
			from information_schema.tables
			where table_schema = current_schema()
			  and table_name in ('supplier_pii_access_grants', 'supplier_pii_access_logs')
				""", Integer.class)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("""
			select count(*) from pg_constraint
			where conname in (
			  'uk_supplier_pii_access_grants_claim_sequence',
			  'uk_supplier_pii_access_grants_claim_key',
			  'ck_supplier_pii_access_logs_reason'
			)
			""", Integer.class)).isEqualTo(3);
		assertThat(jdbcTemplate.queryForObject("""
			select count(*)
			from information_schema.tables
			where table_schema = current_schema()
			  and table_name in ('shipment_items', 'shipment_change_histories')
			""", Integer.class)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("""
			select count(*)
			from information_schema.columns
			where table_schema = current_schema()
			  and table_name = 'shipments'
			  and column_name in (
			    'version', 'idempotency_key', 'creation_request_hash', 'creation_result_snapshot',
			    'carrier_code', 'registered_at', 'registered_by_user_id', 'registered_actor_type',
			    'delivery_evidence_observed_at'
			  )
			""", Integer.class)).isEqualTo(9);
		assertThat(jdbcTemplate.queryForObject("""
			select count(*)
			from pg_constraint
			where conname = 'uk_shipments_order_id'
			""", Integer.class)).isZero();
		assertThat(jdbcTemplate.queryForObject("""
			select count(*)
			from pg_indexes
			where schemaname = current_schema()
			  and indexname in (
			    'uk_shipments_order_legacy',
			    'idx_shipments_registered_by_user_id',
			    'idx_shipment_change_histories_actor_user_id'
			  )
			""", Integer.class)).isEqualTo(3);
		assertThat(jdbcTemplate.queryForObject("""
			select count(*)
			from pg_trigger
			where not tgisinternal
			  and tgname in (
			    'trg_shipment_items_order_match',
			    'trg_shipments_order_update_match',
			    'trg_order_items_order_update_match',
			    'trg_shipments_legacy_allocations'
			  )
			""", Integer.class)).isEqualTo(4);

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
	void backfillsV40RowsAndKeepsOldShapeInsertsCompatibleAfterV41() throws Exception {
		try (PostgreSQLContainer<?> upgrade = new PostgreSQLContainer<>("postgres:17-alpine")) {
			upgrade.start();
			migrateTo(upgrade, "40");

			UUID userId = UUID.randomUUID();
			UUID supplierId = UUID.randomUUID();
			UUID coreableProductId = UUID.randomUUID();
			UUID portalProductId = UUID.randomUUID();
			UUID coreableOptionId = UUID.randomUUID();
			UUID portalOptionId = UUID.randomUUID();
			V40OrderIds coreableOrder;
			try (Connection connection = connection(upgrade)) {
				insertV40User(connection, userId);
				insertV40Supplier(connection, supplierId);
				insertV40Product(connection, coreableProductId, supplierId, "COREABLE", null);
				insertV40Product(connection, portalProductId, supplierId, "SUPPLIER_PORTAL", "DRAFT");
				insertV40Option(connection, coreableOptionId, coreableProductId);
				insertV40Option(connection, portalOptionId, portalProductId);
				coreableOrder = insertV40PendingOrder(
					connection, userId, supplierId, coreableProductId, coreableOptionId);
			}

			migrateLatest(upgrade);

			try (Connection connection = connection(upgrade)) {
				assertV41Inventory(connection, coreableOptionId, "UNTRACKED", null);
				assertV41Inventory(connection, portalOptionId, "TRACKED", 0L);
				assertV41OrderItemSnapshot(connection, coreableOrder.orderItemId());
				assertV42PrivacySchema(connection, coreableOrder.orderId(), userId, supplierId);

				UUID rollbackCompatibleOptionId = UUID.randomUUID();
				insertV40Option(connection, rollbackCompatibleOptionId, coreableProductId);
				V40OrderIds rollbackCompatibleOrder = insertV40PendingOrder(
					connection, userId, supplierId, coreableProductId, rollbackCompatibleOptionId);
				assertV41Inventory(connection, rollbackCompatibleOptionId, "UNTRACKED", null);
				assertV41OrderItemSnapshot(connection, rollbackCompatibleOrder.orderItemId());
			}
		}
	}

	@Test
	void backfillsV43LegacyShipmentsBeforeAllowingPluralRows() throws Exception {
		try (PostgreSQLContainer<?> upgrade = new PostgreSQLContainer<>("postgres:17-alpine")) {
			upgrade.start();
			migrateTo(upgrade, "42");

			UUID userId = UUID.randomUUID();
			UUID supplierId = UUID.randomUUID();
			UUID productId = UUID.randomUUID();
			UUID optionId = UUID.randomUUID();
			UUID secondOrderItemId = UUID.randomUUID();
			UUID legacyShipmentId = UUID.randomUUID();
			V40OrderIds order;
			try (Connection connection = connection(upgrade)) {
				insertV40User(connection, userId);
				insertV40Supplier(connection, supplierId);
				insertV40Product(connection, productId, supplierId, "COREABLE", null);
				insertV40Option(connection, optionId, productId);
				order = insertV40PendingOrder(connection, userId, supplierId, productId, optionId);
				insertV42OrderItem(
					connection, secondOrderItemId, order.orderId(), productId, optionId, supplierId, 2);
				insertV42Shipment(
					connection, legacyShipmentId, order.orderId(), "CJ대한통운", "V43-LEGACY-1");
			}

			migrateLatest(upgrade);

			try (Connection connection = connection(upgrade)) {
				try (PreparedStatement shipment = connection.prepareStatement("""
					select version, carrier_code, registered_at = created_at as registered_from_created
					from shipments
					where id = ?
					""")) {
					shipment.setObject(1, legacyShipmentId);
					try (ResultSet result = shipment.executeQuery()) {
						assertThat(result.next()).isTrue();
						assertThat(result.getLong("version")).isZero();
						assertThat(result.getString("carrier_code")).isEqualTo("CJ_LOGISTICS");
						assertThat(result.getBoolean("registered_from_created")).isTrue();
					}
				}
				try (PreparedStatement allocations = connection.prepareStatement("""
					select count(*) as allocation_count, sum(quantity) as allocated_quantity
					from shipment_items
					where shipment_id = ?
					""")) {
					allocations.setObject(1, legacyShipmentId);
					try (ResultSet result = allocations.executeQuery()) {
						assertThat(result.next()).isTrue();
						assertThat(result.getInt("allocation_count")).isEqualTo(2);
						assertThat(result.getInt("allocated_quantity")).isEqualTo(3);
					}
				}

				assertThatThrownBy(() -> insertV42Shipment(
					connection, UUID.randomUUID(), order.orderId(), "한진택배", "V43-LEGACY-2"
				)).isInstanceOf(SQLException.class);

				UUID rollbackOptionId = UUID.randomUUID();
				insertV40Option(connection, rollbackOptionId, productId);
				V40OrderIds rollbackOrder = insertV40PendingOrder(
					connection, userId, supplierId, productId, rollbackOptionId);
				UUID rollbackShipmentId = UUID.randomUUID();
				insertV42Shipment(
					connection, rollbackShipmentId, rollbackOrder.orderId(), "한진택배", "V43-ROLLBACK-WRITER");
				try (PreparedStatement allocation = connection.prepareStatement("""
					select count(*) as allocation_count, sum(quantity) as allocated_quantity
					from shipment_items
					where shipment_id = ?
					""")) {
					allocation.setObject(1, rollbackShipmentId);
					try (ResultSet result = allocation.executeQuery()) {
						assertThat(result.next()).isTrue();
						assertThat(result.getInt("allocation_count")).isEqualTo(1);
						assertThat(result.getInt("allocated_quantity")).isEqualTo(1);
					}
				}

				UUID portalShipmentId = UUID.randomUUID();
				insertV43PortalShipment(
					connection, portalShipmentId, order.orderId(), userId, "portal-create-key");
				insertV43Allocation(
					connection, UUID.randomUUID(), portalShipmentId, order.orderItemId(), 1);
				assertThatThrownBy(() -> insertV43PortalShipment(
					connection, UUID.randomUUID(), order.orderId(), userId, "portal-create-key"
				)).isInstanceOf(SQLException.class);
				assertThatThrownBy(() -> insertIncompleteV43PortalShipment(
					connection, UUID.randomUUID(), order.orderId(), userId
				)).isInstanceOf(SQLException.class);
				assertThatThrownBy(() -> insertV43Allocation(
					connection, UUID.randomUUID(), portalShipmentId, order.orderItemId(), 0
				)).isInstanceOf(SQLException.class);
				assertThatThrownBy(() -> insertV43Allocation(
					connection, UUID.randomUUID(), portalShipmentId, rollbackOrder.orderItemId(), 1
				)).isInstanceOf(SQLException.class)
					.hasMessageContaining("shipment_items order mismatch");
				assertThatThrownBy(() -> updateOrderId(
					connection, "shipments", portalShipmentId, rollbackOrder.orderId()
				)).isInstanceOf(SQLException.class)
					.hasMessageContaining("shipment_items order mismatch after shipment");
				assertThatThrownBy(() -> updateOrderId(
					connection, "order_items", order.orderItemId(), rollbackOrder.orderId()
				)).isInstanceOf(SQLException.class)
					.hasMessageContaining("shipment_items order mismatch after order item");
			}
		}
	}

	@Test
	void blocksV43UpgradeWhenALegacyShipmentOrderHasNoItems() throws Exception {
		try (PostgreSQLContainer<?> upgrade = new PostgreSQLContainer<>("postgres:17-alpine")) {
			upgrade.start();
			migrateTo(upgrade, "42");

			UUID userId = UUID.randomUUID();
			UUID supplierId = UUID.randomUUID();
			UUID productId = UUID.randomUUID();
			UUID optionId = UUID.randomUUID();
			try (Connection connection = connection(upgrade)) {
				insertV40User(connection, userId);
				insertV40Supplier(connection, supplierId);
				insertV40Product(connection, productId, supplierId, "COREABLE", null);
				insertV40Option(connection, optionId, productId);
				V40OrderIds order = insertV40PendingOrder(
					connection, userId, supplierId, productId, optionId);
				try (PreparedStatement delete = connection.prepareStatement(
					"delete from order_items where order_id = ?")) {
					delete.setObject(1, order.orderId());
					assertThat(delete.executeUpdate()).isEqualTo(1);
				}
				insertV42Shipment(
					connection, UUID.randomUUID(), order.orderId(), "CJ대한통운", "V43-NO-ITEMS");
			}

			Flyway migration = latestFlyway(upgrade);
			assertThatThrownBy(migration::migrate)
				.hasMessageContaining("V43__add_plural_portal_shipments.sql")
				.hasStackTraceContaining(
					"V43 preflight failed: every legacy shipment order must contain at least one order item"
				);
		}
	}

	@Test
	void serializesTwoCustomerCheckoutsAgainstTheSameTrackedPostgresInventory() throws Exception {
		String suffix = UUID.randomUUID().toString();
		UserAccount verifier = createPostgresUser("inventory-verifier-" + suffix, UserRole.ADMIN);
		UserAccount firstCustomer = createPostgresCustomer("inventory-first-" + suffix);
		UserAccount secondCustomer = createPostgresCustomer("inventory-second-" + suffix);
		Instant now = Instant.now();
		Supplier supplier = new Supplier(
			"Postgres inventory race supplier",
			"Manager",
			"010-0000-0000",
			"inventory-race-" + suffix + "@supplier.example",
			null
		);
		supplier.verifyPortalContract(
			"inventory-race-contract-" + suffix,
			now.minusSeconds(60),
			now.plusSeconds(3600),
			now,
			verifier.getId()
		);
		supplier = supplierRepository.saveAndFlush(supplier);
		Product product = new Product(
			supplier,
			"Postgres tracked race product",
			"Two customers compete for one unit",
			10_000,
			13_000,
			ProductCategory.PPE_WORK_GLOVES,
			ProductStatus.ACTIVE,
			ProductManagementChannel.SUPPLIER_PORTAL
		);
		product.updateReview(ProductReviewStatus.APPROVED, null, null);
		product = productRepository.saveAndFlush(product);
		ProductOption option = new ProductOption(product, "Only unit", 0, ProductOptionStatus.ACTIVE);
		option.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 1L);
		option = productOptionRepository.saveAndFlush(option);
		createCartItem(firstCustomer, product, option);
		createCartItem(secondCustomer, product, option);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Integer> first = executor.submit(() -> checkoutStatusWhenReleased(firstCustomer.getId(), ready, start));
			Future<Integer> second = executor.submit(() -> checkoutStatusWhenReleased(secondCustomer.getId(), ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<Integer> statuses = List.of(
				first.get(15, TimeUnit.SECONDS),
				second.get(15, TimeUnit.SECONDS)
			);
			assertThat(statuses).containsExactlyInAnyOrder(201, 400);
		} finally {
			executor.shutdownNow();
		}

		ProductOption current = productOptionRepository.findById(option.getId()).orElseThrow();
		assertThat(current.getOnHandQuantity()).isEqualTo(1);
		assertThat(current.getReservedQuantity()).isEqualTo(1);
		assertThat(current.getReservedQuantity()).isLessThanOrEqualTo(current.getOnHandQuantity());
		assertThat(jdbcTemplate.queryForObject("""
			select count(*)
			from order_items
			where product_option_id = ?
			  and inventory_mode_snapshot = 'TRACKED'
			  and reservation_status = 'HELD'
			""", Integer.class, option.getId())).isEqualTo(1);
	}

	@Test
	void checkoutWaitsForThePostgresInventoryLockAndRevalidatesAfterRelease() throws Exception {
		String suffix = UUID.randomUUID().toString();
		UserAccount verifier = createPostgresUser("inventory-lock-verifier-" + suffix, UserRole.ADMIN);
		UserAccount customer = createPostgresCustomer("inventory-lock-customer-" + suffix);
		Instant now = Instant.now();
		Supplier supplier = new Supplier(
			"Postgres inventory lock supplier", "Manager", "010-0000-0000",
			"inventory-lock-" + suffix + "@supplier.example", null);
		supplier.verifyPortalContract(
			"inventory-lock-contract-" + suffix, now.minusSeconds(60), now.plusSeconds(3600), now,
			verifier.getId());
		supplier = supplierRepository.saveAndFlush(supplier);
		Product product = new Product(
			supplier, "Postgres inventory lock product", "Checkout must revalidate after a blocked row lock",
			10_000, 13_000, ProductCategory.PPE_WORK_GLOVES, ProductStatus.ACTIVE,
			ProductManagementChannel.SUPPLIER_PORTAL);
		product.updateReview(ProductReviewStatus.APPROVED, null, null);
		product = productRepository.saveAndFlush(product);
		ProductOption option = new ProductOption(product, "Only unit", 0, ProductOptionStatus.ACTIVE);
		option.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 1L);
		option = productOptionRepository.saveAndFlush(option);
		createCartItem(customer, product, option);

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (Connection locker = connection(postgres)) {
			locker.setAutoCommit(false);
			try (PreparedStatement lock = locker.prepareStatement(
				"select id from product_options where id = ? for update")) {
				lock.setObject(1, option.getId());
				try (ResultSet result = lock.executeQuery()) {
					assertThat(result.next()).isTrue();
				}
			}

			Future<Integer> checkout = executor.submit(() -> checkoutStatus(customer.getId()));
			try {
				assertThat(awaitPostgresLockWait()).isTrue();
				try (PreparedStatement update = locker.prepareStatement("""
					update product_options
					set on_hand_quantity = 0, inventory_version = inventory_version + 1
					where id = ?
					""")) {
					update.setObject(1, option.getId());
					assertThat(update.executeUpdate()).isEqualTo(1);
				}
				locker.commit();
				assertThat(checkout.get(15, TimeUnit.SECONDS)).isEqualTo(400);
			} finally {
				if (!locker.getAutoCommit()) {
					locker.rollback();
				}
			}
		} finally {
			executor.shutdownNow();
		}

		ProductOption current = productOptionRepository.findById(option.getId()).orElseThrow();
		assertThat(current.getOnHandQuantity()).isZero();
		assertThat(current.getReservedQuantity()).isZero();
		assertThat(jdbcTemplate.queryForObject(
			"select count(*) from order_items where product_option_id = ?", Integer.class, option.getId()))
			.isZero();
	}

	@Test
	void blocksV41UpgradeWhenAV40OrderItemReferencesAPortalProduct() throws Exception {
		try (PostgreSQLContainer<?> upgrade = new PostgreSQLContainer<>("postgres:17-alpine")) {
			upgrade.start();
			migrateTo(upgrade, "40");

			UUID userId = UUID.randomUUID();
			UUID supplierId = UUID.randomUUID();
			UUID productId = UUID.randomUUID();
			UUID optionId = UUID.randomUUID();
			try (Connection connection = connection(upgrade)) {
				insertV40User(connection, userId);
				insertV40Supplier(connection, supplierId);
				insertV40Product(connection, productId, supplierId, "SUPPLIER_PORTAL", "DRAFT");
				insertV40Option(connection, optionId, productId);
					insertV40PendingOrder(connection, userId, supplierId, productId, optionId);
				}

			Flyway migration = latestFlyway(upgrade);
			assertThatThrownBy(migration::migrate)
				.hasMessageContaining("V41__add_supplier_inventory_and_payment_reservations.sql")
				.hasStackTraceContaining(
					"V41 preflight failed: portal-origin order items require explicit reservation reconciliation"
				);
		}
	}

	@Test
	void blocksV41UpgradeWhenLegacyRefundAmountIsNonPositive() throws Exception {
		try (PostgreSQLContainer<?> upgrade = new PostgreSQLContainer<>("postgres:17-alpine")) {
			upgrade.start();
			migrateTo(upgrade, "40");

			UUID userId = UUID.randomUUID();
			UUID supplierId = UUID.randomUUID();
			UUID productId = UUID.randomUUID();
			UUID optionId = UUID.randomUUID();
			try (Connection connection = connection(upgrade)) {
				insertV40User(connection, userId);
				insertV40Supplier(connection, supplierId);
				insertV40Product(connection, productId, supplierId, "COREABLE", null);
				insertV40Option(connection, optionId, productId);
				V40OrderIds order = insertV40PendingOrder(connection, userId, supplierId, productId, optionId);
				insertV40Refund(connection, order, 0);
			}

			Flyway migration = latestFlyway(upgrade);
			assertThatThrownBy(migration::migrate)
				.hasMessageContaining("V41__add_supplier_inventory_and_payment_reservations.sql")
				.hasStackTraceContaining(
					"V41 preflight failed: existing refund scope, amount, or aggregate linkage is incompatible"
				);
		}
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

	private UserAccount createPostgresUser(String providerUserId, UserRole role) {
		return userAccountRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO,
			providerUserId,
			providerUserId + "@example.com",
			providerUserId,
			role
		));
	}

	private UserAccount createPostgresCustomer(String providerUserId) {
		UserAccount customer = createPostgresUser(providerUserId, UserRole.CUSTOMER);
		customer.updateProfile(providerUserId, providerUserId + "@example.com", "01011112222");
		customer = userAccountRepository.saveAndFlush(customer);
		userPolicyAgreementRepository.saveAndFlush(new UserPolicyAgreement(
			customer,
			"2026-08-02",
			"2026-08-04",
			Instant.now()
		));
		return customer;
	}

	private void createCartItem(UserAccount customer, Product product, ProductOption option) {
		Cart cart = cartRepository.saveAndFlush(new Cart(customer));
		cartItemRepository.saveAndFlush(new CartItem(cart, product, option, 1));
	}

	private int checkoutStatusWhenReleased(
		UUID userId,
		CountDownLatch ready,
		CountDownLatch start
	) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new AssertionError("Concurrent checkout start was not released");
		}
		return checkoutStatus(userId);
	}

	private int checkoutStatus(UUID userId) throws Exception {
		return mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(userId)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "recipientName": "Receiver",
					  "recipientPhone": "010-1111-2222",
					  "postalCode": "12345",
					  "address1": "Seoul test road",
					  "address2": "101"
					}
					"""))
			.andReturn()
			.getResponse()
			.getStatus();
	}

	private boolean awaitPostgresLockWait() throws InterruptedException {
		for (int attempt = 0; attempt < 100; attempt++) {
			Integer waiting = jdbcTemplate.queryForObject("""
				select count(*)
				from pg_stat_activity
				where datname = current_database()
				  and pid <> pg_backend_pid()
				  and wait_event_type = 'Lock'
				  and query ilike '%product_options%'
				""", Integer.class);
			if (waiting != null && waiting > 0) {
				return true;
			}
			Thread.sleep(50);
		}
		return false;
	}

	private void migrateTo(PostgreSQLContainer<?> container, String version) {
		Flyway.configure()
			.dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
			.target(MigrationVersion.fromVersion(version))
			.load()
			.migrate();
	}

	private void migrateLatest(PostgreSQLContainer<?> container) {
		latestFlyway(container).migrate();
	}

	private Flyway latestFlyway(PostgreSQLContainer<?> container) {
		return Flyway.configure()
			.dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
			.load();
	}

	private Connection connection(PostgreSQLContainer<?> container) throws SQLException {
		return DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
	}

	private void insertV40User(Connection connection, UUID userId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			insert into users(
				id, provider, provider_user_id, email, display_name, role, status, created_at, updated_at
			) values (?, 'KAKAO', ?, ?, 'V40 customer', 'CUSTOMER', 'ACTIVE', now(), now())
			""")) {
			statement.setObject(1, userId);
			statement.setString(2, "v40-user-" + userId);
			statement.setString(3, "v40-user-" + userId + "@example.com");
			statement.executeUpdate();
		}
	}

	private void insertV40Supplier(Connection connection, UUID supplierId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			insert into suppliers(id, name, status, created_at, updated_at)
			values (?, 'V40 inventory supplier', 'ACTIVE', now(), now())
			""")) {
			statement.setObject(1, supplierId);
			statement.executeUpdate();
		}
	}

	private void insertV40Product(
		Connection connection,
		UUID productId,
		UUID supplierId,
		String managementChannel,
		String reviewStatus
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			insert into products(
				id, supplier_id, name, summary, base_price, source_price, category_code,
				status, compliance_status, detail_version, minimum_order_quantity,
				order_quantity_step, management_channel, review_status, created_at, updated_at
			) values (?, ?, ?, 'V40 inventory migration fixture', 1000, 800, 'PPE_WORK_GLOVES',
				'HIDDEN', 'PENDING', 1, 1, 1, ?, ?, now(), now())
			""")) {
			statement.setObject(1, productId);
			statement.setObject(2, supplierId);
			statement.setString(3, "V40 " + managementChannel + " product");
			statement.setString(4, managementChannel);
			statement.setString(5, reviewStatus);
			statement.executeUpdate();
		}
	}

	private void insertV40Option(Connection connection, UUID optionId, UUID productId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			insert into product_options(
				id, product_id, name, additional_price, status, created_at, updated_at
			) values (?, ?, 'V40 option', 0, 'ACTIVE', now(), now())
			""")) {
			statement.setObject(1, optionId);
			statement.setObject(2, productId);
			statement.executeUpdate();
		}
	}

	private V40OrderIds insertV40PendingOrder(
		Connection connection,
		UUID userId,
		UUID supplierId,
		UUID productId,
		UUID optionId
	) throws SQLException {
		UUID paymentGroupId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		UUID orderItemId = UUID.randomUUID();
		String reference = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
		try (PreparedStatement paymentGroup = connection.prepareStatement("""
			insert into payment_groups(
				id, checkout_number, user_id, status, total_amount, refundable_amount,
				expires_at, created_at, updated_at
			) values (?, ?, ?, 'PAYMENT_PENDING', 1000, 1000, now() + interval '1 hour', now(), now())
			"""); PreparedStatement order = connection.prepareStatement("""
			insert into orders(
				id, order_number, user_id, supplier_id, payment_group_id, status,
				recipient_name, recipient_phone, postal_code, address1,
				subtotal_amount, shipping_fee, discount_amount, total_amount,
				expires_at, created_at, updated_at
			) values (?, ?, ?, ?, ?, 'PAYMENT_PENDING', 'Receiver', '010-0000-0000', '12345', 'Seoul',
				1000, 0, 0, 1000, now() + interval '1 hour', now(), now())
			"""); PreparedStatement item = connection.prepareStatement("""
			insert into order_items(
				id, order_id, product_id, product_option_id, supplier_id,
				product_name, product_summary, product_detail_version, option_name,
				unit_price, quantity, line_amount, created_at, updated_at
			) values (?, ?, ?, ?, ?, 'Portal product', 'Portal summary', 1, 'Portal option',
				1000, 1, 1000, now(), now())
			""")) {
			paymentGroup.setObject(1, paymentGroupId);
			paymentGroup.setString(2, "B102-PG-" + reference);
			paymentGroup.setObject(3, userId);
			paymentGroup.executeUpdate();

			order.setObject(1, orderId);
			order.setString(2, "B102-ORDER-" + reference);
			order.setObject(3, userId);
			order.setObject(4, supplierId);
			order.setObject(5, paymentGroupId);
			order.executeUpdate();

			item.setObject(1, orderItemId);
			item.setObject(2, orderId);
			item.setObject(3, productId);
			item.setObject(4, optionId);
			item.setObject(5, supplierId);
			item.executeUpdate();
		}
		return new V40OrderIds(paymentGroupId, orderId, orderItemId);
	}

	private void insertV40Refund(Connection connection, V40OrderIds order, long amount) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			insert into refunds(
				id, payment_group_id, order_id, reason, status, refund_amount, refund_scope,
				created_at, updated_at
			) values (?, ?, ?, 'CUSTOMER_CANCEL', 'REQUESTED', ?, 'DELIVERY_GROUP_ORDER', now(), now())
			""")) {
			statement.setObject(1, UUID.randomUUID());
			statement.setObject(2, order.paymentGroupId());
			statement.setObject(3, order.orderId());
			statement.setLong(4, amount);
			statement.executeUpdate();
		}
	}

	private void insertV42OrderItem(
		Connection connection,
		UUID orderItemId,
		UUID orderId,
		UUID productId,
		UUID optionId,
		UUID supplierId,
		int quantity
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			insert into order_items(
				id, order_id, product_id, product_option_id, supplier_id,
				product_name, product_summary, product_detail_version, option_name,
				unit_price, quantity, line_amount, created_at, updated_at
			) values (?, ?, ?, ?, ?, 'V42 second item', 'V42 allocation fixture', 1, 'V42 option',
				1000, ?, 1000 * ?, now(), now())
			""")) {
			statement.setObject(1, orderItemId);
			statement.setObject(2, orderId);
			statement.setObject(3, productId);
			statement.setObject(4, optionId);
			statement.setObject(5, supplierId);
			statement.setInt(6, quantity);
			statement.setInt(7, quantity);
			assertThat(statement.executeUpdate()).isEqualTo(1);
		}
	}

	private void insertV42Shipment(
		Connection connection,
		UUID shipmentId,
		UUID orderId,
		String carrier,
		String trackingNumber
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			insert into shipments(
				id, order_id, carrier, tracking_number, status, shipped_at, created_at, updated_at
			) values (?, ?, ?, ?, 'SHIPPED', now(), now(), now())
			""")) {
			statement.setObject(1, shipmentId);
			statement.setObject(2, orderId);
			statement.setString(3, carrier);
			statement.setString(4, trackingNumber);
			assertThat(statement.executeUpdate()).isEqualTo(1);
		}
	}

	private void insertV43PortalShipment(
		Connection connection,
		UUID shipmentId,
		UUID orderId,
		UUID actorUserId,
		String idempotencyKey
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			insert into shipments(
				id, order_id, carrier, carrier_code, tracking_number, status,
				registered_at, registered_by_user_id, registered_actor_type,
				idempotency_key, creation_request_hash, creation_result_snapshot,
				created_at, updated_at
			) values (?, ?, 'CJ대한통운', 'CJ_LOGISTICS', 'V43-PORTAL', 'TRACKING_REGISTERED',
				now(), ?, 'SUPPLIER', ?, 'v43-create-hash', '{}'::jsonb, now(), now())
			""")) {
			statement.setObject(1, shipmentId);
			statement.setObject(2, orderId);
			statement.setObject(3, actorUserId);
			statement.setString(4, idempotencyKey);
			assertThat(statement.executeUpdate()).isEqualTo(1);
		}
	}

	private void insertIncompleteV43PortalShipment(
		Connection connection,
		UUID shipmentId,
		UUID orderId,
		UUID actorUserId
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			insert into shipments(
				id, order_id, carrier, carrier_code, tracking_number, status,
				registered_at, registered_by_user_id, registered_actor_type,
				idempotency_key, creation_request_hash, creation_result_snapshot,
				created_at, updated_at
			) values (?, ?, 'CJ대한통운', 'CJ_LOGISTICS', 'V43-INCOMPLETE', 'TRACKING_REGISTERED',
				now(), ?, 'SUPPLIER', 'incomplete-key', 'incomplete-hash', null, now(), now())
			""")) {
			statement.setObject(1, shipmentId);
			statement.setObject(2, orderId);
			statement.setObject(3, actorUserId);
			statement.executeUpdate();
		}
	}

	private void insertV43Allocation(
		Connection connection,
		UUID allocationId,
		UUID shipmentId,
		UUID orderItemId,
		int quantity
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			insert into shipment_items(id, shipment_id, order_item_id, quantity, created_at)
			values (?, ?, ?, ?, now())
			""")) {
			statement.setObject(1, allocationId);
			statement.setObject(2, shipmentId);
			statement.setObject(3, orderItemId);
			statement.setInt(4, quantity);
			statement.executeUpdate();
		}
	}

	private void updateOrderId(Connection connection, String table, UUID rowId, UUID orderId)
		throws SQLException {
		if (!table.equals("shipments") && !table.equals("order_items")) {
			throw new IllegalArgumentException("Unsupported V43 order-owner table");
		}
		try (PreparedStatement statement = connection.prepareStatement(
			"update " + table + " set order_id = ? where id = ?")) {
			statement.setObject(1, orderId);
			statement.setObject(2, rowId);
			statement.executeUpdate();
		}
	}

	private void assertV41Inventory(
		Connection connection,
		UUID optionId,
		String expectedMode,
		Long expectedOnHand
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			select supplier_availability, inventory_mode, on_hand_quantity,
				reserved_quantity, inventory_version
			from product_options
			where id = ?
			""")) {
			statement.setObject(1, optionId);
			try (ResultSet result = statement.executeQuery()) {
				assertThat(result.next()).isTrue();
				assertThat(result.getString("supplier_availability")).isEqualTo("AVAILABLE");
				assertThat(result.getString("inventory_mode")).isEqualTo(expectedMode);
				assertThat(result.getObject("on_hand_quantity", Long.class)).isEqualTo(expectedOnHand);
				assertThat(result.getLong("reserved_quantity")).isZero();
				assertThat(result.getLong("inventory_version")).isZero();
			}
		}
	}

	private void assertV41OrderItemSnapshot(Connection connection, UUID orderItemId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
			select management_channel_snapshot, inventory_mode_snapshot, reservation_status,
				reserved_at, consumed_at, released_at, reacquired_at
			from order_items
			where id = ?
			""")) {
			statement.setObject(1, orderItemId);
			try (ResultSet result = statement.executeQuery()) {
				assertThat(result.next()).isTrue();
				assertThat(result.getString("management_channel_snapshot")).isEqualTo("COREABLE");
				assertThat(result.getString("inventory_mode_snapshot")).isEqualTo("UNTRACKED");
				assertThat(result.getString("reservation_status")).isEqualTo("NOT_APPLICABLE");
				assertThat(result.getObject("reserved_at")).isNull();
				assertThat(result.getObject("consumed_at")).isNull();
				assertThat(result.getObject("released_at")).isNull();
				assertThat(result.getObject("reacquired_at")).isNull();
			}
		}
	}

	private void assertV42PrivacySchema(
		Connection connection,
		UUID legacyOrderId,
		UUID userId,
		UUID supplierId
	) throws SQLException {
		try (PreparedStatement order = connection.prepareStatement(
			"select delivery_memo from orders where id = ?")) {
			order.setObject(1, legacyOrderId);
			try (ResultSet result = order.executeQuery()) {
				assertThat(result.next()).isTrue();
				assertThat(result.getString("delivery_memo")).isNull();
			}
		}
		try (Statement statement = connection.createStatement()) {
			assertThat(singleInt(statement, """
				select count(*)
				from pg_constraint
				where conname in (
				  'fk_supplier_pii_access_grants_claim',
				  'fk_supplier_pii_access_grants_supplier',
				  'fk_supplier_pii_access_grants_previous',
				  'fk_supplier_pii_access_grants_admin',
				  'fk_supplier_pii_access_logs_actor_user',
				  'fk_supplier_pii_access_logs_order',
				  'uk_supplier_pii_access_grants_claim_sequence',
				  'uk_supplier_pii_access_grants_claim_key'
				)
				""")).isEqualTo(8);
			assertThat(singleInt(statement, """
				select count(*)
				from pg_indexes
				where schemaname = current_schema()
				  and indexname in (
				    'idx_supplier_pii_access_grants_claim_sequence',
				    'idx_supplier_pii_access_grants_supplier',
				    'idx_supplier_pii_access_logs_actor_time',
				    'idx_supplier_pii_access_logs_order_time',
				    'idx_notification_logs_supplier_operational_retention'
				  )
				""")).isEqualTo(5);
		}
		try (PreparedStatement update = connection.prepareStatement(
			"update orders set delivery_memo = repeat('x', ?) where id = ?")) {
			update.setInt(1, 300);
			update.setObject(2, legacyOrderId);
			assertThat(update.executeUpdate()).isEqualTo(1);
		}
		try (PreparedStatement length = connection.prepareStatement(
			"select char_length(delivery_memo) from orders where id = ?")) {
			length.setObject(1, legacyOrderId);
			try (ResultSet result = length.executeQuery()) {
				assertThat(result.next()).isTrue();
				assertThat(result.getInt(1)).isEqualTo(300);
			}
		}
		assertThatThrownBy(() -> {
			try (PreparedStatement tooLong = connection.prepareStatement(
				"update orders set delivery_memo = repeat('x', ?) where id = ?")) {
				tooLong.setInt(1, 301);
				tooLong.setObject(2, legacyOrderId);
				tooLong.executeUpdate();
			}
		}).isInstanceOf(SQLException.class);

		UUID claimId = UUID.randomUUID();
		try (PreparedStatement claim = connection.prepareStatement("""
			insert into claims(
				id, order_id, user_id, claim_type, claim_reason, status, requested_action,
				customer_memo, created_at, updated_at
			) values (?, ?, ?, 'CANCEL', 'DEFECT', 'APPROVED', 'REFUND',
				'V42 reason constraint fixture', now(), now())
			""")) {
			claim.setObject(1, claimId);
			claim.setObject(2, legacyOrderId);
			claim.setObject(3, userId);
			assertThat(claim.executeUpdate()).isEqualTo(1);
		}
		assertThatThrownBy(() -> insertV42Grant(
			connection, claimId, supplierId, userId, "GRANTED", Instant.now().plusSeconds(3600),
			"Return coordination required", "v42-invalid-free-text"
		)).isInstanceOf(SQLException.class);
		assertThatThrownBy(() -> insertV42Grant(
			connection, claimId, supplierId, userId, "REVOKED", null,
			"RETURN_COORDINATION_REQUIRED", "v42-invalid-action-code"
		)).isInstanceOf(SQLException.class);
		insertV42Grant(
			connection, claimId, supplierId, userId, "GRANTED", Instant.now().plusSeconds(3600),
			"RETURN_COORDINATION_REQUIRED", "v42-valid-grant-code"
		);
	}

	private void insertV42Grant(
		Connection connection,
		UUID claimId,
		UUID supplierId,
		UUID actorId,
		String action,
		Instant accessUntil,
		String reason,
		String idempotencyKey
	) throws SQLException {
		try (PreparedStatement grant = connection.prepareStatement("""
			insert into supplier_pii_access_grants(
				id, claim_id, supplier_id, sequence, action, access_until, previous_grant_id,
				acted_by_admin_id, reason, request_hash, idempotency_key, result_snapshot, created_at
			) values (?, ?, ?, 1, ?, ?, null, ?, ?, 'v42-smoke-hash', ?, '{}'::jsonb, now())
			""")) {
			grant.setObject(1, UUID.randomUUID());
			grant.setObject(2, claimId);
			grant.setObject(3, supplierId);
			grant.setString(4, action);
			grant.setTimestamp(5, accessUntil == null ? null : Timestamp.from(accessUntil));
			grant.setObject(6, actorId);
			grant.setString(7, reason);
			grant.setString(8, idempotencyKey);
			assertThat(grant.executeUpdate()).isEqualTo(1);
		}
	}

	private int singleInt(Statement statement, String sql) throws SQLException {
		try (ResultSet result = statement.executeQuery(sql)) {
			assertThat(result.next()).isTrue();
			return result.getInt(1);
		}
	}

	private record V40OrderIds(UUID paymentGroupId, UUID orderId, UUID orderItemId) {
	}
}
