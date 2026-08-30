package com.dropshipshop.api.supplierproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.dropshipshop.api.auth.JwtAccessTokenService;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.storage.FileStorage;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.supplierproduct.domain.SupplierInventoryChangeHistory;
import com.dropshipshop.api.supplierproduct.repository.SupplierInventoryChangeHistoryRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = {
	"app.supplier-portal.enabled=true",
	"spring.datasource.url=jdbc:h2:mem:supplier_inventory_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierInventoryApiIntegrationTest {

	private static final String ORIGIN = "http://localhost:3000";
	private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JwtAccessTokenService jwtAccessTokenService;
	@Autowired UserAccountRepository userRepository;
	@Autowired SupplierRepository supplierRepository;
	@Autowired ProductRepository productRepository;
	@Autowired ProductOptionRepository optionRepository;
	@Autowired PaymentGroupRepository paymentGroupRepository;
	@Autowired CustomerOrderRepository orderRepository;
	@Autowired OrderItemRepository orderItemRepository;
	@Autowired SupplierInventoryChangeHistoryRepository historyRepository;
	@MockitoBean FileStorage fileStorage;

	@Test
	void updatesAbsoluteInventoryReplaysExactlyAndReturnsCanonicalStaleConflict() throws Exception {
		Manager manager = manager("update");
		Cookie token = accessToken(manager);
		JsonNode product = createProduct(token);
		String productId = product.path("id").asText();
		String optionId = product.path("options").get(0).path("id").asText();
		String request = trackedRequest(0, 10);

		mockMvc.perform(inventoryPut(token, productId, optionId, "inventory-update-0001", request))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.optionId", is(optionId)))
			.andExpect(jsonPath("$.inventoryVersion", is(1)))
			.andExpect(jsonPath("$.inventoryMode", is("TRACKED")))
			.andExpect(jsonPath("$.onHandQuantity", is(10)))
			.andExpect(jsonPath("$.reservedQuantity", is(0)))
			.andExpect(jsonPath("$.availableQuantity", is(10)));

		mockMvc.perform(inventoryPut(token, productId, optionId, "inventory-update-0001", request))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inventoryVersion", is(1)))
			.andExpect(jsonPath("$.onHandQuantity", is(10)));

		mockMvc.perform(inventoryPut(token, productId, optionId, "inventory-update-0002", request))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("INVENTORY_CONFLICT")))
			.andExpect(jsonPath("$.details.currentInventory.optionId", is(optionId)))
			.andExpect(jsonPath("$.details.currentInventory.inventoryVersion", is(1)))
			.andExpect(jsonPath("$.details.currentInventory.availableQuantity", is(10)));

		mockMvc.perform(inventoryPut(token, productId, optionId, "inventory-update-0001", trackedRequest(0, 11)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));

		assertThat(historyRepository.findAllBySubjectProductOptionIdOrderByCreatedAtAsc(UUID.fromString(optionId)))
			.singleElement()
			.satisfies(history -> {
				assertThat(history.getBeforeInventoryVersion()).isZero();
				assertThat(history.getAfterInventoryVersion()).isEqualTo(1);
				assertThat(history.getBeforeOnHandQuantity()).isZero();
				assertThat(history.getAfterOnHandQuantity()).isEqualTo(10);
			});
		assertThat(productRepository.findById(UUID.fromString(productId))).get()
			.satisfies(current -> {
				assertThat(current.getVersion()).isZero();
				assertThat(current.getFirstSubmittedAt()).isNull();
			});
	}

	@Test
	void untrackedProjectionUsesNullQuantitiesAndRejectsServerOwnedFields() throws Exception {
		Manager manager = manager("untracked");
		Cookie token = accessToken(manager);
		JsonNode product = createProduct(token);
		String productId = product.path("id").asText();
		String optionId = product.path("options").get(0).path("id").asText();

		mockMvc.perform(inventoryPut(
			token,
			productId,
			optionId,
			"inventory-untracked-0001",
			"""
				{"expectedInventoryVersion":0,"supplierAvailability":"AVAILABLE","inventoryMode":"UNTRACKED"}
				"""
		))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inventoryVersion", is(1)))
			.andExpect(jsonPath("$.onHandQuantity", nullValue()))
			.andExpect(jsonPath("$.reservedQuantity", is(0)))
			.andExpect(jsonPath("$.availableQuantity", nullValue()));

		mockMvc.perform(inventoryPut(
			token,
			productId,
			optionId,
			"inventory-untracked-0002",
			"""
				{"expectedInventoryVersion":1,"supplierAvailability":"AVAILABLE","inventoryMode":"UNTRACKED",
				 "reservedQuantity":1}
				"""
		))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
	}

	@Test
	void tenantBoundaryIsNotDisclosedByInventoryMutation() throws Exception {
		Manager owner = manager("owner");
		Manager other = manager("other");
		JsonNode product = createProduct(accessToken(owner));

		mockMvc.perform(inventoryPut(
			accessToken(other),
			product.path("id").asText(),
			product.path("options").get(0).path("id").asText(),
			"inventory-tenant-0001",
			trackedRequest(0, 5)
		))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));
	}

	@Test
	void modeChangeIsBlockedWhileAnOpenPaymentPendingOrderReferencesTheOption() throws Exception {
		Manager manager = manager("open-order-mode");
		Cookie token = accessToken(manager);
		JsonNode created = createProduct(token);
		UUID productId = UUID.fromString(created.path("id").asText());
		UUID optionId = UUID.fromString(created.path("options").get(0).path("id").asText());

		mockMvc.perform(inventoryPut(
			token,
			productId.toString(),
			optionId.toString(),
			"inventory-open-order-0001",
			"""
				{"expectedInventoryVersion":0,"supplierAvailability":"AVAILABLE","inventoryMode":"UNTRACKED"}
				"""
		))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inventoryVersion", is(1)));

		Product product = productRepository.findById(productId).orElseThrow();
		ProductOption option = optionRepository.findById(optionId).orElseThrow();
		Instant expiresAt = Instant.now().plusSeconds(3600);
		String reference = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
		PaymentGroup paymentGroup = paymentGroupRepository.saveAndFlush(new PaymentGroup(
			"B102-INV-" + reference, manager.user(), 1_000, expiresAt
		));
		CustomerOrder order = orderRepository.saveAndFlush(new CustomerOrder(
			"B102-ORDER-" + reference,
			manager.user(),
			manager.supplier(),
			paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-0000-0000", "12345", "Seoul", null),
			1_000,
			expiresAt
		));
		orderItemRepository.saveAndFlush(new OrderItem(order, product, option, null, 1));

		mockMvc.perform(inventoryPut(
			token,
			productId.toString(),
			optionId.toString(),
			"inventory-open-order-0002",
			trackedRequest(1, 10)
		))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("INVENTORY_CONFLICT")))
			.andExpect(jsonPath("$.details.currentInventory.inventoryVersion", is(1)))
			.andExpect(jsonPath("$.details.currentInventory.inventoryMode", is("UNTRACKED")))
			.andExpect(jsonPath("$.details.currentInventory.onHandQuantity", nullValue()));
	}

	@Test
	void replaySurvivesAllowedDraftOptionDeletion() throws Exception {
		Manager manager = manager("deleted-option");
		Cookie token = accessToken(manager);
		JsonNode product = createProduct(token);
		String productId = product.path("id").asText();
		String optionId = product.path("options").get(0).path("id").asText();
		String request = trackedRequest(0, 5);

		mockMvc.perform(post("/api/supplier/products/{productId}/options", productId)
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedVersion":0,"name":"Second","sourceAdditionalPrice":0,"sortOrder":1}
					"""))
			.andExpect(status().isCreated());
		mockMvc.perform(inventoryPut(token, productId, optionId, "inventory-delete-0001", request))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/supplier/products/{productId}/options/{optionId}", productId, optionId)
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.header(HttpHeaders.IF_MATCH, "\"1\""))
			.andExpect(status().isNoContent());

		mockMvc.perform(inventoryPut(token, productId, optionId, "inventory-delete-0001", request))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.optionId", is(optionId)))
			.andExpect(jsonPath("$.inventoryVersion", is(1)))
			.andExpect(jsonPath("$.onHandQuantity", is(5)));

		SupplierInventoryChangeHistory history = historyRepository
			.findAllBySubjectProductOptionIdOrderByCreatedAtAsc(UUID.fromString(optionId)).getFirst();
		assertThat(history.getProductOption()).isNull();
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder inventoryPut(
		Cookie token,
		String productId,
		String optionId,
		String idempotencyKey,
		String request
	) {
		return put("/api/supplier/products/{productId}/options/{optionId}/inventory", productId, optionId)
			.cookie(token)
			.header(HttpHeaders.ORIGIN, ORIGIN)
			.header("Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(request);
	}

	private JsonNode createProduct(Cookie token) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/supplier/products")
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Portal Product","summary":"Summary","sourcePrice":1000,
					 "minimumOrderQuantity":1,"orderQuantityStep":1,"categoryCode":"PPE_WORK_GLOVES"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.options[0].inventoryVersion", is(0)))
			.andExpect(jsonPath("$.options[0].inventoryMode", is("TRACKED")))
			.andExpect(jsonPath("$.options[0].onHandQuantity", is(0)))
			.andExpect(jsonPath("$.options[0].reservedQuantity", is(0)))
			.andExpect(jsonPath("$.options[0].availableQuantity", is(0)))
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private String trackedRequest(long version, long onHand) {
		return """
			{"expectedInventoryVersion":%d,"supplierAvailability":"AVAILABLE",
			 "inventoryMode":"TRACKED","onHandQuantity":%d}
			""".formatted(version, onHand);
	}

	private Cookie accessToken(Manager manager) {
		return new Cookie("ACCESS_TOKEN", jwtAccessTokenService.issue(manager.user()));
	}

	private Manager manager(String suffix) {
		UserAccount user = userRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO, "supplier-inventory-" + suffix, suffix + "@user.example", suffix, UserRole.CUSTOMER
		));
		Supplier supplier = supplierRepository.saveAndFlush(Supplier.portalApplicant(
			"Supplier " + suffix, suffix, "010-0000-0000", suffix + "@supplier.example", null
		));
		Instant now = Instant.now();
		supplier.verifyPortalContract("contract-" + suffix, now.minusSeconds(60), now.plusSeconds(3600), now, ADMIN_ID);
		supplier.changeSalesStatus(SupplierStatus.ACTIVE, now);
		supplier.bindManager(user.getId(), now);
		supplierRepository.saveAndFlush(supplier);
		return new Manager(user, supplier);
	}

	private record Manager(UserAccount user, Supplier supplier) {
	}
}
