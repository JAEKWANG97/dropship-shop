package com.dropshipshop.api.supplierproduct;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.account.domain.UserPolicyAgreement;
import com.dropshipshop.api.account.repository.UserPolicyAgreementRepository;
import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.cart.domain.Cart;
import com.dropshipshop.api.cart.domain.CartItem;
import com.dropshipshop.api.cart.repository.CartItemRepository;
import com.dropshipshop.api.cart.repository.CartRepository;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.InventoryMode;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductReviewReasonCode;
import com.dropshipshop.api.catalog.domain.ProductReviewStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierAvailability;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest(properties =
	"spring.datasource.url=jdbc:h2:mem:supplier_portal_catalog_gate;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierPortalCatalogGateIntegrationTest {

	private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductOptionRepository productOptionRepository;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private UserPolicyAgreementRepository userPolicyAgreementRepository;

	@Autowired
	private CartRepository cartRepository;

	@Autowired
	private CartItemRepository cartItemRepository;

	@MockitoBean
	private SupplierPortalFeatureGate featureGate;

	@Test
	void portalFlagOffBlocksEveryPathWhileCoreableRemainsSellable() throws Exception {
		when(featureGate.isEnabled()).thenReturn(false);
		Instant now = Instant.now();
		GateFixture portal = portalFixture(
			"gate-flag-off-portal",
			verifiedSupplier("gate-flag-off-portal", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS)),
			ProductReviewStatus.APPROVED
		);
		GateFixture coreable = coreableFixture("gate-flag-off-coreable");

		assertBlockedAcrossPublicCartAndCheckout(portal);
		assertAllowedAcrossPublicCartAndCheckout(coreable);
	}

	@Test
	void supplierAndReviewAndContractConditionsBlockEveryPath() throws Exception {
		when(featureGate.isEnabled()).thenReturn(true);
		Instant now = Instant.now();

		Supplier inactive = verifiedSupplier(
			"gate-inactive",
			now.minus(1, ChronoUnit.DAYS),
			now.plus(1, ChronoUnit.DAYS)
		);
		inactive.updateLegacy("gate-inactive", null, null, null, null, SupplierStatus.INACTIVE);
		List<GateFixture> blocked = List.of(
			portalFixture("gate-inactive", inactive, ProductReviewStatus.APPROVED),
			portalFixture(
				"gate-draft",
				verifiedSupplier("gate-draft", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS)),
				ProductReviewStatus.DRAFT
			),
			portalFixture(
				"gate-review-required",
				verifiedSupplier("gate-review-required", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS)),
				ProductReviewStatus.REVIEW_REQUIRED
			),
			portalFixture("gate-unverified", supplier("gate-unverified"), ProductReviewStatus.APPROVED),
			portalFixture(
				"gate-not-yet-effective",
				verifiedSupplier(
					"gate-not-yet-effective",
					now.plus(1, ChronoUnit.DAYS),
					now.plus(2, ChronoUnit.DAYS)
				),
				ProductReviewStatus.APPROVED
			),
			portalFixture(
				"gate-expired",
				verifiedSupplier("gate-expired", now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS)),
				ProductReviewStatus.APPROVED
			)
		);

		for (GateFixture fixture : blocked) {
			assertBlockedAcrossPublicCartAndCheckout(fixture);
		}
	}

	@Test
	void approvedPortalProductsPassEveryPathWithActiveSupplierContractAndOption() throws Exception {
		when(featureGate.isEnabled()).thenReturn(true);
		Instant now = Instant.now();
		List<GateFixture> allowed = List.of(
			portalFixture(
				"gate-auto-approved",
				verifiedSupplier("gate-auto-approved", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS)),
				ProductReviewStatus.AUTO_APPROVED
			),
			portalFixture(
				"gate-approved",
				verifiedSupplier("gate-approved", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS)),
				ProductReviewStatus.APPROVED
			)
		);

		for (GateFixture fixture : allowed) {
			assertAllowedAcrossPublicCartAndCheckout(fixture);
		}
	}

	@Test
	void trackedZeroStockIsPubliclySoldOutWithoutLeakingInventoryAndRestockRecoversIt() throws Exception {
		when(featureGate.isEnabled()).thenReturn(true);
		Instant now = Instant.now();
		GateFixture fixture = portalFixture(
			"gate-zero-stock",
			verifiedSupplier("gate-zero-stock", now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS)),
			ProductReviewStatus.APPROVED
		);

		mockMvc.perform(get("/api/products").param("q", fixture.product().getName()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products[0].purchasable", is(false)));
		mockMvc.perform(get("/api/products/{productId}", fixture.product().getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.purchasable", is(false)))
			.andExpect(jsonPath("$.options[0].purchasable", is(false)))
			.andExpect(jsonPath("$.options[0].inventoryMode").doesNotExist())
			.andExpect(jsonPath("$.options[0].onHandQuantity").doesNotExist())
			.andExpect(jsonPath("$.options[0].reservedQuantity").doesNotExist())
			.andExpect(jsonPath("$.options[0].availableQuantity").doesNotExist());

		UserAccount customer = createCustomer("gate-zero-stock-customer");
		addCartItem(customer.getId(), fixture.option().getId()).andExpect(status().isBadRequest());

		fixture.option().updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 10L);
		productOptionRepository.saveAndFlush(fixture.option());
		mockMvc.perform(get("/api/products/{productId}", fixture.product().getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.purchasable", is(true)))
			.andExpect(jsonPath("$.options[0].purchasable", is(true)));
	}

	private void assertBlockedAcrossPublicCartAndCheckout(GateFixture fixture) throws Exception {
		assertPublicVisibility(fixture, false);
		UserAccount customer = createCustomer(fixture.label() + "-blocked");

		addCartItem(customer.getId(), fixture.option().getId())
			.andExpect(status().isBadRequest());

		seedCart(customer, fixture);
		checkout(customer.getId())
			.andExpect(status().isBadRequest());
	}

	private void assertAllowedAcrossPublicCartAndCheckout(GateFixture fixture) throws Exception {
		if (fixture.product().getManagementChannel() == ProductManagementChannel.SUPPLIER_PORTAL
			&& fixture.option().getAvailableQuantity() == 0) {
			fixture.option().updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 10L);
			productOptionRepository.saveAndFlush(fixture.option());
		}
		assertPublicVisibility(fixture, true);
		UserAccount customer = createCustomer(fixture.label() + "-allowed");

		addCartItem(customer.getId(), fixture.option().getId())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.checkoutAvailable", is(true)));

		checkout(customer.getId())
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.orders", hasSize(1)))
			.andExpect(jsonPath("$.orders[0].items[0].productName", is(fixture.product().getName())));
	}

	private void assertPublicVisibility(GateFixture fixture, boolean visible) throws Exception {
		var list = mockMvc.perform(get("/api/products").param("q", fixture.product().getName()));
		var detail = mockMvc.perform(get("/api/products/{productId}", fixture.product().getId()));
		if (visible) {
				list.andExpect(status().isOk())
					.andExpect(jsonPath("$.products", hasSize(1)))
					.andExpect(jsonPath("$.products[0].id", is(fixture.product().getId().toString())))
					.andExpect(jsonPath("$.products[0].purchasable", is(true)));
				detail.andExpect(status().isOk())
					.andExpect(jsonPath("$.id", is(fixture.product().getId().toString())))
					.andExpect(jsonPath("$.purchasable", is(true)));
		} else {
			list.andExpect(status().isOk())
				.andExpect(jsonPath("$.products", hasSize(0)));
			detail.andExpect(status().isNotFound());
		}
	}

	private org.springframework.test.web.servlet.ResultActions addCartItem(UUID customerId, UUID optionId)
		throws Exception {
		return mockMvc.perform(post("/api/cart/items")
			.with(authentication(TestAuthentication.customer(customerId)))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "productOptionId": "%s",
				  "quantity": 1
				}
				""".formatted(optionId)));
	}

	private org.springframework.test.web.servlet.ResultActions checkout(UUID customerId) throws Exception {
		return mockMvc.perform(post("/api/checkouts")
			.with(authentication(TestAuthentication.customer(customerId)))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "recipientName": "Receiver",
				  "recipientPhone": "010-1111-2222",
				  "postalCode": "12345",
				  "address1": "Seoul test road",
				  "address2": "101"
				}
				"""));
	}

	private GateFixture portalFixture(String label, Supplier supplier, ProductReviewStatus reviewStatus) {
		return fixture(label, supplier, ProductManagementChannel.SUPPLIER_PORTAL, reviewStatus);
	}

	private GateFixture coreableFixture(String label) {
		return fixture(label, supplier(label), ProductManagementChannel.COREABLE, null);
	}

	private GateFixture fixture(
		String label,
		Supplier supplier,
		ProductManagementChannel channel,
		ProductReviewStatus reviewStatus
	) {
		Supplier savedSupplier = supplierRepository.save(supplier);
		Product product = new Product(
			savedSupplier,
			label + "-product",
			label + " summary",
			10_000,
			12_000,
			ProductCategory.PPE_SAFETY_HELMET,
			ProductStatus.ACTIVE,
			channel
		);
		if (reviewStatus == ProductReviewStatus.REVIEW_REQUIRED) {
			product.updateReview(reviewStatus, ProductReviewReasonCode.SAFETY_REVIEW, null);
		} else if (reviewStatus != null && reviewStatus != ProductReviewStatus.DRAFT) {
			product.updateReview(reviewStatus, null, null);
		}
		productRepository.save(product);
		ProductOption option = productOptionRepository.saveAndFlush(
			new ProductOption(product, "Default", 0, ProductOptionStatus.ACTIVE)
		);
		return new GateFixture(label, product, option);
	}

	private Supplier supplier(String label) {
		return new Supplier(label, null, null, label + "@supplier.example", null);
	}

	private Supplier verifiedSupplier(String label, Instant effectiveAt, Instant expiresAt) {
		Supplier supplier = supplier(label);
		supplier.verifyPortalContract("v1", effectiveAt, expiresAt, Instant.now(), ADMIN_ID);
		return supplier;
	}

	private UserAccount createCustomer(String label) {
		UserAccount customer = new UserAccount(
			SocialProvider.GOOGLE,
			label,
			label + "@example.com",
			label,
			UserRole.CUSTOMER
		);
		customer.updateProfile(label, label + "@example.com", "01011112222");
		userAccountRepository.save(customer);
		userPolicyAgreementRepository.save(new UserPolicyAgreement(
			customer,
			"2026-08-02",
			"2026-08-04",
			Instant.now()
		));
		return customer;
	}

	private void seedCart(UserAccount customer, GateFixture fixture) {
		Cart cart = cartRepository.findByUser_Id(customer.getId())
			.orElseGet(() -> cartRepository.save(new Cart(customer)));
		cartItemRepository.saveAndFlush(new CartItem(cart, fixture.product(), fixture.option(), 1));
	}

	private record GateFixture(String label, Product product, ProductOption option) {
	}
}
