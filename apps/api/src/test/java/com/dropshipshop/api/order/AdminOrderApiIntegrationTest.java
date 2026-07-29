package com.dropshipshop.api.order;

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
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.notification.NotificationLogRepository;
import com.dropshipshop.api.notification.domain.NotificationChannel;
import com.dropshipshop.api.notification.domain.NotificationStatus;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.OrderStatusHistory;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentMethod;
import com.dropshipshop.api.payment.domain.PaymentProvider;
import com.dropshipshop.api.payment.domain.PaymentStatus;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.domain.ShipmentStatus;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminOrderApiIntegrationTest {

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

	@Autowired
	private PaymentGroupRepository paymentGroupRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private CustomerOrderRepository orderRepository;

	@Autowired
	private OrderItemRepository orderItemRepository;

	@Autowired
	private OrderStatusHistoryRepository orderStatusHistoryRepository;

	@Autowired
	private FulfillmentRepository fulfillmentRepository;

	@Autowired
	private ShipmentRepository shipmentRepository;

	@Autowired
	private NotificationLogRepository notificationLogRepository;

	@Test
	void rejectsAnonymousAndCustomerAdminOrderQueueAccess() throws Exception {
		mockMvc.perform(get("/api/admin/orders"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/admin/orders")
				.with(authentication(TestAuthentication.customer())))
			.andExpect(status().isForbidden());
	}

	@Test
	void listsOnlySupplierOrderPendingOrdersForAdminQueue() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-1");
		CustomerOrder pending = createApprovedOrder(customer, "ADM-QUEUE-1", "ADM-CO-1", 21000);
		CustomerOrder paymentPending = createPaymentPendingOrder(customer, "ADM-PAYMENT-PENDING-1", "ADM-CO-PENDING-1", 12000);
		CustomerOrder expired = createExpiredOrder(customer, "ADM-EXPIRED-1", "ADM-CO-EXPIRED-1", 13000);

		mockMvc.perform(get("/api/admin/orders")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')]".formatted(pending.getId()), hasSize(1)))
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')]".formatted(paymentPending.getId()), hasSize(0)))
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')]".formatted(expired.getId()), hasSize(0)))
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')].status".formatted(pending.getId()), hasItem("SUPPLIER_ORDER_PENDING")))
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')].displayStatus".formatted(pending.getId())).doesNotExist())
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')].supplierName".formatted(pending.getId()), hasItem("Supplier ADM-QUEUE-1")))
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')].customerEmail".formatted(pending.getId()), hasItem("admin-order-customer-1@example.com")))
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')].checkoutNumber".formatted(pending.getId()), hasItem("ADM-CO-1")))
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')].itemCount".formatted(pending.getId()), hasItem(1)));
	}

	@Test
	void listsPaymentPendingOrdersWithStatusFilter() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-bank-list");
		CustomerOrder paymentPending = createPaymentPendingOrder(customer, "ADM-BANK-LIST-1", "ADM-BANK-LIST-CO-1", 12000);
		CustomerOrder supplierPending = createApprovedOrder(customer, "ADM-BANK-LIST-2", "ADM-BANK-LIST-CO-2", 21000);

		mockMvc.perform(get("/api/admin/orders")
				.param("status", "PAYMENT_PENDING")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')]".formatted(paymentPending.getId()), hasSize(1)))
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')]".formatted(supplierPending.getId()), hasSize(0)))
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')].status".formatted(paymentPending.getId()), hasItem("PAYMENT_PENDING")));
	}

	@Test
	void confirmsBankTransferDepositAndRejectsDuplicateConfirmation() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-bank-confirm");
		CustomerOrder order = createPaymentPendingOrder(customer, "ADM-BANK-CONFIRM-1", "ADM-BANK-CONFIRM-CO-1", 45000);
		order.getPaymentGroup().confirmPolicy(Instant.now());
		paymentGroupRepository.saveAndFlush(order.getPaymentGroup());

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "actualDepositorName": "Receiver",
					  "actualAmount": 45000,
					  "depositedAt": "2020-07-19T09:00:00Z",
					  "transactionReference": "BANK-ADM-BANK-CONFIRM-1",
					  "reason": "Deposit amount and depositor name matched"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderId", is(order.getId().toString())))
			.andExpect(jsonPath("$.status", is("SUPPLIER_ORDER_PENDING")));

		CustomerOrder savedOrder = orderRepository.findById(order.getId()).orElseThrow();
		PaymentGroup savedPaymentGroup = paymentGroupRepository.findById(order.getPaymentGroup().getId()).orElseThrow();
		Payment payment = paymentRepository.findFirstByPaymentGroup_IdOrderByCreatedAtDesc(savedPaymentGroup.getId()).orElseThrow();
		assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.SUPPLIER_ORDER_PENDING);
		assertThat(savedPaymentGroup.getStatus()).isEqualTo(PaymentGroupStatus.APPROVED);
		assertThat(savedPaymentGroup.getDepositConfirmedByAdminId()).isEqualTo(TestAuthentication.ADMIN_ID);
		assertThat(savedPaymentGroup.getDepositConfirmationReason()).isEqualTo("Deposit amount and depositor name matched");
		assertThat(savedPaymentGroup.getActualDepositorName()).isEqualTo("Receiver");
		assertThat(savedPaymentGroup.getActualDepositAmount()).isEqualTo(45000);
		assertThat(savedPaymentGroup.getDepositTransactionReference()).isEqualTo("BANK-ADM-BANK-CONFIRM-1");
		assertThat(payment.getProvider()).isEqualTo(PaymentProvider.BANK_TRANSFER);
		assertThat(payment.getMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
		assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
		assertThat(payment.getProviderPaymentKey()).isEqualTo("BANK-ADM-BANK-CONFIRM-CO-1");
		mockMvc.perform(get("/api/admin/orders/{orderId}", order.getId())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentGroup.bankTransferDeposit.actualDepositorName", is("Receiver")))
			.andExpect(jsonPath("$.paymentGroup.bankTransferDeposit.actualDepositAmount", is(45000)))
			.andExpect(jsonPath("$.paymentGroup.bankTransferDeposit.depositTransactionReference", is("BANK-ADM-BANK-CONFIRM-1")));
		mockMvc.perform(get("/api/orders/{orderId}", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentGroup.actualDepositAmount").doesNotExist())
			.andExpect(jsonPath("$.paymentGroup.depositTransactionReference").doesNotExist());
		assertThat(orderStatusHistoryRepository.findAllByOrder_IdOrderByCreatedAtAsc(order.getId()))
			.extracting(OrderStatusHistory::getActionType)
			.contains("BANK_TRANSFER_DEPOSIT_CONFIRMED");
		assertThat(notificationLogRepository.findAllByOrderByCreatedAtAsc())
			.filteredOn(log -> order.getId().equals(log.getOrderId()))
			.filteredOn(log -> log.getType() == NotificationType.PAYMENT_COMPLETED)
			.singleElement()
			.satisfies(log -> {
				assertThat(log.getChannel()).isEqualTo(NotificationChannel.SMS);
				assertThat(log.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
				assertThat(log.getRecipient()).isEqualTo("010-1111-2222");
			});

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "actualDepositorName": "Receiver",
					  "actualAmount": 45000,
					  "depositedAt": "2020-07-19T09:00:00Z",
					  "transactionReference": "BANK-ADM-BANK-CONFIRM-1-DUPLICATE",
					  "reason": "Duplicate confirmation"
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsMismatchedDepositAmountWithoutApprovingTheOrder() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-bank-amount-mismatch");
		CustomerOrder order = createPaymentPendingOrder(customer, "ADM-BANK-AMOUNT-MISMATCH-1", "ADM-BANK-AMOUNT-MISMATCH-CO-1", 45000);
		order.getPaymentGroup().confirmPolicy(Instant.now());
		paymentGroupRepository.saveAndFlush(order.getPaymentGroup());

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "actualDepositorName": "Receiver",
					  "actualAmount": 44900,
					  "depositedAt": "2020-07-19T09:00:00Z",
					  "transactionReference": "BANK-ADM-BANK-AMOUNT-MISMATCH-1",
					  "reason": "Deposit amount checked"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", is("Actual deposit amount must match the checkout total")));

		PaymentGroup savedPaymentGroup = paymentGroupRepository.findById(order.getPaymentGroup().getId()).orElseThrow();
		assertThat(savedPaymentGroup.getStatus()).isEqualTo(PaymentGroupStatus.PAYMENT_PENDING);
		assertThat(savedPaymentGroup.getActualDepositAmount()).isNull();
		assertThat(paymentRepository.findFirstByPaymentGroup_IdOrderByCreatedAtDesc(savedPaymentGroup.getId())).isEmpty();
		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
	}

	@Test
	void requiresCompletePastDepositEvidence() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-bank-evidence-validation");
		CustomerOrder order = createPaymentPendingOrder(customer, "ADM-BANK-EVIDENCE-1", "ADM-BANK-EVIDENCE-CO-1", 45000);
		order.getPaymentGroup().confirmPolicy(Instant.now());
		paymentGroupRepository.saveAndFlush(order.getPaymentGroup());

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "actualAmount": 45000,
					  "depositedAt": "2020-07-19T09:00:00Z",
					  "transactionReference": "BANK-ADM-EVIDENCE-MISSING-NAME",
					  "reason": "Deposit amount checked"
					}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "actualDepositorName": "Receiver",
					  "actualAmount": 45000,
					  "depositedAt": "%s",
					  "transactionReference": "BANK-ADM-EVIDENCE-FUTURE",
					  "reason": "Deposit amount checked"
					}
					""".formatted(Instant.now().plusSeconds(3600))))
			.andExpect(status().isBadRequest());

		PaymentGroup savedPaymentGroup = paymentGroupRepository.findById(order.getPaymentGroup().getId()).orElseThrow();
		assertThat(savedPaymentGroup.getStatus()).isEqualTo(PaymentGroupStatus.PAYMENT_PENDING);
		assertThat(savedPaymentGroup.getActualDepositAmount()).isNull();
	}

	@Test
	void sendsManualDelayNoticeBeforeShipment() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-delay-notice");
		CustomerOrder order = createApprovedOrder(customer, "ADM-DELAY-1", "ADM-DELAY-CO-1", 28000);

		mockMvc.perform(post("/api/admin/orders/{orderId}/delay-notice", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Supplier has not confirmed expected shipment date"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("SUPPLIER_ORDER_PENDING")));

		assertThat(notificationLogRepository.findAllByOrderByCreatedAtAsc())
			.filteredOn(log -> order.getId().equals(log.getOrderId()))
			.filteredOn(log -> log.getType() == NotificationType.DELAY_NOTICE)
			.singleElement()
			.satisfies(log -> {
				assertThat(log.getChannel()).isEqualTo(NotificationChannel.SMS);
				assertThat(log.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
				assertThat(log.getRecipient()).isEqualTo("010-1111-2222");
			});
		mockMvc.perform(get("/api/admin/actions")
				.param("orderId", order.getId().toString())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.actions", hasSize(1)))
			.andExpect(jsonPath("$.actions[0].orderId", is(order.getId().toString())))
			.andExpect(jsonPath("$.actions[0].actionType", is("DELAY_NOTICE_SENT")));
	}

	@Test
	void rejectsCustomerBankTransferDepositActions() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-bank-auth");
		CustomerOrder order = createPaymentPendingOrder(customer, "ADM-BANK-AUTH-1", "ADM-BANK-AUTH-CO-1", 19000);

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Deposit confirmed"
					}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void cancelsUnpaidBankTransferAndRecordsDepositMismatchMemo() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-bank-cancel");
		CustomerOrder mismatchOrder = createPaymentPendingOrder(customer, "ADM-BANK-MISMATCH-1", "ADM-BANK-MISMATCH-CO-1", 22000);
		CustomerOrder cancelOrder = createPaymentPendingOrder(customer, "ADM-BANK-CANCEL-1", "ADM-BANK-CANCEL-CO-1", 23000);

		mockMvc.perform(post("/api/admin/orders/{orderId}/deposit-mismatch", mismatchOrder.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "memo": "Depositor name does not match"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("PAYMENT_PENDING")));

		assertThat(paymentGroupRepository.findById(mismatchOrder.getPaymentGroup().getId()).orElseThrow().getDepositMismatchMemo())
			.isEqualTo("Depositor name does not match");
		assertThat(orderRepository.findById(mismatchOrder.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);

		mockMvc.perform(post("/api/admin/orders/{orderId}/unpaid-cancel", cancelOrder.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Deposit deadline passed"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("CANCELLED")));

		PaymentGroup savedPaymentGroup = paymentGroupRepository.findById(cancelOrder.getPaymentGroup().getId()).orElseThrow();
		assertThat(orderRepository.findById(cancelOrder.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
		assertThat(savedPaymentGroup.getStatus()).isEqualTo(PaymentGroupStatus.CANCELLED);
		assertThat(savedPaymentGroup.getUnpaidCancelledByAdminId()).isEqualTo(TestAuthentication.ADMIN_ID);
		assertThat(savedPaymentGroup.getUnpaidCancelReason()).isEqualTo("Deposit deadline passed");
		assertThat(orderStatusHistoryRepository.findAllByOrder_IdOrderByCreatedAtAsc(cancelOrder.getId()))
			.extracting(OrderStatusHistory::getActionType)
			.contains("BANK_TRANSFER_UNPAID_CANCELLED");
	}

	@Test
	void returnsAdminOrderDetailWithSupplierItemsShippingAndPaymentSummary() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-2");
		CustomerOrder order = createApprovedOrder(customer, "ADM-DETAIL-1", "ADM-CO-DETAIL-1", 33000);

		mockMvc.perform(get("/api/admin/orders/{orderId}", order.getId())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderId", is(order.getId().toString())))
			.andExpect(jsonPath("$.orderNumber", is("ADM-DETAIL-1")))
			.andExpect(jsonPath("$.status", is("SUPPLIER_ORDER_PENDING")))
			.andExpect(jsonPath("$.supplier.name", is("Supplier ADM-DETAIL-1")))
			.andExpect(jsonPath("$.supplier.contactName", is("Manager")))
			.andExpect(jsonPath("$.customer.email", is("admin-order-customer-2@example.com")))
			.andExpect(jsonPath("$.shippingAddress.recipientName", is("Receiver")))
			.andExpect(jsonPath("$.shippingAddress.address1", is("Seoul test road")))
			.andExpect(jsonPath("$.paymentGroup.checkoutNumber", is("ADM-CO-DETAIL-1")))
			.andExpect(jsonPath("$.paymentGroup.status", is("APPROVED")))
			.andExpect(jsonPath("$.payment.status", is("APPROVED")))
			.andExpect(jsonPath("$.payment.method", is("BANK_TRANSFER")))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].productName", is("Order Product ADM-DETAIL-1")))
			.andExpect(jsonPath("$.items[0].optionName", is("Default")))
			.andExpect(jsonPath("$.items[0].unitPrice", is(33000)))
			.andExpect(jsonPath("$.items[0].quantity", is(1)));
	}

	@Test
	void rejectsCustomerSupplierActionAccess() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-3");
		CustomerOrder order = createApprovedOrder(customer, "ADM-ACTION-AUTH-1", "ADM-ACTION-AUTH-CO-1", 11000);

		mockMvc.perform(post("/api/admin/orders/{orderId}/supplier-work-start", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Start supplier ordering"
					}
					"""))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/admin/orders/{orderId}/supplier-order/validate", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isForbidden());
	}

	@Test
	void startsSupplierWorkAndLocksAddress() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-4");
		CustomerOrder order = createApprovedOrder(customer, "ADM-WORK-START-1", "ADM-WORK-CO-1", 22000);

		mockMvc.perform(post("/api/admin/orders/{orderId}/supplier-work-start", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Supplier order work started"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderId", is(order.getId().toString())))
			.andExpect(jsonPath("$.status", is("SUPPLIER_ORDER_PENDING")))
			.andExpect(jsonPath("$.fulfillment.status", is("PENDING")))
			.andExpect(jsonPath("$.fulfillment.supplierOrderStartedAt").exists())
			.andExpect(jsonPath("$.fulfillment.addressLockedAt").exists())
			.andExpect(jsonPath("$.fulfillment.addressLockedByAdminId", is(TestAuthentication.ADMIN_ID.toString())));

		CustomerOrder savedOrder = orderRepository.findById(order.getId()).orElseThrow();
		assertThat(savedOrder.getSupplierOrderStartedAt()).isNotNull();
		assertThat(savedOrder.getAddressLockedAt()).isNotNull();
		assertThat(savedOrder.getAddressLockedByAdminId()).isEqualTo(TestAuthentication.ADMIN_ID);
	}

	@Test
	void marksSupplierOrderCompletedOnlyAfterWorkStartWithEvidence() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-5");
		CustomerOrder order = createApprovedOrder(customer, "ADM-SUPPLIER-ORDER-1", "ADM-SUPPLIER-CO-1", 25000);

		mockMvc.perform(post("/api/admin/orders/{orderId}/supplier-order-completed", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierOrderNumber": "SO-001",
					  "expectedShipDate": "2026-07-01",
					  "supplierResponseMemo": "Supplier confirmed",
					  "reason": "Supplier order placed"
					}
					"""))
			.andExpect(status().isBadRequest());

		startSupplierWork(order);

		mockMvc.perform(post("/api/admin/orders/{orderId}/supplier-order-completed", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierOrderNumber": "SO-001",
					  "expectedShipDate": "2026-07-01",
					  "supplierResponseMemo": "Supplier confirmed",
					  "reason": "Supplier order placed"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("SUPPLIER_ORDERED")))
			.andExpect(jsonPath("$.fulfillment.status", is("ORDERED")))
			.andExpect(jsonPath("$.fulfillment.supplierOrderNumber", is("SO-001")))
			.andExpect(jsonPath("$.fulfillment.orderedByAdminId", is(TestAuthentication.ADMIN_ID.toString())))
			.andExpect(jsonPath("$.fulfillment.expectedShipDate", is("2026-07-01")))
			.andExpect(jsonPath("$.fulfillment.supplierResponseMemo", is("Supplier confirmed")));

		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.SUPPLIER_ORDERED);
	}

	@Test
	void marksOutOfStockWithReasonAndRejectsInvalidStates() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-6");
		CustomerOrder order = createApprovedOrder(customer, "ADM-OOS-1", "ADM-OOS-CO-1", 26000);
		CustomerOrder paymentPending = createPaymentPendingOrder(customer, "ADM-OOS-PENDING-1", "ADM-OOS-PENDING-CO-1", 10000);

		mockMvc.perform(post("/api/admin/orders/{orderId}/out-of-stock", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Supplier confirmed stockout"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("OUT_OF_STOCK")))
			.andExpect(jsonPath("$.fulfillment.status", is("OUT_OF_STOCK")))
			.andExpect(jsonPath("$.fulfillment.outOfStockReason", is("Supplier confirmed stockout")));

		mockMvc.perform(post("/api/admin/orders/{orderId}/out-of-stock", paymentPending.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Supplier confirmed stockout"
					}
					"""))
			.andExpect(status().isBadRequest());

		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.OUT_OF_STOCK);
		assertThat(fulfillmentRepository.findByOrder_Id(order.getId()).orElseThrow().getOutOfStockReason())
			.isEqualTo("Supplier confirmed stockout");
	}

	@Test
	void validatesSupplierActionReasonsAndEvidence() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-7");
		CustomerOrder order = createApprovedOrder(customer, "ADM-VALIDATION-1", "ADM-VALIDATION-CO-1", 27000);

		mockMvc.perform(post("/api/admin/orders/{orderId}/supplier-work-start", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());

		startSupplierWork(order);

		mockMvc.perform(post("/api/admin/orders/{orderId}/supplier-order-completed", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierResponseMemo": "Missing supplier order number",
					  "reason": "Supplier order placed"
					}
					"""))
			.andExpect(status().isBadRequest());

		CustomerOrder outOfStockOrder = createApprovedOrder(customer, "ADM-VALIDATION-OOS-1", "ADM-VALIDATION-OOS-CO-1", 28000);
		mockMvc.perform(post("/api/admin/orders/{orderId}/out-of-stock", outOfStockOrder.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsCustomerShipmentAccess() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-8");
		CustomerOrder order = createSupplierOrderedOrder(customer, "ADM-SHIP-AUTH-1", "ADM-SHIP-AUTH-CO-1", 31000);

		mockMvc.perform(post("/api/admin/orders/{orderId}/shipments", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "carrier": "CJ대한통운",
					  "trackingNumber": "1234567890"
					}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void validatesShipmentCarrierAndTrackingNumber() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-9");
		CustomerOrder order = createSupplierOrderedOrder(customer, "ADM-SHIP-VALIDATION-1", "ADM-SHIP-VALIDATION-CO-1", 32000);

		mockMvc.perform(post("/api/admin/orders/{orderId}/shipments", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "carrier": "",
					  "trackingNumber": ""
					}
					"""))
			.andExpect(status().isBadRequest());
	}

	@Test
	void createsShipmentMovesOrderToShippedAndBlocksDuplicateShipment() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-10");
		CustomerOrder order = createSupplierOrderedOrder(customer, "ADM-SHIP-1", "ADM-SHIP-CO-1", 34000);

		mockMvc.perform(post("/api/admin/orders/{orderId}/shipments", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "carrier": "CJ대한통운",
					  "trackingNumber": "1234567890"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("SHIPPED")))
			.andExpect(jsonPath("$.shipment.status", is("SHIPPED")))
			.andExpect(jsonPath("$.shipment.carrier", is("CJ대한통운")))
			.andExpect(jsonPath("$.shipment.trackingNumber", is("1234567890")))
			.andExpect(jsonPath("$.shipment.shippedAt").exists());

		mockMvc.perform(post("/api/admin/orders/{orderId}/shipments", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "carrier": "CJ대한통운",
					  "trackingNumber": "1234567890"
					}
					"""))
			.andExpect(status().isBadRequest());

		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPED);
		assertThat(shipmentRepository.findByOrder_Id(order.getId()).orElseThrow().getTrackingNumber()).isEqualTo("1234567890");

		mockMvc.perform(get("/api/admin/notifications")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notifications[?(@.orderId == '%s')].type".formatted(order.getId()), hasItem("SHIPMENT_STARTED")));
	}

	@Test
	void returnsShipmentSummaryOnCustomerOrderDetail() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-11");
		CustomerOrder order = createSupplierOrderedOrder(customer, "ADM-SHIP-CUSTOMER-1", "ADM-SHIP-CUSTOMER-CO-1", 35000);
		createShipment(order, "롯데택배", "9988776655");

			mockMvc.perform(get("/api/orders/{orderId}", order.getId())
					.with(authentication(TestAuthentication.customer(customer.getId()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("SHIPPED")))
				.andExpect(jsonPath("$.displayStatus").doesNotExist())
				.andExpect(jsonPath("$.shipment.status", is("SHIPPED")))
				.andExpect(jsonPath("$.shipment.carrier", is("롯데택배")))
				.andExpect(jsonPath("$.shipment.trackingNumber", is("9988776655")));
	}

	@Test
	void syncsDeliveredShipmentAndDoesNotMoveBackward() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-12");
		CustomerOrder order = createSupplierOrderedOrder(customer, "ADM-SHIP-SYNC-1", "ADM-SHIP-SYNC-CO-1", 36000);
		createShipment(order, "CJ대한통운", "SYNC-123");
		Shipment shipment = shipmentRepository.findByOrder_Id(order.getId()).orElseThrow();

		mockMvc.perform(post("/api/admin/shipments/{shipmentId}/tracking-sync", shipment.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "trackingStatus": "DELIVERED"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shipmentStatus", is("DELIVERED")))
			.andExpect(jsonPath("$.orderStatus", is("DELIVERED")))
			.andExpect(jsonPath("$.trackingSyncedAt").exists());

		mockMvc.perform(post("/api/admin/shipments/{shipmentId}/tracking-sync", shipment.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "trackingStatus": "IN_TRANSIT"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shipmentStatus", is("DELIVERED")))
			.andExpect(jsonPath("$.orderStatus", is("DELIVERED")));

		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
		assertThat(shipmentRepository.findById(shipment.getId()).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
		var histories = orderStatusHistoryRepository.findAllByOrder_IdOrderByCreatedAtAsc(order.getId());
		assertThat(histories.get(histories.size() - 1).getActionType()).isEqualTo("SHIPMENT_TRACKING_SYNC");

		mockMvc.perform(get("/api/admin/notifications")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notifications[?(@.orderId == '%s')].type".formatted(order.getId()), hasItem("DELIVERY_COMPLETED")));
	}

	@Test
	void recordsInternalTrackingSyncFailureWithoutChangingOrder() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-13");
		CustomerOrder order = createSupplierOrderedOrder(customer, "ADM-SHIP-FAIL-1", "ADM-SHIP-FAIL-CO-1", 37000);
		createShipment(order, "한진택배", "FAIL-123");

		mockMvc.perform(post("/api/internal/shipments/tracking-sync")
				.header("X-Internal-Sync-Token", "test-internal-sync-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "shipments": [
					    {
					      "carrier": "한진택배",
					      "trackingNumber": "FAIL-123",
					      "failureReason": "Carrier timeout"
					    }
					  ]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.received", is(1)))
			.andExpect(jsonPath("$.matched", is(1)))
			.andExpect(jsonPath("$.delivered", is(0)))
			.andExpect(jsonPath("$.failed", is(1)))
			.andExpect(jsonPath("$.notFound", is(0)));

		Shipment shipment = shipmentRepository.findByOrder_Id(order.getId()).orElseThrow();
		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPED);
		assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
		assertThat(shipment.getTrackingSyncFailureReason()).isEqualTo("Carrier timeout");
		assertThat(shipment.getTrackingSyncedAt()).isNotNull();
	}

	@Test
	void rejectsInternalTrackingSyncWithoutToken() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-13-token");
		CustomerOrder order = createSupplierOrderedOrder(customer, "ADM-SHIP-TOKEN-1", "ADM-SHIP-TOKEN-CO-1", 37100);
		createShipment(order, "한진택배", "TOKEN-123");

		mockMvc.perform(post("/api/internal/shipments/tracking-sync")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "shipments": [
					    {
					      "carrier": "한진택배",
					      "trackingNumber": "TOKEN-123",
					      "trackingStatus": "DELIVERED"
					    }
					  ]
					}
					"""))
			.andExpect(status().isUnauthorized());

		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPED);
	}

	@Test
	void rejectsCustomerShipmentTrackingSyncAccess() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-14");
		CustomerOrder order = createSupplierOrderedOrder(customer, "ADM-SHIP-SYNC-AUTH-1", "ADM-SHIP-SYNC-AUTH-CO-1", 38000);
		createShipment(order, "CJ대한통운", "SYNC-AUTH-123");
		Shipment shipment = shipmentRepository.findByOrder_Id(order.getId()).orElseThrow();

		mockMvc.perform(post("/api/admin/shipments/{shipmentId}/tracking-sync", shipment.getId())
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "trackingStatus": "DELIVERED"
					}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void manuallyCorrectsShipmentToDeliveredWithStatusHistory() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-15");
		CustomerOrder order = createSupplierOrderedOrder(customer, "ADM-SHIP-MANUAL-1", "ADM-SHIP-MANUAL-CO-1", 39000);
		createShipment(order, "우체국택배", "MANUAL-123");
		Shipment shipment = shipmentRepository.findByOrder_Id(order.getId()).orElseThrow();

		mockMvc.perform(post("/api/admin/shipments/{shipmentId}/manual-correction", shipment.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "DELIVERED",
					  "reason": "Carrier site shows delivered"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shipmentStatus", is("DELIVERED")))
			.andExpect(jsonPath("$.orderStatus", is("DELIVERED")))
			.andExpect(jsonPath("$.manualCorrectionReason", is("Carrier site shows delivered")));

		Shipment savedShipment = shipmentRepository.findById(shipment.getId()).orElseThrow();
		assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
		assertThat(savedShipment.isManualOverride()).isTrue();
		assertThat(savedShipment.getManualCorrectionReason()).isEqualTo("Carrier site shows delivered");
		assertThat(savedShipment.getManualCorrectedByAdminId()).isEqualTo(TestAuthentication.ADMIN_ID);
		assertThat(savedShipment.getManualCorrectedAt()).isNotNull();

		var histories = orderStatusHistoryRepository.findAllByOrder_IdOrderByCreatedAtAsc(order.getId());
		OrderStatusHistory history = histories.get(histories.size() - 1);
		assertThat(history.getActionType()).isEqualTo("SHIPMENT_MANUAL_CORRECTION");
		assertThat(history.getFromStatus()).isEqualTo(OrderStatus.SHIPPED);
		assertThat(history.getToStatus()).isEqualTo(OrderStatus.DELIVERED);
		assertThat(history.getReason()).isEqualTo("Carrier site shows delivered");
	}

	@Test
	void exposesOrderStatusAndAdminActionHistories() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-17");
		CustomerOrder order = createSupplierOrderedOrder(customer, "ADM-AUDIT-1", "ADM-AUDIT-CO-1", 41000);
		createShipment(order, "CJ대한통운", "AUDIT-123");

		mockMvc.perform(get("/api/admin/orders/{orderId}/status-history", order.getId())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.histories", hasSize(2)))
			.andExpect(jsonPath("$.histories[*].actorUserId", hasItem(TestAuthentication.ADMIN_ID.toString())))
			.andExpect(jsonPath("$.histories[*].actionType", hasItem("SUPPLIER_ORDER_COMPLETED")))
			.andExpect(jsonPath("$.histories[*].actionType", hasItem("SHIPMENT_STARTED")))
			.andExpect(jsonPath("$.histories[*].toStatus", hasItem("SHIPPED")))
			.andExpect(jsonPath("$.histories[*].guardResult", hasItem("ALLOWED")));

		mockMvc.perform(get("/api/admin/orders/{orderId}/status-history", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/admin/orders/{orderId}/status-history", UUID.randomUUID())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/admin/actions")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.actions[?(@.orderId == '%s')].adminUserId".formatted(order.getId()), hasItem(TestAuthentication.ADMIN_ID.toString())))
			.andExpect(jsonPath("$.actions[?(@.orderId == '%s')].actionType".formatted(order.getId()), hasItem("SUPPLIER_WORK_START")))
			.andExpect(jsonPath("$.actions[?(@.orderId == '%s')].actionType".formatted(order.getId()), hasItem("SUPPLIER_ORDER_COMPLETED")))
			.andExpect(jsonPath("$.actions[?(@.orderId == '%s')].actionType".formatted(order.getId()), hasItem("SHIPMENT_STARTED")));

		mockMvc.perform(get("/api/admin/actions")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isForbidden());
	}

	@Test
	void rejectsUnsupportedShipmentManualCorrectionStatus() throws Exception {
		UserAccount customer = createCustomer("admin-order-customer-16");
		CustomerOrder order = createSupplierOrderedOrder(customer, "ADM-SHIP-MANUAL-INVALID-1", "ADM-SHIP-MANUAL-INVALID-CO-1", 40000);
		createShipment(order, "우체국택배", "MANUAL-INVALID-123");
		Shipment shipment = shipmentRepository.findByOrder_Id(order.getId()).orElseThrow();

		mockMvc.perform(post("/api/admin/shipments/{shipmentId}/manual-correction", shipment.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "SHIPPED",
					  "reason": "Do not move backward"
					}
					"""))
			.andExpect(status().isBadRequest());
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

	private CustomerOrder createApprovedOrder(UserAccount customer, String orderNumber, String checkoutNumber, long amount) {
		CustomerOrder order = createPaymentPendingOrder(customer, orderNumber, checkoutNumber, amount);
		order.getPaymentGroup().approve(amount, Instant.now());
		paymentGroupRepository.save(order.getPaymentGroup());
		order.markSupplierOrderPending();
		paymentRepository.save(Payment.bankTransferApproved(
			order.getPaymentGroup(),
			"BANK-" + checkoutNumber,
			amount,
			Instant.now()
		));
		return orderRepository.saveAndFlush(order);
	}

	private CustomerOrder createExpiredOrder(UserAccount customer, String orderNumber, String checkoutNumber, long amount) {
		CustomerOrder order = createPaymentPendingOrder(customer, orderNumber, checkoutNumber, amount);
		order.getPaymentGroup().expire();
		order.expire();
		return orderRepository.saveAndFlush(order);
	}

	private CustomerOrder createPaymentPendingOrder(
		UserAccount customer,
		String orderNumber,
		String checkoutNumber,
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
		PaymentGroup paymentGroup = paymentGroupRepository.save(new PaymentGroup(
			checkoutNumber,
			customer,
			amount,
			Instant.now().plusSeconds(1800)
		));
		CustomerOrder order = orderRepository.save(new CustomerOrder(
			orderNumber,
			customer,
			supplier,
			paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul test road", "101"),
			amount,
			paymentGroup.getExpiresAt()
		));
		orderItemRepository.save(new OrderItem(order, product, option, 1, 1));
		return order;
	}

	private CustomerOrder createSupplierOrderedOrder(
		UserAccount customer,
		String orderNumber,
		String checkoutNumber,
		long amount
	) throws Exception {
		CustomerOrder order = createApprovedOrder(customer, orderNumber, checkoutNumber, amount);
		startSupplierWork(order);
		markSupplierOrderCompleted(order);
		return orderRepository.findById(order.getId()).orElseThrow();
	}

	private void startSupplierWork(CustomerOrder order) throws Exception {
		mockMvc.perform(post("/api/admin/orders/{orderId}/supplier-work-start", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Supplier order work started"
					}
					"""))
			.andExpect(status().isOk());
	}

	private void markSupplierOrderCompleted(CustomerOrder order) throws Exception {
		mockMvc.perform(post("/api/admin/orders/{orderId}/supplier-order-completed", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierOrderNumber": "SO-%s",
					  "expectedShipDate": "2026-07-01",
					  "supplierResponseMemo": "Supplier confirmed",
					  "reason": "Supplier order placed"
					}
					""".formatted(order.getOrderNumber())))
			.andExpect(status().isOk());
	}

	private void createShipment(CustomerOrder order, String carrier, String trackingNumber) throws Exception {
		mockMvc.perform(post("/api/admin/orders/{orderId}/shipments", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "carrier": "%s",
					  "trackingNumber": "%s"
					}
					""".formatted(carrier, trackingNumber)))
			.andExpect(status().isOk());
	}
}
