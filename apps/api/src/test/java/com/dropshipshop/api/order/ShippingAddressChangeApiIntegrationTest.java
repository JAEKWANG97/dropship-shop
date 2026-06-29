package com.dropshipshop.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShippingAddressChangeApiIntegrationTest {

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
	private PaymentGroupRepository paymentGroupRepository;

	@Autowired
	private CustomerOrderRepository orderRepository;

	@Test
	void updatesCheckoutShippingAddressBeforePolicyConfirmation() throws Exception {
		UserAccount customer = createCustomer("shipping-change-customer-1");
		ProductOption option = createOption("Shipping Change Product", 12000);
		addCartItem(customer.getId(), option.getId(), 1);
		String checkoutNumber = createCheckout(customer.getId());

		mockMvc.perform(patch("/api/checkouts/{checkoutNumber}/shipping-address", checkoutNumber)
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(shippingAddressRequest("Changed Receiver", "010-9999-9999", "99999", "Changed address", "909")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkoutNumber", is(checkoutNumber)));

		PaymentGroup paymentGroup = paymentGroupRepository.findByCheckoutNumberAndUser_Id(checkoutNumber, customer.getId()).orElseThrow();
		CustomerOrder order = orderRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(paymentGroup.getId()).getFirst();
		assertThat(order.getRecipientName()).isEqualTo("Changed Receiver");
		assertThat(order.getRecipientPhone()).isEqualTo("010-9999-9999");
		assertThat(order.getPostalCode()).isEqualTo("99999");
		assertThat(order.getAddress1()).isEqualTo("Changed address");
		assertThat(order.getAddress2()).isEqualTo("909");
	}

	@Test
	void rejectsCheckoutShippingAddressChangeAfterPolicyConfirmationOrForOtherUser() throws Exception {
		UserAccount customer = createCustomer("shipping-change-customer-2");
		UserAccount other = createCustomer("shipping-change-customer-3");
		ProductOption option = createOption("Shipping Change Product B", 13000);
		addCartItem(customer.getId(), option.getId(), 1);
		String checkoutNumber = createCheckout(customer.getId());

		mockMvc.perform(patch("/api/checkouts/{checkoutNumber}/shipping-address", checkoutNumber)
				.with(authentication(TestAuthentication.customer(other.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(shippingAddressRequest("Other", "010-0000-0000", "00000", "Other address", null)))
			.andExpect(status().isNotFound());

		confirmPolicy(customer.getId(), checkoutNumber);

		mockMvc.perform(patch("/api/checkouts/{checkoutNumber}/shipping-address", checkoutNumber)
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(shippingAddressRequest("Changed", "010-9999-9999", "99999", "Changed address", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", is("Checkout policy confirmation is already completed")));
	}

	@Test
	void updatesOrderShippingAddressBeforeSupplierWorkAndRejectsLockedOrders() throws Exception {
		UserAccount customer = createCustomer("shipping-change-customer-4");
		UserAccount other = createCustomer("shipping-change-customer-5");
		CustomerOrder order = createSupplierOrderPendingOrder(customer, "SHIP-CHANGE-ORDER-1", "SHIP-CHANGE-CO-1");

		mockMvc.perform(patch("/api/orders/{orderId}/shipping-address", order.getId())
				.with(authentication(TestAuthentication.customer(other.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(shippingAddressRequest("Other", "010-0000-0000", "00000", "Other address", null)))
			.andExpect(status().isNotFound());

		mockMvc.perform(patch("/api/orders/{orderId}/shipping-address", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(shippingAddressRequest("Order Receiver", "010-8888-8888", "88888", "Order changed address", "808")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shippingAddress.recipientName", is("Order Receiver")))
			.andExpect(jsonPath("$.shippingAddress.recipientPhone", is("010-8888-8888")))
			.andExpect(jsonPath("$.shippingAddress.postalCode", is("88888")))
			.andExpect(jsonPath("$.shippingAddress.address1", is("Order changed address")))
			.andExpect(jsonPath("$.shippingAddress.address2", is("808")));

		CustomerOrder lockedOrder = createSupplierOrderPendingOrder(customer, "SHIP-CHANGE-ORDER-2", "SHIP-CHANGE-CO-2");
		lockedOrder.startSupplierOrderWork(TestAuthentication.ADMIN_ID, Instant.now());
		orderRepository.saveAndFlush(lockedOrder);

		mockMvc.perform(patch("/api/orders/{orderId}/shipping-address", lockedOrder.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(shippingAddressRequest("Locked", "010-7777-7777", "77777", "Locked address", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", is("Order address can be changed only before supplier work starts")));
	}

	@Test
	void rejectsOrderShippingAddressChangeAfterSupplierOrderedAndValidatesRequest() throws Exception {
		UserAccount customer = createCustomer("shipping-change-customer-6");
		CustomerOrder order = createSupplierOrderPendingOrder(customer, "SHIP-CHANGE-ORDER-3", "SHIP-CHANGE-CO-3");
		order.startSupplierOrderWork(TestAuthentication.ADMIN_ID, Instant.now());
		order.markSupplierOrdered();
		orderRepository.saveAndFlush(order);

		mockMvc.perform(patch("/api/orders/{orderId}/shipping-address", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(shippingAddressRequest("Ordered", "010-7777-7777", "77777", "Ordered address", null)))
			.andExpect(status().isBadRequest());

		CustomerOrder changeable = createSupplierOrderPendingOrder(customer, "SHIP-CHANGE-ORDER-4", "SHIP-CHANGE-CO-4");
		mockMvc.perform(patch("/api/orders/{orderId}/shipping-address", changeable.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(shippingAddressRequest("", "010-7777-7777", "77777", "Ordered address", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
	}

	private UserAccount createCustomer(String providerUserId) {
		UserAccount customer = userAccountRepository.save(new UserAccount(
			SocialProvider.GOOGLE,
			providerUserId,
			providerUserId + "@example.com",
			providerUserId,
			UserRole.CUSTOMER
		));
		customer.verifyPhone("01011112222", Instant.now());
		userAccountRepository.save(customer);
		userPolicyAgreementRepository.save(new UserPolicyAgreement(
			customer,
			"terms-2026-06-01",
			"privacy-2026-06-01",
			Instant.now()
		));
		return customer;
	}

	private ProductOption createOption(String productName, long basePrice) {
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
			ProductStatus.ACTIVE
		));
		return productOptionRepository.saveAndFlush(new ProductOption(product, "Default", 0, ProductOptionStatus.ACTIVE));
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

	private String createCheckout(UUID userId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(userId)))
				.contentType(MediaType.APPLICATION_JSON)
				.content(shippingAddressRequest("Receiver", "010-1111-2222", "12345", "Seoul test road", "101")))
			.andExpect(status().isCreated())
			.andReturn();
		return fieldFrom(result, "checkoutNumber");
	}

	private void confirmPolicy(UUID userId, String checkoutNumber) throws Exception {
		mockMvc.perform(post("/api/checkouts/{checkoutNumber}/policy-confirmation", checkoutNumber)
				.with(authentication(TestAuthentication.customer(userId)))
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
			.andExpect(status().isOk());
	}

	private CustomerOrder createSupplierOrderPendingOrder(UserAccount customer, String orderNumber, String checkoutNumber) {
		Supplier supplier = supplierRepository.save(new Supplier(
			orderNumber + " Supplier",
			"Manager",
			"010-0000-0000",
			orderNumber + "@supplier.example",
			null
		));
		PaymentGroup paymentGroup = paymentGroupRepository.save(new PaymentGroup(
			checkoutNumber,
			customer,
			10000,
			Instant.now().plusSeconds(1800)
		));
		paymentGroup.approve(10000, Instant.now());
		CustomerOrder order = new CustomerOrder(
			orderNumber,
			customer,
			supplier,
			paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul test road", "101"),
			10000,
			paymentGroup.getExpiresAt()
		);
		order.markSupplierOrderPending();
		return orderRepository.saveAndFlush(order);
	}

	private String shippingAddressRequest(
		String recipientName,
		String recipientPhone,
		String postalCode,
		String address1,
		String address2
	) {
		return """
			{
			  "recipientName": "%s",
			  "recipientPhone": "%s",
			  "postalCode": "%s",
			  "address1": "%s",
			  "address2": %s
			}
			""".formatted(
			recipientName,
			recipientPhone,
			postalCode,
			address1,
			address2 == null ? "null" : "\"" + address2 + "\""
		);
	}

	private String fieldFrom(MvcResult result, String fieldName) throws Exception {
		String json = result.getResponse().getContentAsString();
		String key = "\"" + fieldName + "\":\"";
		int keyIndex = json.indexOf(key);
		int start = keyIndex + key.length();
		int end = json.indexOf('"', start);
		return json.substring(start, end);
	}
}
