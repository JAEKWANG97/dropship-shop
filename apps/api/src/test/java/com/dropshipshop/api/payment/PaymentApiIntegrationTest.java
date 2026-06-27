package com.dropshipshop.api.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
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
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.domain.PaymentMethod;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.payment.toss.TossCancelledPayment;
import com.dropshipshop.api.payment.toss.TossApprovedPayment;
import com.dropshipshop.api.payment.toss.TossPaymentsClient;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private FakeTossPaymentsClient fakeTossPaymentsClient;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductOptionRepository productOptionRepository;

	@Autowired
	private PaymentGroupRepository paymentGroupRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private CustomerOrderRepository orderRepository;

	@BeforeEach
	void resetFakeClient() {
		fakeTossPaymentsClient.reset();
	}

	@Test
	void confirmsTossPaymentAndHandlesDuplicateConfirmIdempotently() throws Exception {
		UserAccount customer = createCustomer("payment-customer-1");
		ProductOption option = createOption("Payment Product A", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE, 39000, 1000);
		addCartItem(customer.getId(), option.getId(), 2);
		String checkoutNumber = createCheckout(customer.getId());
		confirmPolicy(customer.getId(), checkoutNumber);

		MvcResult result = mockMvc.perform(post("/api/payments/toss/confirm")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(confirmRequest(checkoutNumber, "pay-success-1", 80000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.checkoutNumber", is(checkoutNumber)))
			.andExpect(jsonPath("$.paymentStatus", is("APPROVED")))
			.andExpect(jsonPath("$.paymentGroupStatus", is("APPROVED")))
			.andExpect(jsonPath("$.approvedAmount", is(80000)))
			.andExpect(jsonPath("$.orders", hasSize(1)))
			.andExpect(jsonPath("$.orders[0].status", is("SUPPLIER_ORDER_PENDING")))
			.andReturn();

		UUID paymentId = paymentIdFrom(result);

		mockMvc.perform(post("/api/payments/toss/confirm")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(confirmRequest(checkoutNumber, "pay-success-1", 80000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentId", is(paymentId.toString())))
			.andExpect(jsonPath("$.paymentStatus", is("APPROVED")));

		assertThat(fakeTossPaymentsClient.confirmCalls).isEqualTo(1);
		assertThat(paymentRepository.count()).isEqualTo(1);
	}

	@Test
	void rejectsAmountMismatchFromTossAndMovesCheckoutToPaymentException() throws Exception {
		UserAccount customer = createCustomer("payment-customer-2");
		ProductOption option = createOption("Payment Product B", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE, 10000, 0);
		addCartItem(customer.getId(), option.getId(), 1);
		String checkoutNumber = createCheckout(customer.getId());
		confirmPolicy(customer.getId(), checkoutNumber);
		fakeTossPaymentsClient.nextApprovedAmount = 9000L;

		mockMvc.perform(post("/api/payments/toss/confirm")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(confirmRequest(checkoutNumber, "pay-mismatch-1", 10000)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/checkouts/{checkoutNumber}", checkoutNumber)
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("PAYMENT_EXCEPTION")))
			.andExpect(jsonPath("$.orders[0].status", is("PAYMENT_EXCEPTION")));
	}

	@Test
	void rejectsExpiredPolicyMissingAndUnsellableCheckoutConfirmation() throws Exception {
		UserAccount customer = createCustomer("payment-customer-3");
		ProductOption option = createOption("Payment Product C", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE, 10000, 0);
		addCartItem(customer.getId(), option.getId(), 1);
		String checkoutWithoutPolicy = createCheckout(customer.getId());

		mockMvc.perform(post("/api/payments/toss/confirm")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(confirmRequest(checkoutWithoutPolicy, "pay-policy-missing", 10000)))
			.andExpect(status().isBadRequest());

		addCartItem(customer.getId(), option.getId(), 1);
		String unsellableCheckout = createCheckout(customer.getId());
		confirmPolicy(customer.getId(), unsellableCheckout);
		option.updateStatus(ProductOptionStatus.SOLD_OUT);
		productOptionRepository.saveAndFlush(option);

		mockMvc.perform(post("/api/payments/toss/confirm")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(confirmRequest(unsellableCheckout, "pay-unsellable", 10000)))
			.andExpect(status().isBadRequest());

		PaymentGroup expired = createExpiredPaymentGroup(customer);
		mockMvc.perform(post("/api/payments/toss/confirm")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(confirmRequest(expired.getCheckoutNumber(), "pay-expired", 10000)))
			.andExpect(status().isBadRequest());

		assertThat(paymentGroupRepository.findById(expired.getId()).orElseThrow().getStatus().name()).isEqualTo("EXPIRED");
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

	private ProductOption createOption(
		String productName,
		ProductStatus productStatus,
		ProductOptionStatus optionStatus,
		long basePrice,
		long additionalPrice
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

	private String createCheckout(UUID userId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/checkouts")
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

	private PaymentGroup createExpiredPaymentGroup(UserAccount customer) {
		Supplier supplier = supplierRepository.save(new Supplier(
			"Expired Supplier",
			"Manager",
			"010-0000-0000",
			"expired@supplier.example",
			null
		));
		PaymentGroup paymentGroup = paymentGroupRepository.save(new PaymentGroup(
			"COEXPIRED0001",
			customer,
			10000,
			Instant.now().minusSeconds(60)
		));
		orderRepository.save(new CustomerOrder(
			"ODEXPIRED0001",
			customer,
			supplier,
			paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul test road", "101"),
			10000,
			paymentGroup.getExpiresAt()
		));
		return paymentGroup;
	}

	private String confirmRequest(String checkoutNumber, String paymentKey, long amount) {
		return """
			{
			  "checkoutNumber": "%s",
			  "paymentKey": "%s",
			  "amount": %d
			}
			""".formatted(checkoutNumber, paymentKey, amount);
	}

	private UUID paymentIdFrom(MvcResult result) throws Exception {
		return UUID.fromString(fieldFrom(result, "paymentId"));
	}

	private String fieldFrom(MvcResult result, String fieldName) throws Exception {
		String json = result.getResponse().getContentAsString();
		String key = "\"" + fieldName + "\":\"";
		int keyIndex = json.indexOf(key);
		int start = keyIndex + key.length();
		int end = json.indexOf('"', start);
		return json.substring(start, end);
	}

	@TestConfiguration
	static class FakeTossPaymentsConfiguration {

		@Bean
		@Primary
		FakeTossPaymentsClient fakeTossPaymentsClient() {
			return new FakeTossPaymentsClient();
		}
	}

	static class FakeTossPaymentsClient implements TossPaymentsClient {

		private Long nextApprovedAmount;
		private int confirmCalls;

		@Override
		public TossApprovedPayment confirm(String paymentKey, String orderId, long amount) {
			confirmCalls += 1;
			long approvedAmount = nextApprovedAmount == null ? amount : nextApprovedAmount;
			return new TossApprovedPayment(
				paymentKey,
				orderId,
				approvedAmount,
				PaymentMethod.CARD,
				Instant.now(),
				"DONE"
			);
		}

		@Override
		public TossCancelledPayment cancel(String paymentKey, String cancelReason, long cancelAmount, String idempotencyKey) {
			return new TossCancelledPayment(paymentKey, "cancel-order", cancelAmount, 0, "cancel-" + paymentKey, "CANCELED");
		}

		void reset() {
			nextApprovedAmount = null;
			confirmCalls = 0;
		}
	}
}
