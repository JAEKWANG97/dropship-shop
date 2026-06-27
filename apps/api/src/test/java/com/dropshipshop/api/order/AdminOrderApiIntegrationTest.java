package com.dropshipshop.api.order;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.domain.PaymentMethod;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
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
	private FulfillmentRepository fulfillmentRepository;

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
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')].checkoutNumber".formatted(pending.getId()), hasItem("ADM-CO-1")));
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
			.andExpect(jsonPath("$.payment.method", is("CARD")))
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
		paymentRepository.save(Payment.approved(
			order.getPaymentGroup(),
			"pay-" + orderNumber,
			PaymentMethod.CARD,
			amount,
			amount,
			Instant.now(),
			"DONE",
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
}
