package com.dropshipshop.api.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentMethod;
import com.dropshipshop.api.payment.domain.PaymentStatus;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.payment.toss.TossApprovedPayment;
import com.dropshipshop.api.payment.toss.TossCancelledPayment;
import com.dropshipshop.api.payment.toss.TossPaymentException;
import com.dropshipshop.api.payment.toss.TossPaymentSnapshot;
import com.dropshipshop.api.payment.toss.TossPaymentsClient;
import com.dropshipshop.api.refund.domain.RefundStatus;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RefundApiIntegrationTest {

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

	@Autowired
	private OrderItemRepository orderItemRepository;

	@Autowired
	private RefundRepository refundRepository;

	@BeforeEach
	void resetFakeClient() {
		fakeTossPaymentsClient.reset();
	}

	@Test
	void selfServiceCancelCreatesRefundAndCompletesPgCancel() throws Exception {
		UserAccount customer = createCustomer("refund-customer-1");
		CustomerOrder order = createApprovedOrder(customer, "REFUND-CANCEL-1", "REFUND-CANCEL-CO-1", 22000);

		mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Changed mind"
					}
					"""))
			.andExpect(status().isCreated());

		UUID refundId = refundRepository.findByOrder_Id(order.getId()).orElseThrow().getId();

		mockMvc.perform(get("/api/admin/refunds")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.refunds[?(@.refundId == '%s')]".formatted(refundId), hasSize(1)))
			.andExpect(jsonPath("$.refunds[?(@.refundId == '%s')].status".formatted(refundId), hasItem("REQUESTED")))
			.andExpect(jsonPath("$.refunds[?(@.refundId == '%s')].refundAmount".formatted(refundId), hasItem(22000)));

		approveRefund(refundId, "Customer cancel approved");

		mockMvc.perform(post("/api/admin/refunds/{refundId}/request-pg-cancel", refundId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("COMPLETED")))
			.andExpect(jsonPath("$.orderStatus", is("REFUNDED")))
			.andExpect(jsonPath("$.paymentGroupStatus", is("REFUNDED")))
			.andExpect(jsonPath("$.paymentStatus", is("REFUNDED")))
			.andExpect(jsonPath("$.providerCancelTransactionKey", is("cancel-pay-REFUND-CANCEL-1")));

		mockMvc.perform(get("/api/orders/{orderId}", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayStatus", is("환불 완료")))
			.andExpect(jsonPath("$.refund.displayStatus", is("환불 완료")))
			.andExpect(jsonPath("$.refund.amount", is(22000)));

		mockMvc.perform(get("/api/admin/orders/{orderId}", order.getId())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.refund.status", is("COMPLETED")))
			.andExpect(jsonPath("$.refund.refundAmount", is(22000)));
	}

	@Test
	void outOfStockRefundPartiallyRefundsPaymentGroupOrderAmount() throws Exception {
		UserAccount customer = createCustomer("refund-customer-2");
		PaymentGroup paymentGroup = createApprovedPaymentGroup(customer, "REFUND-PARTIAL-CO-1", 70000);
		CustomerOrder activeOrder = createOrderInPaymentGroup(customer, paymentGroup, "REFUND-ACTIVE-1", 30000);
		CustomerOrder stockoutOrder = createOrderInPaymentGroup(customer, paymentGroup, "REFUND-STOCKOUT-1", 40000);
		approvePayment(paymentGroup, "pay-REFUND-PARTIAL-1", 70000);

		mockMvc.perform(post("/api/admin/orders/{orderId}/out-of-stock", stockoutOrder.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Supplier confirmed stockout"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("OUT_OF_STOCK")));

		UUID refundId = refundRepository.findByOrder_Id(stockoutOrder.getId()).orElseThrow().getId();
		approveRefund(refundId, "Out of stock refund approved");

		mockMvc.perform(post("/api/admin/refunds/{refundId}/request-pg-cancel", refundId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("COMPLETED")))
			.andExpect(jsonPath("$.refundAmount", is(40000)))
			.andExpect(jsonPath("$.orderStatus", is("REFUNDED")))
			.andExpect(jsonPath("$.paymentGroupStatus", is("PARTIALLY_REFUNDED")))
			.andExpect(jsonPath("$.paymentStatus", is("PARTIALLY_REFUNDED")));

		assertThat(orderRepository.findById(stockoutOrder.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
		assertThat(orderRepository.findById(activeOrder.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.SUPPLIER_ORDER_PENDING);
		assertThat(paymentGroupRepository.findById(paymentGroup.getId()).orElseThrow().getStatus()).isEqualTo(PaymentGroupStatus.PARTIALLY_REFUNDED);
		assertThat(paymentRepository.findFirstByPaymentGroup_IdOrderByCreatedAtDesc(paymentGroup.getId()).orElseThrow().getStatus())
			.isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
		assertThat(fakeTossPaymentsClient.cancelAmounts).containsExactly(40000L);
	}

	@Test
	void pgCancelFailureKeepsRefundRetryRequiredAndRetryCanComplete() throws Exception {
		UserAccount customer = createCustomer("refund-customer-3");
		CustomerOrder order = createApprovedOrder(customer, "REFUND-FAIL-1", "REFUND-FAIL-CO-1", 26000);
		mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Changed mind"
					}
					"""))
			.andExpect(status().isCreated());
		UUID refundId = refundRepository.findByOrder_Id(order.getId()).orElseThrow().getId();
		approveRefund(refundId, "Customer cancel approved");
		fakeTossPaymentsClient.failNextCancel = true;

		mockMvc.perform(post("/api/admin/refunds/{refundId}/request-pg-cancel", refundId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("RETRY_REQUIRED")))
			.andExpect(jsonPath("$.orderStatus", is("REFUND_REQUESTED")))
			.andExpect(jsonPath("$.paymentStatus", is("REFUND_FAILED")))
			.andExpect(jsonPath("$.failureCode", is("TOSS_CANCEL_FAILED")));

		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
		assertThat(refundRepository.findById(refundId).orElseThrow().getStatus()).isEqualTo(RefundStatus.RETRY_REQUIRED);

		fakeTossPaymentsClient.failNextCancel = true;
		mockMvc.perform(post("/api/admin/refunds/{refundId}/retry", refundId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("MANUAL_REVIEW_REQUIRED")))
			.andExpect(jsonPath("$.failureCode", is("TOSS_CANCEL_FAILED")));

		mockMvc.perform(post("/api/admin/refunds/{refundId}/manual-review", refundId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "APPROVED",
					  "reason": "Manual review approved retry"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("APPROVED")))
			.andExpect(jsonPath("$.reviewedByAdminId", is(TestAuthentication.ADMIN_ID.toString())))
			.andExpect(jsonPath("$.adminReviewReason", is("Manual review approved retry")));

		mockMvc.perform(post("/api/admin/refunds/{refundId}/request-pg-cancel", refundId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("COMPLETED")))
			.andExpect(jsonPath("$.orderStatus", is("REFUNDED")))
			.andExpect(jsonPath("$.paymentStatus", is("REFUNDED")));
	}

	@Test
	void protectsAdminRefundApisAndRejectsCompletedRefundRetry() throws Exception {
		UserAccount customer = createCustomer("refund-customer-4");
		CustomerOrder order = createApprovedOrder(customer, "REFUND-AUTH-1", "REFUND-AUTH-CO-1", 27000);
		mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Changed mind"
					}
					"""))
			.andExpect(status().isCreated());
		UUID refundId = refundRepository.findByOrder_Id(order.getId()).orElseThrow().getId();

		mockMvc.perform(get("/api/admin/refunds"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/admin/refunds")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/admin/refunds/{refundId}/request-pg-cancel", refundId)
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/admin/refunds/{refundId}/request-pg-cancel", refundId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isBadRequest());

		approveRefund(refundId, "Customer cancel approved");

		mockMvc.perform(post("/api/admin/refunds/{refundId}/request-pg-cancel", refundId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("COMPLETED")));

		mockMvc.perform(post("/api/admin/refunds/{refundId}/request-pg-cancel", refundId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsRefundManualReviewToUnsupportedStatus() throws Exception {
		UserAccount customer = createCustomer("refund-customer-5");
		CustomerOrder order = createApprovedOrder(customer, "REFUND-MANUAL-REJECT-1", "REFUND-MANUAL-REJECT-CO-1", 28000);
		mockMvc.perform(post("/api/orders/{orderId}/cancel", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Changed mind"
					}
					"""))
			.andExpect(status().isCreated());
		UUID refundId = refundRepository.findByOrder_Id(order.getId()).orElseThrow().getId();
		approveRefund(refundId, "Customer cancel approved");
		fakeTossPaymentsClient.failNextCancel = true;
		mockMvc.perform(post("/api/admin/refunds/{refundId}/request-pg-cancel", refundId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("RETRY_REQUIRED")));
		fakeTossPaymentsClient.failNextCancel = true;
		mockMvc.perform(post("/api/admin/refunds/{refundId}/retry", refundId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("MANUAL_REVIEW_REQUIRED")));

		mockMvc.perform(post("/api/admin/refunds/{refundId}/manual-review", refundId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "COMPLETED",
					  "reason": "Invalid manual review state"
					}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/admin/refunds/{refundId}/manual-review", refundId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "REJECTED",
					  "reason": "Manual review rejected"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("REJECTED")))
			.andExpect(jsonPath("$.adminReviewReason", is("Manual review rejected")));
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

	private void approveRefund(UUID refundId, String reason) throws Exception {
		mockMvc.perform(post("/api/admin/refunds/{refundId}/approve", refundId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "%s"
					}
					""".formatted(reason)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("APPROVED")))
			.andExpect(jsonPath("$.reviewedByAdminId", is(TestAuthentication.ADMIN_ID.toString())))
			.andExpect(jsonPath("$.adminReviewReason", is(reason)));
	}

	private CustomerOrder createApprovedOrder(UserAccount customer, String orderNumber, String checkoutNumber, long amount) {
		PaymentGroup paymentGroup = createApprovedPaymentGroup(customer, checkoutNumber, amount);
		CustomerOrder order = createOrderInPaymentGroup(customer, paymentGroup, orderNumber, amount);
		approvePayment(paymentGroup, "pay-" + orderNumber, amount);
		return orderRepository.saveAndFlush(order);
	}

	private PaymentGroup createApprovedPaymentGroup(UserAccount customer, String checkoutNumber, long amount) {
		PaymentGroup paymentGroup = paymentGroupRepository.save(new PaymentGroup(
			checkoutNumber,
			customer,
			amount,
			Instant.now().plusSeconds(1800)
		));
		paymentGroup.approve(amount, Instant.now());
		return paymentGroupRepository.saveAndFlush(paymentGroup);
	}

	private CustomerOrder createOrderInPaymentGroup(
		UserAccount customer,
		PaymentGroup paymentGroup,
		String orderNumber,
		long amount
	) {
		Supplier supplier = supplierRepository.save(new Supplier(
			"Supplier " + orderNumber,
			"Manager",
			"010-0000-0000",
			orderNumber + "@supplier.example",
			null
		));
		Product product = productRepository.save(new Product(
			supplier,
			"Order Product " + orderNumber,
			"Order Product Summary",
			amount,
			ProductStatus.ACTIVE
		));
		ProductOption option = productOptionRepository.save(new ProductOption(product, "Default", 0, ProductOptionStatus.ACTIVE));
		CustomerOrder order = orderRepository.save(new CustomerOrder(
			orderNumber,
			customer,
			supplier,
			paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul test road", "101"),
			amount,
			paymentGroup.getExpiresAt()
		));
		order.markSupplierOrderPending();
		orderItemRepository.save(new OrderItem(order, product, option, 1, 1));
		return orderRepository.saveAndFlush(order);
	}

	private void approvePayment(PaymentGroup paymentGroup, String paymentKey, long amount) {
		paymentRepository.saveAndFlush(Payment.approved(
			paymentGroup,
			paymentKey,
			PaymentMethod.CARD,
			amount,
			amount,
			Instant.now(),
			"DONE",
			Instant.now()
		));
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

		private boolean failNextCancel;
		private final List<Long> cancelAmounts = new ArrayList<>();

		@Override
		public TossApprovedPayment confirm(String paymentKey, String orderId, long amount) {
			return new TossApprovedPayment(
				paymentKey,
				orderId,
				amount,
				PaymentMethod.CARD,
				Instant.now(),
				"DONE"
			);
		}

		@Override
		public TossCancelledPayment cancel(String paymentKey, String cancelReason, long cancelAmount, String idempotencyKey) {
			if (failNextCancel) {
				failNextCancel = false;
				throw new TossPaymentException("temporary cancel failure");
			}
			cancelAmounts.add(cancelAmount);
			return new TossCancelledPayment(
				paymentKey,
				"order-" + paymentKey,
				cancelAmount,
				0,
				"cancel-" + paymentKey,
				"CANCELED"
			);
		}

		@Override
		public TossPaymentSnapshot getPayment(String paymentKey) {
			return new TossPaymentSnapshot(paymentKey, "order-" + paymentKey, 0, "DONE");
		}

		void reset() {
			failNextCancel = false;
			cancelAmounts.clear();
		}
	}
}
