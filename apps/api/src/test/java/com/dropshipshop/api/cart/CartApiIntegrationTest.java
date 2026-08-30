package com.dropshipshop.api.cart;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CartApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductOptionRepository productOptionRepository;

	@Test
	void rejectsAnonymousAndAdminAccess() throws Exception {
		mockMvc.perform(get("/api/cart"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/cart")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isForbidden());
	}

	@Test
	void managesCustomerCartAndValidatesSellabilityBeforeCheckout() throws Exception {
		UserAccount customer = createCustomer("cart-customer-1");
		ProductOption option = createOption("Product A", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE);

		mockMvc.perform(get("/api/cart")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(0)))
			.andExpect(jsonPath("$.salesEnabled", is(true)))
			.andExpect(jsonPath("$.checkoutAvailable", is(false)))
			.andExpect(jsonPath("$.issues[0].code", is("EMPTY_CART")));

		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "productOptionId": "%s",
					  "quantity": 2
					}
					""".formatted(option.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].quantity", is(2)))
			.andExpect(jsonPath("$.items[0].unitPrice", is(40000)))
			.andExpect(jsonPath("$.items[0].lineAmount", is(80000)))
			.andExpect(jsonPath("$.checkoutAvailable", is(true)));

		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "productOptionId": "%s",
					  "quantity": 3
					}
					""".formatted(option.getId())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].quantity", is(5)))
			.andExpect(jsonPath("$.subtotalAmount", is(200000)));

		UUID cartItemId = currentCartItemId(customer.getId());

		mockMvc.perform(patch("/api/cart/items/{cartItemId}", cartItemId)
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "quantity": 4
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].quantity", is(4)))
			.andExpect(jsonPath("$.subtotalAmount", is(160000)));

		mockMvc.perform(post("/api/cart/validate")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkoutAvailable", is(true)))
			.andExpect(jsonPath("$.issues", hasSize(0)));

		option.updateStatus(ProductOptionStatus.SOLD_OUT);
		productOptionRepository.saveAndFlush(option);

		mockMvc.perform(get("/api/cart")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkoutAvailable", is(false)))
			.andExpect(jsonPath("$.items[0].sellable", is(false)))
			.andExpect(jsonPath("$.items[0].unavailableReason", is(
				"현재 선택한 옵션은 판매가 중지되었습니다. 삭제 후 다른 옵션을 선택해 주세요."
			)));

		mockMvc.perform(post("/api/cart/validate")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkoutAvailable", is(false)))
			.andExpect(jsonPath("$.issues[0].cartItemId", is(cartItemId.toString())))
			.andExpect(jsonPath("$.issues[0].message", is(
				"현재 선택한 옵션은 판매가 중지되었습니다. 삭제 후 다른 옵션을 선택해 주세요."
			)));

		option.updateStatus(ProductOptionStatus.ACTIVE);
		productOptionRepository.saveAndFlush(option);
		option.getProduct().updateStatus(ProductStatus.HIDDEN);
		productRepository.saveAndFlush(option.getProduct());

		mockMvc.perform(get("/api/cart")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkoutAvailable", is(false)))
			.andExpect(jsonPath("$.items[0].sellable", is(false)))
			.andExpect(jsonPath("$.items[0].unavailableReason", is(
				"판매가 중지된 상품입니다. 삭제 후 주문해 주세요."
			)));

		mockMvc.perform(delete("/api/cart/items/{cartItemId}", cartItemId)
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/cart")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(0)))
			.andExpect(jsonPath("$.checkoutAvailable", is(false)));
	}

	@Test
	void rejectsInvalidQuantityUnsellableAddAndOtherUserCartItemAccess() throws Exception {
		UserAccount owner = createCustomer("cart-customer-2");
		UserAccount other = createCustomer("cart-customer-3");
		ProductOption option = createOption("Product B", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE);

		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer(owner.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "productOptionId": "%s",
					  "quantity": 0
					}
					""".formatted(option.getId())))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer(owner.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "productOptionId": "%s",
					  "quantity": 100
					}
					""".formatted(option.getId())))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer(owner.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "productOptionId": "%s",
					  "quantity": 99
					}
					""".formatted(option.getId())))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer(owner.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "productOptionId": "%s",
					  "quantity": 1
					}
					""".formatted(option.getId())))
			.andExpect(status().isBadRequest());

		UUID ownerCartItemId = currentCartItemId(owner.getId());

		mockMvc.perform(patch("/api/cart/items/{cartItemId}", ownerCartItemId)
				.with(authentication(TestAuthentication.customer(other.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "quantity": 1
					}
					"""))
			.andExpect(status().isNotFound());

		ProductOption hiddenOption = createOption("Product C", ProductStatus.HIDDEN, ProductOptionStatus.ACTIVE);
		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer(owner.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "productOptionId": "%s",
					  "quantity": 1
					}
					""".formatted(hiddenOption.getId())))
			.andExpect(status().isBadRequest());
	}

	@Test
	void enforcesMinimumAndStepForAddCombinedQuantityUpdateAndSavedCart() throws Exception {
		UserAccount customer = createCustomer("cart-moq-customer");
		ProductOption option = createOption("MOQ Product", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE, 6, 6);

		addItem(customer.getId(), option.getId(), 1)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", is("현재 수량은 1개입니다. 최소 6개부터 주문할 수 있습니다.")));

		addItem(customer.getId(), option.getId(), 7)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", is("현재 수량은 7개입니다. 6개 단위로 주문할 수 있습니다.")));

		addItem(customer.getId(), option.getId(), 6)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.items[0].quantity", is(6)))
			.andExpect(jsonPath("$.items[0].minimumOrderQuantity", is(6)))
			.andExpect(jsonPath("$.items[0].orderQuantityStep", is(6)))
			.andExpect(jsonPath("$.checkoutAvailable", is(true)));

		addItem(customer.getId(), option.getId(), 6)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.items[0].quantity", is(12)));

		addItem(customer.getId(), option.getId(), 1)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", is("현재 수량은 13개입니다. 6개 단위로 주문할 수 있습니다.")));

		UUID cartItemId = currentCartItemId(customer.getId());
		mockMvc.perform(patch("/api/cart/items/{cartItemId}", cartItemId)
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "quantity": 18
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].quantity", is(18)));

		option.getProduct().updateOrderQuantityRules(8, 8);
		productRepository.saveAndFlush(option.getProduct());

		mockMvc.perform(get("/api/cart")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkoutAvailable", is(false)))
			.andExpect(jsonPath("$.items[0].quantity", is(18)))
			.andExpect(jsonPath("$.items[0].minimumOrderQuantity", is(8)))
			.andExpect(jsonPath("$.items[0].orderQuantityStep", is(8)))
			.andExpect(jsonPath("$.items[0].sellable", is(false)))
			.andExpect(jsonPath("$.items[0].unavailableReason", is(
				"현재 수량은 18개입니다. 8개 단위로 주문할 수 있습니다."
			)))
			.andExpect(jsonPath("$.issues[0].code", is("INVALID_ORDER_QUANTITY")));
	}

	@Test
	void keepsAnUnsellableZeroPricedSavedItemReadable() throws Exception {
		UserAccount customer = createCustomer("cart-zero-price");
		ProductOption option = createOption("Zeroed Product", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE);
		addItem(customer.getId(), option.getId(), 1).andExpect(status().isCreated());

		option.getProduct().updateStatus(ProductStatus.HIDDEN);
		option.getProduct().updateSourcePricing(0, 0);
		option.update("Default", 0);
		productRepository.saveAndFlush(option.getProduct());
		productOptionRepository.saveAndFlush(option);

		mockMvc.perform(get("/api/cart")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.subtotalAmount", is(0)))
			.andExpect(jsonPath("$.checkoutAvailable", is(false)))
			.andExpect(jsonPath("$.items[0].unitPrice", is(0)))
			.andExpect(jsonPath("$.items[0].lineAmount", is(0)))
			.andExpect(jsonPath("$.items[0].sellable", is(false)));
	}

	private UserAccount createCustomer(String providerUserId) {
		return userAccountRepository.save(new UserAccount(
			SocialProvider.GOOGLE,
			providerUserId,
			providerUserId + "@example.com",
			providerUserId,
			UserRole.CUSTOMER
		));
	}

	private ProductOption createOption(String productName, ProductStatus productStatus, ProductOptionStatus optionStatus) {
		return createOption(productName, productStatus, optionStatus, 1, 1);
	}

	private ProductOption createOption(
		String productName,
		ProductStatus productStatus,
		ProductOptionStatus optionStatus,
		int minimumOrderQuantity,
		int orderQuantityStep
	) {
		Supplier supplier = supplierRepository.save(new Supplier(
			productName + " Supplier",
			"Manager",
			"010-0000-0000",
			productName + "@supplier.example",
			null
		));
		Product product = new Product(
			supplier,
			productName,
			productName + " Summary",
			39000,
			productStatus
		);
		product.updateOrderQuantityRules(minimumOrderQuantity, orderQuantityStep);
		productRepository.save(product);
		return productOptionRepository.saveAndFlush(new ProductOption(product, "Default", 1000, optionStatus));
	}

	private org.springframework.test.web.servlet.ResultActions addItem(UUID userId, UUID productOptionId, int quantity)
		throws Exception {
		return mockMvc.perform(post("/api/cart/items")
			.with(authentication(TestAuthentication.customer(userId)))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "productOptionId": "%s",
				  "quantity": %d
				}
				""".formatted(productOptionId, quantity)));
	}

	private UUID currentCartItemId(UUID userId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/cart")
				.with(authentication(TestAuthentication.customer(userId))))
			.andExpect(status().isOk())
			.andReturn();
		String json = result.getResponse().getContentAsString();
		int idKeyIndex = json.indexOf("\"id\":\"");
		int idStart = idKeyIndex + "\"id\":\"".length();
		int idEnd = json.indexOf('"', idStart);
		return UUID.fromString(json.substring(idStart, idEnd));
	}
}
