package com.dropshipshop.api.order;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

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
class CustomerOrderApiIntegrationTest {

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
	void rejectsAnonymousAndAdminOrderHistoryAccess() throws Exception {
		mockMvc.perform(get("/api/orders"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/orders")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isForbidden());
	}

	@Test
	void listsOnlyCurrentCustomerVisibleOrdersWithoutInternalOrderStatus() throws Exception {
		UserAccount customer = createCustomer("order-customer-1");
		UserAccount other = createCustomer("order-customer-2");
		CustomerOrder visible = createApprovedOrder(customer, "ORD-LIST-1", "CO-LIST-1", 20000);
		createPendingOrder(customer, "ORD-PENDING-1", "CO-PENDING-1", 10000);
		createExpiredOrder(customer, "ORD-EXPIRED-1", "CO-EXPIRED-1", 10000);
		createApprovedOrder(other, "ORD-OTHER-1", "CO-OTHER-1", 30000);
		createPaymentExceptionOrder(customer, "ORD-EXCEPTION-1", "CO-EXCEPTION-1", 15000);

		mockMvc.perform(get("/api/orders")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orders", hasSize(2)))
			.andExpect(jsonPath("$.orders[0].status").exists())
			.andExpect(jsonPath("$.orders[0].displayStatus").doesNotExist())
			.andExpect(jsonPath("$.orders[?(@.orderId == '%s')]".formatted(visible.getId()), hasSize(1)))
			.andExpect(jsonPath("$.orders[?(@.status == 'PAYMENT_EXCEPTION')]", hasSize(1)));
	}

	@Test
	void returnsCustomerOrderDetailWithPaymentAndPlaceholderSummaries() throws Exception {
		UserAccount customer = createCustomer("order-customer-3");
		UserAccount other = createCustomer("order-customer-4");
		CustomerOrder order = createApprovedOrder(customer, "ORD-DETAIL-1", "CO-DETAIL-1", 40000);
		CustomerOrder pendingOrder = createPendingOrder(customer, "ORD-PENDING-DETAIL-1", "CO-PENDING-DETAIL-1", 10000);

		mockMvc.perform(get("/api/orders/{orderId}", order.getId())
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderId", is(order.getId().toString())))
			.andExpect(jsonPath("$.status", is("SUPPLIER_ORDER_PENDING")))
			.andExpect(jsonPath("$.displayStatus").doesNotExist())
			.andExpect(jsonPath("$.paymentGroup.checkoutNumber", is("CO-DETAIL-1")))
			.andExpect(jsonPath("$.paymentGroup.status", is("APPROVED")))
			.andExpect(jsonPath("$.paymentGroup.displayStatus").doesNotExist())
			.andExpect(jsonPath("$.payment.status", is("APPROVED")))
			.andExpect(jsonPath("$.shippingAddress.recipientName", is("Receiver")))
			.andExpect(jsonPath("$.items", hasSize(1)))
			.andExpect(jsonPath("$.items[0].productName", is("Order Product ORD-DETAIL-1")))
			.andExpect(jsonPath("$.fulfillment.status", is("PENDING")))
			.andExpect(jsonPath("$.shipment.status", is("READY")))
			.andExpect(jsonPath("$.refund.status").doesNotExist());

		mockMvc.perform(get("/api/orders/{orderId}", order.getId())
				.with(authentication(TestAuthentication.customer(other.getId()))))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/orders/{orderId}", pendingOrder.getId())
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isNotFound());
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
		CustomerOrder order = createPendingOrder(customer, orderNumber, checkoutNumber, amount);
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

	private CustomerOrder createPaymentExceptionOrder(UserAccount customer, String orderNumber, String checkoutNumber, long amount) {
		CustomerOrder order = createPendingOrder(customer, orderNumber, checkoutNumber, amount);
		order.getPaymentGroup().markPaymentException();
		order.markPaymentException();
		return orderRepository.saveAndFlush(order);
	}

	private CustomerOrder createExpiredOrder(UserAccount customer, String orderNumber, String checkoutNumber, long amount) {
		CustomerOrder order = createPendingOrder(customer, orderNumber, checkoutNumber, amount);
		order.getPaymentGroup().expire();
		order.expire();
		return orderRepository.saveAndFlush(order);
	}

	private CustomerOrder createPendingOrder(UserAccount customer, String orderNumber, String checkoutNumber, long amount) {
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
