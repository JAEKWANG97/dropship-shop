package com.dropshipshop.api.checkout;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dropshipshop.api.account.domain.UserPolicyAgreement;
import com.dropshipshop.api.account.repository.UserPolicyAgreementRepository;
import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductNotice;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
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
class CheckoutApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private UserPolicyAgreementRepository userPolicyAgreementRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductOptionRepository productOptionRepository;

	@Autowired
	private ProductNoticeRepository productNoticeRepository;

	@Test
	void rejectsAnonymousAndAdminCheckoutCreation() throws Exception {
		mockMvc.perform(post("/api/checkouts")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCheckoutRequest()))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCheckoutRequest()))
			.andExpect(status().isForbidden());
	}

	@Test
	void createsPaymentGroupAndSupplierGroupedOrdersFromCart() throws Exception {
		UserAccount customer = createCustomer("checkout-customer-1");
		ProductOption optionA = createOption("Checkout Product A", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE, 39000, 1000, 2);
		ProductOption optionB = createOption("Checkout Product B", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE, 59000, 2000, 1);
		addCartItem(customer.getId(), optionA.getId(), 2);
		addCartItem(customer.getId(), optionB.getId(), 1);

		MvcResult result = mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "recipientName": "Receiver",
					  "recipientPhone": "010-1111-2222",
					  "postalCode": "12345",
					  "address1": "Seoul test road",
					  "address2": "101",
					  "clientSubmittedTotalAmount": 1
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.checkoutNumber", matchesPattern("CO[0-9]{12}")))
			.andExpect(jsonPath("$.status", is("PAYMENT_PENDING")))
			.andExpect(jsonPath("$.totalAmount", is(141000)))
			.andExpect(jsonPath("$.refundableAmount", is(141000)))
			.andExpect(jsonPath("$.orders", hasSize(2)))
			.andExpect(jsonPath("$.orders[0].status", is("PAYMENT_PENDING")))
			.andExpect(jsonPath("$.orders[0].subtotalAmount", is(80000)))
			.andExpect(jsonPath("$.orders[0].shippingFee", is(0)))
			.andExpect(jsonPath("$.orders[0].items", hasSize(1)))
			.andExpect(jsonPath("$.orders[0].items[0].productName", is("Checkout Product A")))
			.andExpect(jsonPath("$.orders[0].items[0].optionName", is("Default")))
			.andExpect(jsonPath("$.orders[0].items[0].quantity", is(2)))
			.andExpect(jsonPath("$.orders[0].items[0].unitPrice", is(40000)))
			.andExpect(jsonPath("$.orders[0].items[0].lineAmount", is(80000)))
			.andExpect(jsonPath("$.orders[0].items[0].productDetailVersion", is(1)))
			.andExpect(jsonPath("$.orders[0].items[0].productNoticeVersion", is(2)))
			.andExpect(jsonPath("$.orders[1].subtotalAmount", is(61000)))
			.andReturn();

		String checkoutNumber = checkoutNumberFrom(result);

		mockMvc.perform(get("/api/cart")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(0)));

		mockMvc.perform(get("/api/checkouts/{checkoutNumber}", checkoutNumber)
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkoutNumber", is(checkoutNumber)))
			.andExpect(jsonPath("$.orders", hasSize(2)));

		mockMvc.perform(post("/api/checkouts/{checkoutNumber}/policy-confirmation", checkoutNumber)
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "termsVersion": "terms-2026-06-01",
					  "privacyVersion": "privacy-2026-06-01",
					  "orderPolicyVersion": "order-2026-06-01",
					  "cancellationRefundPolicyVersion": "refund-2026-06-01",
					  "outOfStockNoticeVersion": "out-of-stock-2026-06-01",
					  "confirmedNoticeText": "I agree to the checkout policies."
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkoutNumber", is(checkoutNumber)));

		mockMvc.perform(get("/api/checkouts/{checkoutNumber}", checkoutNumber)
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policyConfirmedAt").exists());
	}

	@Test
	void rejectsEmptyCartInvalidAddressUnsellableCartAndOtherCustomerCheckoutAccess() throws Exception {
		UserAccount owner = createCustomer("checkout-customer-2");
		UserAccount other = createCustomer("checkout-customer-3");

		mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(owner.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCheckoutRequest()))
			.andExpect(status().isBadRequest());

		ProductOption option = createOption("Checkout Product C", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE, 10000, 0, 1);
		addCartItem(owner.getId(), option.getId(), 1);

		mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(owner.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "recipientName": "",
					  "recipientPhone": "010-1111-2222",
					  "postalCode": "12345",
					  "address1": "Seoul test road"
					}
					"""))
			.andExpect(status().isBadRequest());

		option.updateStatus(ProductOptionStatus.SOLD_OUT);
		productOptionRepository.saveAndFlush(option);

		mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(owner.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCheckoutRequest()))
			.andExpect(status().isBadRequest());

		option.updateStatus(ProductOptionStatus.ACTIVE);
		productOptionRepository.saveAndFlush(option);

		String checkoutNumber = checkoutNumberFrom(mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(owner.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCheckoutRequest()))
			.andExpect(status().isCreated())
			.andReturn());

		mockMvc.perform(get("/api/checkouts/{checkoutNumber}", checkoutNumber)
				.with(authentication(TestAuthentication.customer(other.getId()))))
			.andExpect(status().isNotFound());
	}

	private UserAccount createCustomer(String providerUserId) {
		UserAccount customer = userAccountRepository.save(new UserAccount(
			SocialProvider.GOOGLE,
			providerUserId,
			providerUserId + "@example.com",
			providerUserId,
			UserRole.CUSTOMER
		));
		userPolicyAgreementRepository.save(new UserPolicyAgreement(
			customer,
			"terms-2026-06-01",
			"privacy-2026-06-01",
			Instant.now()
		));
		return customer;
	}

	private ProductOption createOption(
		String productName,
		ProductStatus productStatus,
		ProductOptionStatus optionStatus,
		long basePrice,
		long additionalPrice,
		int noticeVersion
	) {
		Supplier supplier = supplierRepository.save(new Supplier(
			productName + " Supplier",
			"Manager",
			"010-0000-0000",
			productName + "@supplier.example",
			null
		));
		Product product = productRepository.save(new Product(
			supplier,
			productName,
			productName + " Summary",
			basePrice,
			productStatus
		));
		productNoticeRepository.save(new ProductNotice(
			product,
			noticeVersion,
			"Product info",
			"Shipping info",
			"AS info",
			"Return exchange info"
		));
		return productOptionRepository.saveAndFlush(new ProductOption(product, "Default", additionalPrice, optionStatus));
	}

	private void addCartItem(UUID userId, UUID productOptionId, int quantity) throws Exception {
		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer(userId)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "productOptionId": "%s",
					  "quantity": %d
					}
					""".formatted(productOptionId, quantity)))
			.andExpect(status().isCreated());
	}

	private String validCheckoutRequest() {
		return """
			{
			  "recipientName": "Receiver",
			  "recipientPhone": "010-1111-2222",
			  "postalCode": "12345",
			  "address1": "Seoul test road",
			  "address2": "101"
			}
			""";
	}

	private String checkoutNumberFrom(MvcResult result) throws Exception {
		String json = result.getResponse().getContentAsString();
		int keyIndex = json.indexOf("\"checkoutNumber\":\"");
		int start = keyIndex + "\"checkoutNumber\":\"".length();
		int end = json.indexOf('"', start);
		return json.substring(start, end);
	}
}
