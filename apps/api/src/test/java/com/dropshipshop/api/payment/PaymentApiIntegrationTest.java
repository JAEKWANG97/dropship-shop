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
import java.util.ArrayList;
import java.util.List;
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
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.domain.PaymentMethod;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.payment.repository.PaymentEventRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.payment.toss.TossCancelledPayment;
import com.dropshipshop.api.payment.toss.TossApprovedPayment;
import com.dropshipshop.api.payment.toss.TossPaymentException;
import com.dropshipshop.api.payment.toss.TossPaymentSnapshot;
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
	private PaymentEventRepository paymentEventRepository;

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
		assertThat(paymentRepository.findByProviderPaymentKey("pay-success-1")).isPresent();
	}

	@Test
	void autoCancelsAmountMismatchFromTossAndHandlesDuplicateConfirmIdempotently() throws Exception {
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
			.andExpect(jsonPath("$.status", is("CANCELLED")))
			.andExpect(jsonPath("$.orders[0].status", is("CANCELLED")));

		MvcResult duplicateResult = mockMvc.perform(post("/api/payments/toss/confirm")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(confirmRequest(checkoutNumber, "pay-mismatch-1", 10000)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentStatus", is("CANCELLED")))
			.andExpect(jsonPath("$.paymentGroupStatus", is("CANCELLED")))
			.andExpect(jsonPath("$.orders[0].status", is("CANCELLED")))
			.andReturn();

		UUID paymentId = paymentIdFrom(duplicateResult);

		mockMvc.perform(get("/api/admin/payment-exceptions")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')]".formatted(checkoutNumber), hasSize(0)));

		assertThat(fakeTossPaymentsClient.confirmCalls).isEqualTo(1);
		assertThat(fakeTossPaymentsClient.cancelCalls).isEqualTo(1);
		assertThat(fakeTossPaymentsClient.cancelAmounts).containsExactly(9000L);
		assertThat(fakeTossPaymentsClient.cancelIdempotencyKeys)
			.containsExactly("payment-exception-cancel-" + paymentId);
		assertThat(paymentRepository.findByProviderPaymentKey("pay-mismatch-1")).isPresent();
	}

	@Test
	void listsFailedPaymentExceptionCancelAndAllowsAdminRetry() throws Exception {
		UserAccount customer = createCustomer("payment-customer-3");
		ProductOption option = createOption("Payment Product C", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE, 10000, 0);
		addCartItem(customer.getId(), option.getId(), 1);
		String checkoutNumber = createCheckout(customer.getId());
		confirmPolicy(customer.getId(), checkoutNumber);
		fakeTossPaymentsClient.nextApprovedAmount = 9000L;
		fakeTossPaymentsClient.failNextCancel = true;

		mockMvc.perform(post("/api/payments/toss/confirm")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(confirmRequest(checkoutNumber, "pay-mismatch-retry-1", 10000)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/admin/payment-exceptions")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')]".formatted(checkoutNumber), hasSize(1)))
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')].paymentStatus".formatted(checkoutNumber), is(List.of("CANCEL_FAILED"))))
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')].paymentGroupStatus".formatted(checkoutNumber), is(List.of("CANCEL_FAILED"))))
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')].exceptionReason".formatted(checkoutNumber), is(List.of("AMOUNT_MISMATCH"))))
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')].requestedAmount".formatted(checkoutNumber), is(List.of(10000))))
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')].approvedAmount".formatted(checkoutNumber), is(List.of(9000))))
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')].failureCode".formatted(checkoutNumber), is(List.of("TOSS_CANCEL_FAILED"))));

		UUID paymentId = paymentRepository.findByProviderPaymentKey("pay-mismatch-retry-1").orElseThrow().getId();
		String firstIdempotencyKey = fakeTossPaymentsClient.cancelIdempotencyKeys.get(0);

		mockMvc.perform(get("/api/admin/payment-exceptions")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/admin/payments/{paymentId}/retry-cancel", paymentId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentStatus", is("CANCELLED")))
			.andExpect(jsonPath("$.paymentGroupStatus", is("CANCELLED")))
			.andExpect(jsonPath("$.providerCancelTransactionKey", is("cancel-pay-mismatch-retry-1")));

		mockMvc.perform(get("/api/checkouts/{checkoutNumber}", checkoutNumber)
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("CANCELLED")))
			.andExpect(jsonPath("$.orders[0].status", is("CANCELLED")));

		mockMvc.perform(get("/api/admin/payment-exceptions")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')]".formatted(checkoutNumber), hasSize(0)));

		assertThat(fakeTossPaymentsClient.cancelCalls).isEqualTo(2);
		assertThat(fakeTossPaymentsClient.cancelAmounts).containsExactly(9000L, 9000L);
		assertThat(fakeTossPaymentsClient.cancelIdempotencyKeys)
			.containsExactly(firstIdempotencyKey, firstIdempotencyKey);
	}

	@Test
	void rejectsExpiredPolicyMissingAndUnsellableCheckoutConfirmation() throws Exception {
		UserAccount customer = createCustomer("payment-customer-4");
		ProductOption option = createOption("Payment Product D", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE, 10000, 0);
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

	@Test
	void storesDuplicateTossWebhookOnlyOnce() throws Exception {
		UserAccount customer = createCustomer("payment-customer-5");
		ProductOption option = createOption("Payment Product E", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE, 10000, 0);
		addCartItem(customer.getId(), option.getId(), 1);
		String checkoutNumber = createCheckout(customer.getId());
		confirmPolicy(customer.getId(), checkoutNumber);

		mockMvc.perform(post("/api/payments/toss/confirm")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(confirmRequest(checkoutNumber, "pay-webhook-dup-1", 10000)))
			.andExpect(status().isOk());

		String webhookBody = tossWebhook("PAYMENT_STATUS_CHANGED", "pay-webhook-dup-1", "DONE");
		mockMvc.perform(post("/api/payments/toss/webhook")
				.header("TossPayments-Webhook-Transmission-Id", "wh-dup-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(webhookBody))
			.andExpect(status().isAccepted());

		mockMvc.perform(post("/api/payments/toss/webhook")
				.header("TossPayments-Webhook-Transmission-Id", "wh-dup-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(webhookBody))
			.andExpect(status().isAccepted());

		assertThat(paymentEventRepository.countByIdempotencyKey("toss-webhook:wh-dup-1")).isEqualTo(1);
		assertThat(fakeTossPaymentsClient.lookupCalls).isEqualTo(1);
	}

	@Test
	void acceptsVerifiedTossWebhookForUnknownPaymentKeyWithoutLocalEvent() throws Exception {
		mockMvc.perform(post("/api/payments/toss/webhook")
				.header("TossPayments-Webhook-Transmission-Id", "wh-unknown-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(tossWebhook("PAYMENT_STATUS_CHANGED", "unknown-payment-key", "DONE")))
			.andExpect(status().isAccepted());

		assertThat(paymentRepository.findByProviderPaymentKey("unknown-payment-key")).isEmpty();
		assertThat(paymentEventRepository.countByIdempotencyKey("toss-webhook:wh-unknown-1")).isZero();
		assertThat(fakeTossPaymentsClient.lookupCalls).isEqualTo(1);
	}

	@Test
	void movesPaymentToReviewRequiredWhenTossWebhookConflictsWithLocalState() throws Exception {
		UserAccount customer = createCustomer("payment-customer-6");
		ProductOption option = createOption("Payment Product F", ProductStatus.ACTIVE, ProductOptionStatus.ACTIVE, 10000, 0);
		addCartItem(customer.getId(), option.getId(), 1);
		String checkoutNumber = createCheckout(customer.getId());
		confirmPolicy(customer.getId(), checkoutNumber);

		mockMvc.perform(post("/api/payments/toss/confirm")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(confirmRequest(checkoutNumber, "pay-webhook-conflict-1", 10000)))
			.andExpect(status().isOk());

		fakeTossPaymentsClient.lookupStatus = "CANCELED";
		mockMvc.perform(post("/api/payments/toss/webhook")
				.header("TossPayments-Webhook-Transmission-Id", "wh-conflict-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(tossWebhook("PAYMENT_STATUS_CHANGED", "pay-webhook-conflict-1", "CANCELED")))
			.andExpect(status().isAccepted());

		mockMvc.perform(get("/api/admin/payment-exceptions")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')]".formatted(checkoutNumber), hasSize(1)))
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')].paymentStatus".formatted(checkoutNumber), is(List.of("REVIEW_REQUIRED"))))
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')].paymentGroupStatus".formatted(checkoutNumber), is(List.of("APPROVED"))))
			.andExpect(jsonPath("$.exceptions[?(@.checkoutNumber == '%s')].failureCode".formatted(checkoutNumber), is(List.of("WEBHOOK_STATUS_CONFLICT"))));

		assertThat(paymentEventRepository.countByIdempotencyKey("toss-webhook:wh-conflict-1")).isEqualTo(1);
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

	private String tossWebhook(String eventType, String paymentKey, String status) {
		return """
			{
			  "eventType": "%s",
			  "createdAt": "2026-06-28T00:00:00Z",
			  "data": {
			    "paymentKey": "%s",
			    "status": "%s"
			  }
			}
			""".formatted(eventType, paymentKey, status);
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
		private boolean failNextCancel;
		private int confirmCalls;
		private int cancelCalls;
		private int lookupCalls;
		private String lookupStatus = "DONE";
		private final List<Long> cancelAmounts = new ArrayList<>();
		private final List<String> cancelIdempotencyKeys = new ArrayList<>();

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
			cancelCalls += 1;
			cancelAmounts.add(cancelAmount);
			cancelIdempotencyKeys.add(idempotencyKey);
			if (failNextCancel) {
				failNextCancel = false;
				throw new TossPaymentException("Toss cancel failed");
			}
			return new TossCancelledPayment(paymentKey, "cancel-order", cancelAmount, 0, "cancel-" + paymentKey, "CANCELED");
		}

		@Override
		public TossPaymentSnapshot getPayment(String paymentKey) {
			lookupCalls += 1;
			return new TossPaymentSnapshot(paymentKey, "order-" + paymentKey, 10000, lookupStatus);
		}

		void reset() {
			nextApprovedAmount = null;
			failNextCancel = false;
			confirmCalls = 0;
			cancelCalls = 0;
			lookupCalls = 0;
			lookupStatus = "DONE";
			cancelAmounts.clear();
			cancelIdempotencyKeys.clear();
		}
	}
}
