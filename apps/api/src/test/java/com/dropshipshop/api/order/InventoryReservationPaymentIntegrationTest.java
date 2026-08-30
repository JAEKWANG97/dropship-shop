package com.dropshipshop.api.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.InventoryMode;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductReviewStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierAvailability;
import com.dropshipshop.api.catalog.domain.SupplierPortalContractStatus;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.checkout.CheckoutExpiryService;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderItemReservationStatus;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.notification.NotificationLogRepository;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentStatus;
import com.dropshipshop.api.payment.repository.PaymentEventRepository;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.refund.domain.RefundReason;
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
class InventoryReservationPaymentIntegrationTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserAccountRepository userRepository;
	@Autowired
	private SupplierRepository supplierRepository;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private ProductOptionRepository optionRepository;
	@Autowired
	private PaymentGroupRepository paymentGroupRepository;
	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	private PaymentEventRepository paymentEventRepository;
	@Autowired
	private CustomerOrderRepository orderRepository;
	@Autowired
	private OrderItemRepository orderItemRepository;
	@Autowired
	private RefundRepository refundRepository;
	@Autowired
	private FulfillmentRepository fulfillmentRepository;
	@Autowired
	private NotificationLogRepository notificationLogRepository;
	@Autowired
	private CheckoutExpiryService checkoutExpiryService;

	@Test
	void normalPortalDepositConsumesHeldInventoryExactlyOnce() throws Exception {
		PortalCheckout checkout = createPortalCheckout("normal", 5, Instant.now().plusSeconds(1800));

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", checkout.orderId())
				.header("Idempotency-Key", "portal-normal-confirm-1")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(receipt(12000, checkout.depositedAt(), "PORTAL-NORMAL-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome", is("APPROVED")))
			.andExpect(jsonPath("$.status", is("SUPPLIER_ORDER_PENDING")));

		ProductOption option = optionRepository.findById(checkout.optionId()).orElseThrow();
		OrderItem item = orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(checkout.orderId()).getFirst();
		assertThat(option.getOnHandQuantity()).isEqualTo(4);
		assertThat(option.getReservedQuantity()).isZero();
		assertThat(item.getReservationStatus()).isEqualTo(OrderItemReservationStatus.CONSUMED);
		assertThat(item.getConsumedAt()).isNotNull();
		assertThat(fulfillmentRepository.findByOrder_Id(checkout.orderId())).isEmpty();

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", checkout.orderId())
				.header("Idempotency-Key", "portal-normal-confirm-1")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(receipt(12000, checkout.depositedAt(), "PORTAL-NORMAL-1")))
			.andExpect(status().isOk());
		assertThat(optionRepository.findById(checkout.optionId()).orElseThrow().getOnHandQuantity()).isEqualTo(4);
	}

	@Test
	void expiryReleasesHeldInventoryOnceAndLateDepositReacquiresIt() throws Exception {
		Instant deadline = Instant.now().minusSeconds(60);
		PortalCheckout checkout = createPortalCheckout("late", 5, deadline);

		assertThat(checkoutExpiryService.expire(checkout.paymentGroupId(), Instant.now())).isTrue();
		ProductOption releasedOption = optionRepository.findById(checkout.optionId()).orElseThrow();
		OrderItem releasedItem = orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(checkout.orderId()).getFirst();
		long releasedVersion = releasedOption.getInventoryVersion();
		assertThat(releasedOption.getOnHandQuantity()).isEqualTo(5);
		assertThat(releasedOption.getReservedQuantity()).isZero();
		assertThat(releasedItem.getReservationStatus()).isEqualTo(OrderItemReservationStatus.RELEASED);
		assertThat(checkoutExpiryService.expire(checkout.paymentGroupId(), Instant.now())).isFalse();
		assertThat(optionRepository.findById(checkout.optionId()).orElseThrow().getInventoryVersion())
			.isEqualTo(releasedVersion);

		Instant depositedAt = deadline.minusSeconds(30);
		mockMvc.perform(post("/api/admin/orders/{orderId}/late-deposit", checkout.orderId())
				.header("Idempotency-Key", "portal-late-confirm-1")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(receipt(12000, depositedAt, "PORTAL-LATE-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome", is("APPROVED")));

		ProductOption consumedOption = optionRepository.findById(checkout.optionId()).orElseThrow();
		OrderItem consumedItem = orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(checkout.orderId()).getFirst();
		assertThat(consumedOption.getOnHandQuantity()).isEqualTo(4);
		assertThat(consumedOption.getReservedQuantity()).isZero();
		assertThat(consumedItem.getReservationStatus()).isEqualTo(OrderItemReservationStatus.CONSUMED);
		assertThat(consumedItem.getReleasedAt()).isNotNull();
		assertThat(consumedItem.getReacquiredAt()).isNotNull();
		assertThat(paymentGroupRepository.findById(checkout.paymentGroupId()).orElseThrow().getStatus())
			.isEqualTo(PaymentGroupStatus.APPROVED);
	}

	@Test
	void expiryIgnoresLegacyUntrackedCheckout() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Instant deadline = Instant.now().minusSeconds(60);
		UserAccount customer = userRepository.save(new UserAccount(
			SocialProvider.GOOGLE, "legacy-expiry-" + suffix,
			"legacy-expiry-" + suffix + "@example.com", "Customer", UserRole.CUSTOMER));
		Supplier supplier = supplierRepository.save(new Supplier(
			"Legacy expiry supplier " + suffix, "Manager", "010-2222-5555",
			"legacy-expiry-" + suffix + "@supplier.example", null));
		Product product = productRepository.save(new Product(
			supplier, "Legacy expiry product " + suffix, "Legacy product", 12000, ProductStatus.ACTIVE));
		ProductOption option = optionRepository.save(new ProductOption(
			product, "Default", 0, ProductOptionStatus.ACTIVE));
		PaymentGroup paymentGroup = paymentGroupRepository.save(new PaymentGroup(
			"P-" + UUID.randomUUID(), customer, 12000, deadline));
		CustomerOrder order = orderRepository.save(new CustomerOrder(
			"O-" + UUID.randomUUID(), customer, supplier, paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul", "101"),
			12000, deadline));
		OrderItem item = orderItemRepository.save(new OrderItem(order, product, option, 1, 1));

		assertThat(paymentGroupRepository.findExpiryCandidateIds(
			Instant.now(), PageRequest.of(0, 100))).doesNotContain(paymentGroup.getId());
		assertThat(checkoutExpiryService.expire(paymentGroup.getId(), Instant.now())).isFalse();
		assertThat(paymentGroupRepository.findById(paymentGroup.getId()).orElseThrow().getStatus())
			.isEqualTo(PaymentGroupStatus.PAYMENT_PENDING);
		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.PAYMENT_PENDING);
		assertThat(orderItemRepository.findById(item.getId()).orElseThrow().getReservationStatus())
			.isEqualTo(OrderItemReservationStatus.NOT_APPLICABLE);
	}

	@Test
	void depositRejectsProductSupplierReassignmentAgainstImmutableOrderSnapshot() throws Exception {
		PortalCheckout checkout = createPortalCheckout("supplier-drift", 5, Instant.now().plusSeconds(1800));
		Instant now = Instant.now();
		Supplier replacement = Supplier.portalApplicant(
			"Replacement supplier", "Replacement manager", "010-2222-6666",
			"replacement-" + UUID.randomUUID() + "@supplier.example", null);
		replacement.verifyPortalContract("replacement-contract", now.minusSeconds(60), now.plusSeconds(3600),
			now, TestAuthentication.ADMIN_ID);
		replacement.changeSalesStatus(SupplierStatus.ACTIVE, now);
		replacement = supplierRepository.save(replacement);
		Product product = productRepository.findById(checkout.productId()).orElseThrow();
		product.updateBase(replacement, product.getName(), product.getSummary(), product.getSourcePrice(),
			product.getBasePrice(), product.getCategoryCode());
		productRepository.saveAndFlush(product);

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", checkout.orderId())
				.header("Idempotency-Key", "portal-supplier-drift-confirm-1")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(receipt(12000, checkout.depositedAt(), "PORTAL-SUPPLIER-DRIFT-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome", is("PAYMENT_EXCEPTION")))
			.andExpect(jsonPath("$.exceptionReason", is("SELLABILITY_CHECK_FAILED")))
			.andExpect(jsonPath("$.refunds[0].reason", is("SALE_UNAVAILABLE_AT_DEPOSIT")));

		assertThat(orderRepository.findById(checkout.orderId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.REFUND_REQUESTED);
		assertThat(orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(checkout.orderId()).getFirst()
			.getReservationStatus()).isEqualTo(OrderItemReservationStatus.RELEASED);
		assertThat(fulfillmentRepository.findByOrder_Id(checkout.orderId())).isEmpty();
	}

	@Test
	void lateDepositWithInsufficientReacquisitionStockCreatesRefundWithoutResuming() throws Exception {
		Instant deadline = Instant.now().minusSeconds(60);
		PortalCheckout checkout = createPortalCheckout("shortage", 1, deadline);
		assertThat(checkoutExpiryService.expire(checkout.paymentGroupId(), Instant.now())).isTrue();
		ProductOption option = optionRepository.findById(checkout.optionId()).orElseThrow();
		option.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 0L);
		optionRepository.saveAndFlush(option);

		mockMvc.perform(post("/api/admin/orders/{orderId}/late-deposit", checkout.orderId())
				.header("Idempotency-Key", "portal-late-shortage-1")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(receipt(12000, deadline.minusSeconds(30), "PORTAL-SHORTAGE-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome", is("PAYMENT_EXCEPTION")))
			.andExpect(jsonPath("$.refunds[0].reason", is("LATE_DEPOSIT_EXCEPTION")));

		assertThat(optionRepository.findById(checkout.optionId()).orElseThrow().getOnHandQuantity()).isZero();
		assertThat(orderRepository.findById(checkout.orderId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.REFUND_REQUESTED);
		assertThat(refundRepository.findByOrder_Id(checkout.orderId()).orElseThrow().getReason())
			.isEqualTo(RefundReason.LATE_DEPOSIT_EXCEPTION);
		assertThat(fulfillmentRepository.findByOrder_Id(checkout.orderId())).isEmpty();
	}

	@Test
	void pendingPortalReceiptAfterDeadlineBecomesLateDepositExceptionWithoutScheduler() throws Exception {
		PortalCheckout checkout = createPortalCheckout(
			"scheduler-delay", 3, Instant.now().minusSeconds(60));

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", checkout.orderId())
				.header("Idempotency-Key", "portal-scheduler-delay-confirm-1")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(receipt(12000, checkout.depositedAt(), "PORTAL-SCHEDULER-DELAY-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome", is("PAYMENT_EXCEPTION")))
			.andExpect(jsonPath("$.exceptionReason", is("APPROVED_AFTER_EXPIRED")))
			.andExpect(jsonPath("$.refunds[0].reason", is("LATE_DEPOSIT_EXCEPTION")));

		assertThat(orderRepository.findById(checkout.orderId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.REFUND_REQUESTED);
		assertThat(orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(checkout.orderId()).getFirst()
			.getReservationStatus()).isEqualTo(OrderItemReservationStatus.RELEASED);
		ProductOption option = optionRepository.findById(checkout.optionId()).orElseThrow();
		assertThat(option.getOnHandQuantity()).isEqualTo(3);
		assertThat(option.getReservedQuantity()).isZero();
	}

	@Test
	void depositLazilyExpiresOverduePortalContractAndRoutesReceiptToRefund() throws Exception {
		PortalCheckout checkout = createPortalCheckout(
			"contract-expired", 3, Instant.now().plusSeconds(1800));
		Instant now = Instant.now();
		Supplier supplier = supplierRepository.findById(checkout.supplierId()).orElseThrow();
		supplier.verifyPortalContract("expired-contract", now.minusSeconds(7200), now.minusSeconds(3600),
			now.minusSeconds(7200), TestAuthentication.ADMIN_ID);
		supplierRepository.saveAndFlush(supplier);

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", checkout.orderId())
				.header("Idempotency-Key", "portal-contract-expired-confirm-1")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(receipt(12000, checkout.depositedAt(), "PORTAL-CONTRACT-EXPIRED-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome", is("PAYMENT_EXCEPTION")))
			.andExpect(jsonPath("$.exceptionReason", is("SELLABILITY_CHECK_FAILED")))
			.andExpect(jsonPath("$.refunds[0].reason", is("SALE_UNAVAILABLE_AT_DEPOSIT")));

		Supplier expired = supplierRepository.findById(checkout.supplierId()).orElseThrow();
		assertThat(expired.getPortalContractStatus()).isEqualTo(SupplierPortalContractStatus.EXPIRED);
		assertThat(expired.getStatus()).isEqualTo(SupplierStatus.INACTIVE);
		assertThat(fulfillmentRepository.findByOrder_Id(checkout.orderId())).isEmpty();
	}

	@Test
	void lateDepositRejectsCurrentInventoryModeThatDiffersFromTheOrderSnapshot() throws Exception {
		PortalCheckout checkout = createPortalCheckout(
			"mode-mismatch", 3, Instant.now().minusSeconds(60));
		Instant now = Instant.now();
		assertThat(checkoutExpiryService.expire(checkout.paymentGroupId(), now)).isTrue();
		ProductOption option = optionRepository.findById(checkout.optionId()).orElseThrow();
		option.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.UNTRACKED, null);
		optionRepository.saveAndFlush(option);
		Instant depositedAt = paymentGroupRepository.findById(checkout.paymentGroupId()).orElseThrow()
			.getExpiresAt().minusSeconds(30);

		mockMvc.perform(post("/api/admin/orders/{orderId}/late-deposit", checkout.orderId())
				.header("Idempotency-Key", "portal-mode-mismatch-late-1")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(receipt(12000, depositedAt, "PORTAL-MODE-MISMATCH-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome", is("PAYMENT_EXCEPTION")))
			.andExpect(jsonPath("$.exceptionReason", is("SELLABILITY_CHECK_FAILED")))
			.andExpect(jsonPath("$.refunds[0].reason", is("SALE_UNAVAILABLE_AT_DEPOSIT")));

		assertThat(orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(checkout.orderId()).getFirst()
			.getReservationStatus()).isEqualTo(OrderItemReservationStatus.RELEASED);
		assertThat(optionRepository.findById(checkout.optionId()).orElseThrow().getInventoryMode())
			.isEqualTo(InventoryMode.UNTRACKED);
	}

	@Test
	void receivedSaleUnavailableRefundCompletesEachOrderAndReplaysExactlyOnce() throws Exception {
		MultiOrderPortalCheckout checkout = createMultiOrderPortalCheckout();
		ProductOption option = optionRepository.findById(checkout.optionId()).orElseThrow();
		option.updateInventory(SupplierAvailability.UNAVAILABLE, InventoryMode.TRACKED, option.getOnHandQuantity());
		optionRepository.saveAndFlush(option);

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", checkout.firstOrderId())
				.header("Idempotency-Key", "portal-sale-unavailable-confirm-1")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(receipt(12000, checkout.depositedAt(), "PORTAL-SALE-UNAVAILABLE-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome", is("PAYMENT_EXCEPTION")))
			.andExpect(jsonPath("$.refunds", hasSize(2)))
			.andExpect(jsonPath("$.refunds[0].reason", is("SALE_UNAVAILABLE_AT_DEPOSIT")));

		Refund firstRefund = refundRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(checkout.paymentGroupId())
			.stream()
			.filter(refund -> refund.getOrder().getId().equals(checkout.firstOrderId()))
			.findFirst()
			.orElseThrow();
		mockMvc.perform(post("/api/admin/refunds/{refundId}/approve", firstRefund.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reason":"Approve received sale-unavailable refund"}
					"""))
			.andExpect(status().isOk());

		String completeBody = """
			{
			  "transferredAmount": 6000,
			  "reason": "Return first order amount",
			  "bankName": "Refund Bank",
			  "accountNumber": "111-222-333",
			  "accountHolder": "Receiver",
			  "transferredAt": "2020-07-19T10:00:00Z",
			  "transactionReference": "PARTIAL-REFUND-TRANSFER-1"
			}
			""";
		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(post("/api/admin/refunds/{refundId}/manual-complete", firstRefund.getId())
					.header("Idempotency-Key", "portal-partial-refund-key-1")
					.with(authentication(TestAuthentication.admin()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(completeBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("COMPLETED")))
				.andExpect(jsonPath("$.paymentGroupStatus", is("PARTIALLY_REFUNDED")))
				.andExpect(jsonPath("$.appliedOrderIds[0]", is(checkout.firstOrderId().toString())));
		}

		PaymentGroup paymentGroup = paymentGroupRepository.findById(checkout.paymentGroupId()).orElseThrow();
		assertThat(paymentGroup.getStatus()).isEqualTo(PaymentGroupStatus.PARTIALLY_REFUNDED);
		assertThat(paymentGroup.getRefundableAmount()).isEqualTo(6000);
		assertThat(paymentRepository.findFirstByPaymentGroup_IdOrderByCreatedAtDesc(checkout.paymentGroupId())
			.orElseThrow().getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
		assertThat(refundRepository.findById(firstRefund.getId()).orElseThrow().getStatus())
			.isEqualTo(RefundStatus.COMPLETED);
		assertThat(orderRepository.findById(checkout.firstOrderId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.REFUNDED);
		assertThat(orderRepository.findById(checkout.secondOrderId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.REFUND_REQUESTED);
		assertThat(paymentEventRepository.countByIdempotencyKey("portal-partial-refund-key-1")).isEqualTo(1);
		assertThat(paymentEventRepository.findByPaymentGroup_IdAndIdempotencyKeyAndCommandTypeIsNotNull(
			checkout.paymentGroupId(), "portal-partial-refund-key-1").orElseThrow().getOrderId())
			.isEqualTo(checkout.firstOrderId());

		Refund secondRefund = refundRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(checkout.paymentGroupId())
			.stream()
			.filter(refund -> refund.getOrder().getId().equals(checkout.secondOrderId()))
			.findFirst()
			.orElseThrow();
		mockMvc.perform(post("/api/admin/refunds/{refundId}/approve", secondRefund.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reason":"Approve second received sale-unavailable refund"}
					"""))
			.andExpect(status().isOk());

		String finalCompleteBody = completeBody.replace(
			"PARTIAL-REFUND-TRANSFER-1", "FINAL-REFUND-TRANSFER-2");
		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(post("/api/admin/refunds/{refundId}/manual-complete", secondRefund.getId())
					.header("Idempotency-Key", "portal-final-refund-key-2")
					.with(authentication(TestAuthentication.admin()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(finalCompleteBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("COMPLETED")))
				.andExpect(jsonPath("$.paymentGroupStatus", is("REFUNDED")));
		}

		assertThat(paymentGroupRepository.findById(checkout.paymentGroupId()).orElseThrow().getStatus())
			.isEqualTo(PaymentGroupStatus.REFUNDED);
		assertThat(paymentGroupRepository.findById(checkout.paymentGroupId()).orElseThrow().getRefundableAmount())
			.isZero();
		assertThat(paymentRepository.findFirstByPaymentGroup_IdOrderByCreatedAtDesc(checkout.paymentGroupId())
			.orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
		assertThat(orderRepository.findById(checkout.firstOrderId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.REFUNDED);
		assertThat(orderRepository.findById(checkout.secondOrderId()).orElseThrow().getStatus())
			.isEqualTo(OrderStatus.REFUNDED);
		assertThat(refundRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(checkout.paymentGroupId()))
			.extracting(Refund::getRefundAmount).containsExactlyInAnyOrder(6000L, 6000L);
		assertThat(paymentEventRepository.countByIdempotencyKey("portal-final-refund-key-2")).isEqualTo(1);
		assertThat(paymentEventRepository.findByPaymentGroup_IdAndIdempotencyKeyAndCommandTypeIsNotNull(
			checkout.paymentGroupId(), "portal-final-refund-key-2").orElseThrow().getOrderId())
			.isEqualTo(checkout.secondOrderId());
	}

	@Test
	void amountMismatchReleasesEverySupplierReservationAndCreatesOneGroupRefund() throws Exception {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Instant now = Instant.now();
		UserAccount customer = userRepository.save(new UserAccount(
			SocialProvider.GOOGLE, "mismatch-multi-" + suffix,
			"mismatch-multi-" + suffix + "@example.com", "Customer", UserRole.CUSTOMER));
		PaymentGroup paymentGroup = new PaymentGroup(
			"P-" + UUID.randomUUID(), customer, 12000, now.plusSeconds(1800));
		paymentGroup.confirmPolicy(now);
		paymentGroup = paymentGroupRepository.save(paymentGroup);

		List<Supplier> suppliers = List.of(
			portalSupplier("Mismatch supplier A " + suffix, "mismatch-a-" + suffix, now),
			portalSupplier("Mismatch supplier B " + suffix, "mismatch-b-" + suffix, now)
		);
		List<CustomerOrder> orders = new java.util.ArrayList<>();
		List<ProductOption> options = new java.util.ArrayList<>();
		for (int index = 0; index < suppliers.size(); index++) {
			Supplier supplier = suppliers.get(index);
			Product product = new Product(supplier, "Mismatch product " + index, "Mismatch product", 5000, 6000,
				ProductCategory.PPE_SAFETY_HELMET, ProductStatus.ACTIVE,
				ProductManagementChannel.SUPPLIER_PORTAL);
			product.updateReview(ProductReviewStatus.APPROVED, null, null);
			product = productRepository.save(product);
			ProductOption option = new ProductOption(product, "Default", 0, ProductOptionStatus.ACTIVE);
			option.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 2L);
			option.reserve(1);
			option = optionRepository.save(option);
			CustomerOrder order = orderRepository.save(new CustomerOrder(
				"O-" + UUID.randomUUID(), customer, supplier, paymentGroup,
				new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul", "101"),
				6000, paymentGroup.getExpiresAt()));
			orderItemRepository.save(new OrderItem(order, product, option, 1, 1, now));
			orders.add(order);
			options.add(option);
		}
		long notificationCount = notificationLogRepository.count();

		mockMvc.perform(post("/api/admin/orders/{orderId}/deposit-mismatch", orders.getFirst().getId())
				.header("Idempotency-Key", "portal-multi-mismatch-key-1")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(receipt(11000, now.minusSeconds(30), "PORTAL-MULTI-MISMATCH-1")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome", is("PAYMENT_EXCEPTION")))
			.andExpect(jsonPath("$.exceptionReason", is("AMOUNT_MISMATCH")))
			.andExpect(jsonPath("$.orderStatuses", hasSize(2)))
			.andExpect(jsonPath("$.refund.refundScope", is("PAYMENT_GROUP")))
			.andExpect(jsonPath("$.refund.orderId").doesNotExist())
			.andExpect(jsonPath("$.refund.refundAmount", is(11000)));

		Refund groupRefund = refundRepository.findByPaymentGroup_IdAndRefundScope(
			paymentGroup.getId(), com.dropshipshop.api.refund.domain.RefundScope.PAYMENT_GROUP).orElseThrow();
		assertThat(groupRefund.getOrder()).isNull();
		assertThat(refundRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(paymentGroup.getId()))
			.extracting(Refund::getId).containsExactly(groupRefund.getId());
		assertThat(orders).allSatisfy(order -> {
			assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
				.isEqualTo(OrderStatus.REFUND_REQUESTED);
			assertThat(fulfillmentRepository.findByOrder_Id(order.getId())).isEmpty();
			assertThat(orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(order.getId()).getFirst()
				.getReservationStatus()).isEqualTo(OrderItemReservationStatus.RELEASED);
		});
		assertThat(options).allSatisfy(option -> {
			ProductOption current = optionRepository.findById(option.getId()).orElseThrow();
			assertThat(current.getOnHandQuantity()).isEqualTo(2);
			assertThat(current.getReservedQuantity()).isZero();
		});
		assertThat(notificationLogRepository.count()).isEqualTo(notificationCount);
		assertThat(paymentEventRepository.findByPaymentGroup_IdAndIdempotencyKeyAndCommandTypeIsNotNull(
			paymentGroup.getId(), "portal-multi-mismatch-key-1").orElseThrow().getOrderId()).isNull();
	}

	private PortalCheckout createPortalCheckout(String suffix, long onHand, Instant deadline) {
		UserAccount customer = userRepository.save(new UserAccount(
			SocialProvider.GOOGLE, "portal-customer-" + suffix,
			"portal-customer-" + suffix + "@example.com", "Customer", UserRole.CUSTOMER));
		UserAccount manager = userRepository.save(new UserAccount(
			SocialProvider.KAKAO, "portal-manager-" + suffix,
			"portal-manager-" + suffix + "@example.com", "Manager", UserRole.CUSTOMER));
		Instant now = Instant.now();
		Supplier supplier = Supplier.portalApplicant(
			"Portal supplier " + suffix, "Manager", "010-2222-3333",
			"portal-manager-" + suffix + "@example.com", null);
		supplier.verifyPortalContract("portal-contract-v1", now.minusSeconds(3600), now.plusSeconds(3600),
			now.minusSeconds(3600), TestAuthentication.ADMIN_ID);
		supplier.bindManager(manager.getId(), now.minusSeconds(3500));
		supplier.changeSalesStatus(SupplierStatus.ACTIVE, now);
		supplier = supplierRepository.save(supplier);
		Product product = new Product(supplier, "Portal product " + suffix, "Portal product", 10000, 12000,
			ProductCategory.PPE_SAFETY_HELMET, ProductStatus.ACTIVE, ProductManagementChannel.SUPPLIER_PORTAL);
		product.updateReview(ProductReviewStatus.AUTO_APPROVED, null, null);
		product = productRepository.save(product);
		ProductOption option = new ProductOption(product, "Default", 0, ProductOptionStatus.ACTIVE);
		option.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, onHand);
		option.reserve(1);
		option = optionRepository.save(option);
		PaymentGroup paymentGroup = new PaymentGroup("P-" + UUID.randomUUID(),
			customer, 12000, deadline);
		paymentGroup.confirmPolicy(now);
		paymentGroup = paymentGroupRepository.save(paymentGroup);
		CustomerOrder order = orderRepository.save(new CustomerOrder(
			"O-" + UUID.randomUUID(), customer, supplier, paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul", "101"),
			12000, deadline));
		orderItemRepository.save(new OrderItem(order, product, option, 1, 1, now));
		return new PortalCheckout(paymentGroup.getId(), order.getId(), supplier.getId(), product.getId(),
			option.getId(), now.minusSeconds(30));
	}

	private Supplier portalSupplier(String name, String key, Instant now) {
		Supplier supplier = Supplier.portalApplicant(
			name, "Manager", "010-2222-7777", key + "@supplier.example", null);
		supplier.verifyPortalContract(key + "-contract", now.minusSeconds(3600), now.plusSeconds(3600),
			now.minusSeconds(3600), TestAuthentication.ADMIN_ID);
		supplier.changeSalesStatus(SupplierStatus.ACTIVE, now);
		return supplierRepository.save(supplier);
	}

	private MultiOrderPortalCheckout createMultiOrderPortalCheckout() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		UserAccount customer = userRepository.save(new UserAccount(
			SocialProvider.GOOGLE, "partial-customer-" + suffix,
			"partial-customer-" + suffix + "@example.com", "Customer", UserRole.CUSTOMER));
		UserAccount manager = userRepository.save(new UserAccount(
			SocialProvider.KAKAO, "partial-manager-" + suffix,
			"partial-manager-" + suffix + "@example.com", "Manager", UserRole.CUSTOMER));
		Instant now = Instant.now();
		Supplier supplier = Supplier.portalApplicant(
			"Partial supplier " + suffix, "Manager", "010-2222-4444",
			"partial-manager-" + suffix + "@example.com", null);
		supplier.verifyPortalContract("portal-contract-v1", now.minusSeconds(3600), now.plusSeconds(3600),
			now.minusSeconds(3600), TestAuthentication.ADMIN_ID);
		supplier.bindManager(manager.getId(), now.minusSeconds(3500));
		supplier.changeSalesStatus(SupplierStatus.ACTIVE, now);
		supplier = supplierRepository.save(supplier);
		Product product = new Product(supplier, "Partial product " + suffix, "Partial product", 5000, 6000,
			ProductCategory.PPE_SAFETY_HELMET, ProductStatus.ACTIVE, ProductManagementChannel.SUPPLIER_PORTAL);
		product.updateReview(ProductReviewStatus.AUTO_APPROVED, null, null);
		product = productRepository.save(product);
		ProductOption option = new ProductOption(product, "Default", 0, ProductOptionStatus.ACTIVE);
		option.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 4L);
		option.reserve(2);
		option = optionRepository.save(option);
		PaymentGroup paymentGroup = new PaymentGroup("P-" + UUID.randomUUID(), customer, 12000,
			now.plusSeconds(1800));
		paymentGroup.confirmPolicy(now);
		paymentGroup = paymentGroupRepository.save(paymentGroup);
		CustomerOrder firstOrder = orderRepository.save(new CustomerOrder(
			"O-" + UUID.randomUUID(), customer, supplier, paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul", "101"),
			6000, paymentGroup.getExpiresAt()));
		CustomerOrder secondOrder = orderRepository.save(new CustomerOrder(
			"O-" + UUID.randomUUID(), customer, supplier, paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul", "101"),
			6000, paymentGroup.getExpiresAt()));
		orderItemRepository.save(new OrderItem(firstOrder, product, option, 1, 1, now));
		orderItemRepository.save(new OrderItem(secondOrder, product, option, 1, 1, now));
		return new MultiOrderPortalCheckout(paymentGroup.getId(), firstOrder.getId(), secondOrder.getId(),
			option.getId(), now.minusSeconds(30));
	}

	private String receipt(long amount, Instant depositedAt, String reference) {
		return """
			{
			  "actualDepositorName":"Receiver",
			  "actualAmount":%d,
			  "depositedAt":"%s",
			  "transactionReference":"%s",
			  "reason":"Bank receipt verified"
			}
			""".formatted(amount, depositedAt, reference);
	}

	private record PortalCheckout(
		UUID paymentGroupId,
		UUID orderId,
		UUID supplierId,
		UUID productId,
		UUID optionId,
		Instant depositedAt
	) {
	}

	private record MultiOrderPortalCheckout(
		UUID paymentGroupId,
		UUID firstOrderId,
		UUID secondOrderId,
		UUID optionId,
		Instant depositedAt
	) {
	}
}
