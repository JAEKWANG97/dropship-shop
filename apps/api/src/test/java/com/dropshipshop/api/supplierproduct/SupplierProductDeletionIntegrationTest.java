package com.dropshipshop.api.supplierproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.dropshipshop.api.auth.JwtAccessTokenService;
import com.dropshipshop.api.cart.domain.Cart;
import com.dropshipshop.api.cart.domain.CartItem;
import com.dropshipshop.api.cart.repository.CartItemRepository;
import com.dropshipshop.api.cart.repository.CartRepository;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductChangeActorType;
import com.dropshipshop.api.catalog.domain.ProductChangeHistory;
import com.dropshipshop.api.catalog.domain.ProductChangeType;
import com.dropshipshop.api.catalog.domain.ProductImage;
import com.dropshipshop.api.catalog.domain.ProductImageCleanupStatus;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;
import com.dropshipshop.api.catalog.repository.ProductImageCleanupJobRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = {
	"app.supplier-portal.enabled=true",
	"spring.datasource.url=jdbc:h2:mem:supplier_product_deletion;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierProductDeletionIntegrationTest {

	private static final String ORIGIN = "http://localhost:3000";
	private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JwtAccessTokenService jwtAccessTokenService;
	@Autowired UserAccountRepository userRepository;
	@Autowired SupplierRepository supplierRepository;
	@Autowired ProductRepository productRepository;
	@Autowired ProductOptionRepository optionRepository;
	@Autowired ProductImageRepository imageRepository;
	@Autowired ProductImageCleanupJobRepository cleanupJobRepository;
	@Autowired ProductChangeHistoryRepository historyRepository;
	@Autowired CartRepository cartRepository;
	@Autowired CartItemRepository cartItemRepository;
	@Autowired PaymentGroupRepository paymentGroupRepository;
	@Autowired CustomerOrderRepository orderRepository;
	@Autowired OrderItemRepository orderItemRepository;

	@Test
	void deletesUnsubmittedDraftAndPreservesAuditWhileEnqueueingAssetCleanup() throws Exception {
		Manager manager = manager("delete-product");
		Cookie token = accessToken(manager);
		CreatedProduct created = createProduct(token);
		Product product = productRepository.findById(created.id()).orElseThrow();
		String storageObjectKey = "products/%s/delete-me.png".formatted(created.id());
		imageRepository.saveAndFlush(new ProductImage(
			product,
			ProductImageType.THUMBNAIL,
			"/uploads/" + storageObjectKey,
			0,
			"Delete me",
			storageObjectKey
		));

		mockMvc.perform(delete("/api/supplier/products/{productId}", created.id())
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.header(HttpHeaders.IF_MATCH, etag(created.version())))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/supplier/products/{productId}", created.id()).cookie(token))
			.andExpect(status().isNotFound());
		assertThat(productRepository.findById(created.id())).isEmpty();
		assertThat(optionRepository.findById(created.defaultOptionId())).isEmpty();
		assertThat(imageRepository.findAllByProduct_IdOrderBySortOrderAsc(created.id())).isEmpty();

		var cleanupJob = cleanupJobRepository.findByStorageObjectKey(storageObjectKey).orElseThrow();
		assertThat(cleanupJob.getSubjectProductId()).isEqualTo(created.id());
		assertThat(cleanupJob.getStatus()).isEqualTo(ProductImageCleanupStatus.PENDING);
		assertThat(cleanupJob.getAttemptCount()).isZero();

		ProductChangeHistory deletion = historyRepository
			.findAllBySubjectProductIdOrderByCreatedAtAsc(created.id()).stream()
			.filter(history -> history.getChangeType() == ProductChangeType.PRODUCT_DELETED)
			.findFirst()
			.orElseThrow();
		assertThat(deletion.getSubjectProductId()).isEqualTo(created.id());
		assertThat(deletion.getActorType()).isEqualTo(ProductChangeActorType.SUPPLIER);
		assertThat(deletion.getActorUserId()).isEqualTo(manager.user().getId());
		assertThat(deletion.getActorSupplierId()).isEqualTo(manager.supplier().getId());
		assertThat(deletion.getBeforeVersion()).isEqualTo(created.version());
		assertThat(deletion.getAfterVersion()).isNull();
		assertThat(deletion.getProduct()).isNull();
	}

	@Test
	void deletesOneOfMultipleOptionsAndAdvancesVersionWithImmutableAuditSubject() throws Exception {
		Manager manager = manager("delete-option");
		Cookie token = accessToken(manager);
		CreatedProduct created = createProduct(token);
		CreatedOption second = createOption(token, created.id(), created.version(), "Second option");

		mockMvc.perform(delete("/api/supplier/products/{productId}/options/{optionId}", created.id(), second.id())
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.header(HttpHeaders.IF_MATCH, etag(second.productVersion())))
			.andExpect(status().isNoContent())
			.andExpect(header().string(HttpHeaders.ETAG, etag(second.productVersion() + 1)));

		assertThat(optionRepository.findById(second.id())).isEmpty();
		assertThat(optionRepository.findById(created.defaultOptionId())).isPresent();
		assertThat(productRepository.findById(created.id())).get()
			.extracting(Product::getVersion)
			.isEqualTo(second.productVersion() + 1);

		ProductChangeHistory deletion = historyRepository
			.findAllBySubjectProductIdOrderByCreatedAtAsc(created.id()).stream()
			.filter(history -> history.getChangeType() == ProductChangeType.OPTION_DELETED)
			.findFirst()
			.orElseThrow();
		assertThat(deletion.getSubjectProductOptionId()).isEqualTo(second.id());
		assertThat(deletion.getBeforeVersion()).isEqualTo(second.productVersion());
		assertThat(deletion.getAfterVersion()).isEqualTo(second.productVersion() + 1);
		assertThat(deletion.getProductOption()).isNull();
	}

	@Test
	void rejectsDeletingTheLastOptionWithoutChangingState() throws Exception {
		Manager manager = manager("last-option");
		Cookie token = accessToken(manager);
		CreatedProduct created = createProduct(token);

		mockMvc.perform(delete("/api/supplier/products/{productId}/options/{optionId}",
				created.id(), created.defaultOptionId())
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.header(HttpHeaders.IF_MATCH, etag(created.version())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("LAST_OPTION_REQUIRED")));

		assertThat(optionRepository.findById(created.defaultOptionId())).isPresent();
		assertThat(productRepository.findById(created.id())).get()
			.extracting(Product::getVersion)
			.isEqualTo(created.version());
	}

	@Test
	void rejectsStaleProductDeletionWithoutPartialWrite() throws Exception {
		Manager manager = manager("stale-delete");
		Cookie token = accessToken(manager);
		CreatedProduct created = createProduct(token);

		mockMvc.perform(patch("/api/supplier/products/{productId}", created.id())
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateRequest(created.version())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(1)));

		mockMvc.perform(delete("/api/supplier/products/{productId}", created.id())
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.header(HttpHeaders.IF_MATCH, etag(created.version())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("PRODUCT_VERSION_CONFLICT")));

		assertThat(productRepository.findById(created.id())).get()
			.satisfies(product -> {
				assertThat(product.getVersion()).isEqualTo(1);
				assertThat(product.getName()).isEqualTo("Updated Product");
			});
		assertThat(historyRepository.findAllBySubjectProductIdOrderByCreatedAtAsc(created.id()))
			.noneMatch(history -> history.getChangeType() == ProductChangeType.PRODUCT_DELETED);
	}

	@Test
	void rejectsDeletionAfterFirstSubmission() throws Exception {
		Manager manager = manager("submitted-delete");
		Cookie token = accessToken(manager);
		CreatedProduct created = createProduct(token);

		mockMvc.perform(post("/api/supplier/products/{productId}/submit", created.id())
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"expectedVersion\":%d}".formatted(created.version())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(1)));

		mockMvc.perform(delete("/api/supplier/products/{productId}", created.id())
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.header(HttpHeaders.IF_MATCH, etag(1)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("PRODUCT_ALREADY_SUBMITTED")));

		assertThat(productRepository.findById(created.id())).get()
			.satisfies(product -> {
				assertThat(product.getVersion()).isEqualTo(1);
				assertThat(product.getFirstSubmittedAt()).isNotNull();
			});
	}

	@Test
	void rejectsDeletingAProductReferencedByCart() throws Exception {
		Manager manager = manager("cart-reference");
		Cookie token = accessToken(manager);
		CreatedProduct created = createProduct(token);
		Product product = productRepository.findById(created.id()).orElseThrow();
		ProductOption option = optionRepository.findById(created.defaultOptionId()).orElseThrow();
		UserAccount customer = userRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO,
			"supplier-product-delete-cart-customer",
			"cart-customer@user.example",
			"Cart customer",
			UserRole.CUSTOMER
		));
		Cart cart = cartRepository.saveAndFlush(new Cart(customer));
		cartItemRepository.saveAndFlush(new CartItem(cart, product, option, 1));

		mockMvc.perform(delete("/api/supplier/products/{productId}", created.id())
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.header(HttpHeaders.IF_MATCH, etag(created.version())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("PRODUCT_REFERENCED")));

		assertThat(productRepository.findById(created.id())).isPresent();
		assertThat(optionRepository.findById(created.defaultOptionId())).isPresent();
		assertThat(historyRepository.findAllBySubjectProductIdOrderByCreatedAtAsc(created.id()))
			.noneMatch(history -> history.getChangeType() == ProductChangeType.PRODUCT_DELETED);
	}

	@Test
	void rejectsDeletingAProductReferencedByAnOrder() throws Exception {
		Manager manager = manager("order-product-reference");
		Cookie token = accessToken(manager);
		CreatedProduct created = createProduct(token);
		Product product = productRepository.findById(created.id()).orElseThrow();
		ProductOption option = optionRepository.findById(created.defaultOptionId()).orElseThrow();
		createOrderReference(manager, product, option, "product");

		mockMvc.perform(delete("/api/supplier/products/{productId}", created.id())
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.header(HttpHeaders.IF_MATCH, etag(created.version())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("PRODUCT_REFERENCED")));

		assertThat(productRepository.findById(created.id())).isPresent();
		assertThat(optionRepository.findById(created.defaultOptionId())).isPresent();
		assertThat(historyRepository.findAllBySubjectProductIdOrderByCreatedAtAsc(created.id()))
			.noneMatch(history -> history.getChangeType() == ProductChangeType.PRODUCT_DELETED);
	}

	@Test
	void rejectsDeletingAnOptionReferencedByCart() throws Exception {
		Manager manager = manager("cart-option-reference");
		Cookie token = accessToken(manager);
		CreatedProduct created = createProduct(token);
		CreatedOption second = createOption(token, created.id(), created.version(), "Cart option");
		Product product = productRepository.findById(created.id()).orElseThrow();
		ProductOption option = optionRepository.findById(second.id()).orElseThrow();
		Cart cart = cartRepository.saveAndFlush(new Cart(manager.user()));
		cartItemRepository.saveAndFlush(new CartItem(cart, product, option, 1));

		mockMvc.perform(delete("/api/supplier/products/{productId}/options/{optionId}", created.id(), option.getId())
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.header(HttpHeaders.IF_MATCH, etag(second.productVersion())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("OPTION_REFERENCED")));

		assertThat(optionRepository.findById(option.getId())).isPresent();
		assertThat(productRepository.findById(created.id())).get()
			.extracting(Product::getVersion)
			.isEqualTo(second.productVersion());
	}

	@Test
	void rejectsDeletingAnOptionReferencedByAnOrder() throws Exception {
		Manager manager = manager("order-option-reference");
		Cookie token = accessToken(manager);
		CreatedProduct created = createProduct(token);
		CreatedOption second = createOption(token, created.id(), created.version(), "Order option");
		Product product = productRepository.findById(created.id()).orElseThrow();
		ProductOption option = optionRepository.findById(second.id()).orElseThrow();
		createOrderReference(manager, product, option, "option");

		mockMvc.perform(delete("/api/supplier/products/{productId}/options/{optionId}", created.id(), option.getId())
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.header(HttpHeaders.IF_MATCH, etag(second.productVersion())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("OPTION_REFERENCED")));

		assertThat(optionRepository.findById(option.getId())).isPresent();
		assertThat(productRepository.findById(created.id())).get()
			.extracting(Product::getVersion)
			.isEqualTo(second.productVersion());
	}

	private void createOrderReference(Manager manager, Product product, ProductOption option, String suffix) {
		Instant expiresAt = Instant.now().plusSeconds(3600);
		String reference = suffix.substring(0, 1).toUpperCase()
			+ UUID.randomUUID().toString().replace("-", "").substring(0, 20);
		PaymentGroup paymentGroup = paymentGroupRepository.saveAndFlush(new PaymentGroup(
			"B101-PG-" + reference, manager.user(), 1_300, expiresAt
		));
		CustomerOrder order = orderRepository.saveAndFlush(new CustomerOrder(
			"B101-ORDER-" + reference,
			manager.user(),
			manager.supplier(),
			paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul", "101"),
			1_300,
			expiresAt
		));
		orderItemRepository.saveAndFlush(new OrderItem(order, product, option, null, 1));
	}

	private CreatedProduct createProduct(Cookie token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/supplier/products")
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(productRequest()))
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.ETAG, etag(0)))
			.andReturn();
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		return new CreatedProduct(
			UUID.fromString(body.path("id").asText()),
			body.path("version").asLong(),
			UUID.fromString(body.path("options").get(0).path("id").asText())
		);
	}

	private CreatedOption createOption(Cookie token, UUID productId, long expectedVersion, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/supplier/products/{productId}/options", productId)
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedVersion":%d,"name":"%s","sourceOptionCode":"second",
					"sourceAdditionalPrice":100,"sortOrder":1}
					""".formatted(expectedVersion, name)))
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.ETAG, etag(expectedVersion + 1)))
			.andReturn();
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		for (JsonNode option : body.path("options")) {
			if (name.equals(option.path("name").asText())) {
				return new CreatedOption(UUID.fromString(option.path("id").asText()), body.path("version").asLong());
			}
		}
		throw new AssertionError("Created option is missing from supplier response");
	}

	private Cookie accessToken(Manager manager) {
		return new Cookie("ACCESS_TOKEN", jwtAccessTokenService.issue(manager.user()));
	}

	private Manager manager(String suffix) {
		UserAccount user = userRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO,
			"supplier-product-delete-" + suffix,
			suffix + "@user.example",
			suffix,
			UserRole.CUSTOMER
		));
		Supplier supplier = supplierRepository.saveAndFlush(Supplier.portalApplicant(
			"Supplier " + suffix,
			suffix,
			"010-0000-0000",
			suffix + "@supplier.example",
			null
		));
		Instant now = Instant.now();
		supplier.verifyPortalContract("contract-" + suffix, now.minusSeconds(60), now.plusSeconds(3600), now, ADMIN_ID);
		supplier.changeSalesStatus(SupplierStatus.ACTIVE, now);
		supplier.bindManager(user.getId(), now);
		supplierRepository.saveAndFlush(supplier);
		return new Manager(user, supplier);
	}

	private String productRequest() {
		return """
			{"name":"Portal Product","summary":"Summary","sourcePrice":1000,
			"minimumOrderQuantity":1,"orderQuantityStep":1,"categoryCode":"PPE_WORK_GLOVES"}
			""";
	}

	private String updateRequest(long version) {
		return """
			{"expectedVersion":%d,"name":"Updated Product","summary":"Summary","sourcePrice":1200,
			"minimumOrderQuantity":1,"orderQuantityStep":1,"categoryCode":"PPE_WORK_GLOVES"}
			""".formatted(version);
	}

	private String etag(long version) {
		return "\"" + version + "\"";
	}

	private record Manager(UserAccount user, Supplier supplier) {
	}

	private record CreatedProduct(UUID id, long version, UUID defaultOptionId) {
	}

	private record CreatedOption(UUID id, long productVersion) {
	}
}
