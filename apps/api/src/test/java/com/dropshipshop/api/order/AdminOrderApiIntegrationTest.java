package com.dropshipshop.api.order;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
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
}
