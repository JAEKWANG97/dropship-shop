package com.dropshipshop.api.notification;

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

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.notification.domain.NotificationChannel;
import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.notification.domain.NotificationStatus;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.sms.SmsSendResult;
import com.dropshipshop.api.sms.SmsSender;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminNotificationApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RecordingSmsSender smsSender;

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
	private NotificationLogRepository notificationLogRepository;

	@BeforeEach
	void resetSmsSender() {
		smsSender.reset();
	}

	@Test
	void recordsFailedNotificationWithoutRollingBackBankTransferConfirmation() throws Exception {
		smsSender.failTransactional("SENS timeout");
		UserAccount customer = createCustomer("notification-failure-customer");
		CustomerOrder order = createPaymentPendingOrder(
			customer,
			"NOTIFY-FAIL-1",
			"NOTIFY-FAIL-CO-1",
			33000,
			"010-9999-0000"
		);

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", order.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Deposit matched"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("SUPPLIER_ORDER_PENDING")));

		CustomerOrder savedOrder = orderRepository.findById(order.getId()).orElseThrow();
		PaymentGroup savedPaymentGroup = paymentGroupRepository.findById(order.getPaymentGroup().getId()).orElseThrow();
		Payment payment = paymentRepository.findFirstByPaymentGroup_IdOrderByCreatedAtDesc(savedPaymentGroup.getId()).orElseThrow();
		assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.SUPPLIER_ORDER_PENDING);
		assertThat(savedPaymentGroup.getStatus()).isEqualTo(PaymentGroupStatus.APPROVED);
		assertThat(payment.getApprovedAmount()).isEqualTo(33000L);
		assertThat(notificationLogRepository.findAllByOrderByCreatedAtAsc())
			.filteredOn(log -> order.getId().equals(log.getOrderId()))
			.filteredOn(log -> log.getType() == NotificationType.PAYMENT_COMPLETED)
			.singleElement()
			.satisfies(log -> {
				assertThat(log.getStatus()).isEqualTo(NotificationStatus.FAILED);
				assertThat(log.getFailureReason()).contains("SENS timeout");
				assertThat(log.getRecipient()).isEqualTo("010-9999-0000");
			});
	}

	@Test
	void retriesFailedNotificationForAdminOnlyAndSupportsStatusFilter() throws Exception {
		NotificationLog log = new NotificationLog(
			null,
			null,
			null,
			null,
			null,
			NotificationType.DELAY_NOTICE,
			NotificationChannel.SMS,
			"010-2222-3333",
			"delay_notice",
			"message=[코어블SAF] 출고 지연 중입니다. 확인 후 안내드리겠습니다"
		);
		log.markFailed("SENS timeout");
		notificationLogRepository.saveAndFlush(log);

		mockMvc.perform(get("/api/admin/notifications")
				.param("status", "FAILED")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notifications[?(@.notificationId == '%s')]".formatted(log.getId()), hasSize(1)))
			.andExpect(jsonPath("$.notifications[?(@.notificationId == '%s')].status".formatted(log.getId()), hasItem("FAILED")));

		mockMvc.perform(post("/api/admin/notifications/{notificationId}/retry", log.getId())
				.with(authentication(TestAuthentication.customer())))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/admin/notifications/{notificationId}/retry", log.getId())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk());

		NotificationLog retried = notificationLogRepository.findById(log.getId()).orElseThrow();
		assertThat(retried.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(retried.getFailureReason()).isNull();
		assertThat(retried.getSentAt()).isNotNull();
		assertThat(smsSender.transactionalMessages()).contains(
			"010-2222-3333|[코어블SAF] 출고 지연 중입니다. 확인 후 안내드리겠습니다"
		);

		mockMvc.perform(get("/api/admin/notifications")
				.param("status", "SENT")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notifications[*].notificationId", hasItem(log.getId().toString())));
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

	private CustomerOrder createPaymentPendingOrder(
		UserAccount customer,
		String orderNumber,
		String checkoutNumber,
		long amount,
		String recipientPhone
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
		paymentGroup.configureBankTransfer("Test Bank", "123-456", "가라사니", "Receiver", "Cash receipt on request");
		paymentGroup.confirmPolicy(Instant.now());
		paymentGroupRepository.saveAndFlush(paymentGroup);
		CustomerOrder order = orderRepository.save(new CustomerOrder(
			orderNumber,
			customer,
			supplier,
			paymentGroup,
			new ShippingAddressSnapshot("Receiver", recipientPhone, "12345", "Seoul test road", "101"),
			amount,
			paymentGroup.getExpiresAt()
		));
		orderItemRepository.save(new OrderItem(order, product, option, 1, 1));
		return order;
	}

	@TestConfiguration
	static class SmsTestConfig {

		@Bean
		@Primary
		RecordingSmsSender recordingSmsSender() {
			return new RecordingSmsSender();
		}
	}

	static class RecordingSmsSender implements SmsSender {

		private final List<String> transactionalMessages = new ArrayList<>();
		private String failureMessage;

		@Override
		public void sendVerificationCode(String phoneNumber, String code) {
		}

		@Override
		public SmsSendResult sendTransactional(String phoneNumber, String message) {
			if (failureMessage != null) {
				throw new IllegalStateException(failureMessage);
			}
			transactionalMessages.add(phoneNumber + "|" + message);
			return SmsSendResult.sent();
		}

		void failTransactional(String message) {
			this.failureMessage = message;
		}

		void reset() {
			failureMessage = null;
			transactionalMessages.clear();
		}

		List<String> transactionalMessages() {
			return transactionalMessages;
		}
	}
}
