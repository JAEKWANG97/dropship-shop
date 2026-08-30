package com.dropshipshop.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierSalesAction;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.email.EmailSendResult;
import com.dropshipshop.api.email.EmailSender;
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

@SpringBootTest(properties = "app.supplier-portal.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminNotificationApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RecordingSmsSender smsSender;

	@Autowired
	private RecordingEmailSender emailSender;

	@Autowired
	private NotificationDispatchListener notificationDispatchListener;

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

	@Autowired
	private SupplierNotificationRetentionService supplierNotificationRetentionService;

	@Autowired
	private SupplierOperationalNotificationScheduler supplierOperationalNotificationScheduler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void resetSmsSender() {
		smsSender.reset();
		emailSender.reset();
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
					  "actualDepositorName": "Receiver",
					  "actualAmount": 33000,
					  "depositedAt": "2020-07-19T09:00:00Z",
					  "transactionReference": "BANK-NOTIFY-FAIL-CO-1",
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

	@Test
	void rejectsGenericRetryForSupplierInvitationWithoutSending() throws Exception {
		NotificationLog invite = NotificationLog.supplierInvitation(
			UUID.randomUUID(), UUID.randomUUID(), "invite@example.com", "activation_link_not_stored"
		);
		invite.markFailed("EMAIL_PROVIDER_FAILURE");
		invite = notificationLogRepository.saveAndFlush(invite);

		mockMvc.perform(post("/api/admin/notifications/{notificationId}/retry", invite.getId())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("INVITE_REISSUE_NOT_ALLOWED")));

		assertThat(emailSender.messages()).isEmpty();
		assertThat(notificationLogRepository.findById(invite.getId()).orElseThrow().getStatus())
			.isEqualTo(NotificationStatus.FAILED);
	}

	@Test
	void dispatchesInquiryEmailWithLookupLinkAndRecordsFailure() {
		UUID inquiryId = UUID.randomUUID();
		NotificationLog sentLog = notificationLogRepository.saveAndFlush(new NotificationLog(
			null,
			null,
			null,
			null,
			null,
			inquiryId,
			NotificationType.CUSTOMER_INQUIRY_ANSWERED,
			NotificationChannel.EMAIL,
			"customer@example.com",
			"customer_inquiry_answered",
			"message=답변 내용"
		));

		notificationDispatchListener.dispatchNow(sentLog.getId());

		NotificationLog sent = notificationLogRepository.findById(sentLog.getId()).orElseThrow();
		assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(sent.getPayloadSnapshot()).doesNotContain("#token=");
		assertThat(emailSender.messages()).singleElement().asString()
			.contains("customer@example.com", "답변 내용", "/support/inquiries/" + inquiryId, "#token=");

		emailSender.fail("SES timeout");
		NotificationLog failedLog = notificationLogRepository.saveAndFlush(new NotificationLog(
			null,
			null,
			null,
			null,
			null,
			UUID.randomUUID(),
			NotificationType.CUSTOMER_INQUIRY_ANSWERED,
			NotificationChannel.EMAIL,
			"failed@example.com",
			"customer_inquiry_answered",
			"message=실패 답변"
		));

		notificationDispatchListener.dispatchNow(failedLog.getId());

		assertThat(notificationLogRepository.findById(failedLog.getId()).orElseThrow())
			.satisfies(log -> {
				assertThat(log.getStatus()).isEqualTo(NotificationStatus.FAILED);
				assertThat(log.getFailureReason()).contains("SES timeout");
			});
	}

	@Test
	void supplierOperationalDispatchRevalidatesAuthorizationAndExactRecipient() {
		Supplier supplier = createActivePortalSupplier("dispatch-revalidation");
		NotificationLog mismatchedRecipient = notificationLogRepository.saveAndFlush(
			operationalLog(supplier, "old-contact@example.com")
		);
		NotificationLog missingSupplier = notificationLogRepository.saveAndFlush(NotificationLog.supplierOperational(
			UUID.randomUUID(), UUID.randomUUID(), null,
			NotificationType.SUPPLIER_FULFILLMENT_REQUESTED,
			"missing-supplier@example.com",
			"supplier_fulfillment_requested",
			"event=FULFILLMENT_REQUESTED, orderNumber=SAFE-2, portalPath=/supplier/orders/SAFE-2"
		));

		notificationDispatchListener.dispatchNow(mismatchedRecipient.getId());
		notificationDispatchListener.dispatchNow(missingSupplier.getId());

		assertThat(emailSender.messages()).isEmpty();
		assertThat(notificationLogRepository.findById(mismatchedRecipient.getId()).orElseThrow())
			.satisfies(log -> {
				assertThat(log.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
				assertThat(log.getFailureReason()).isEqualTo("SUPPLIER_AUTHORIZATION_CHANGED");
				assertThat(log.getRecipientRetentionExpiresAt()).isNotNull();
			});
		assertThat(notificationLogRepository.findById(missingSupplier.getId()).orElseThrow().getStatus())
			.isEqualTo(NotificationStatus.SKIPPED);
	}

	@Test
	void supplierOperationalProviderFailureStoresOnlyRedactedCode() {
		Supplier supplier = createActivePortalSupplier("redacted-provider-failure");
		NotificationLog log = notificationLogRepository.saveAndFlush(operationalLog(supplier, supplier.getEmail()));
		emailSender.fail("SES rejected customer@example.com at 010-9999-0000");

		notificationDispatchListener.dispatchNow(log.getId());

		assertThat(notificationLogRepository.findById(log.getId()).orElseThrow())
			.satisfies(failed -> {
				assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
				assertThat(failed.getFailureReason()).isEqualTo("EMAIL_PROVIDER_FAILURE");
				assertThat(failed.getFailureReason()).doesNotContain("customer@example.com", "010-9999-0000");
				assertThat(failed.getRecipientRetentionExpiresAt())
					.isEqualTo(failed.getCreatedAt().plus(37, ChronoUnit.DAYS));
				});
	}

	@Test
	void supplierOperationalUnsuccessfulResultUsesAllowlistedSkipCode() {
		Supplier supplier = createActivePortalSupplier("provider-skipped-result");
		NotificationLog log = notificationLogRepository.saveAndFlush(operationalLog(supplier, supplier.getEmail()));
		emailSender.skip("SES suppressed private@example.com");

		notificationDispatchListener.dispatchNow(log.getId());

		assertThat(notificationLogRepository.findById(log.getId()).orElseThrow())
			.satisfies(skipped -> {
				assertThat(skipped.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
				assertThat(skipped.getFailureReason()).isEqualTo("EMAIL_PROVIDER_SKIPPED");
				assertThat(skipped.getFailureReason()).doesNotContain("private@example.com");
				assertThat(skipped.getRecipientRetentionExpiresAt()).isNotNull();
			});
		assertThat(emailSender.messages()).hasSize(1);
	}

	@Test
	void recoversCommittedPendingSupplierOperationalOutboxRow() {
		Supplier supplier = createActivePortalSupplier("pending-outbox-recovery");
		NotificationLog pending = notificationLogRepository.saveAndFlush(
			operationalLog(supplier, supplier.getEmail())
		);

		assertThat(supplierOperationalNotificationScheduler.recoverPendingBatch()).isGreaterThanOrEqualTo(1);

		assertThat(notificationLogRepository.findById(pending.getId()).orElseThrow().getStatus())
			.isEqualTo(NotificationStatus.SENT);
		assertThat(emailSender.messages()).anySatisfy(message ->
			assertThat(message).contains(supplier.getEmail(), "[코어블SAF] 새 출고 요청", "/supplier/orders")
		);
	}

	@Test
	void terminalizesExpiredPendingSupplierOperationalWithoutSending() {
		Supplier supplier = createActivePortalSupplier("expired-pending-outbox");
		NotificationLog pending = notificationLogRepository.saveAndFlush(
			operationalLog(supplier, supplier.getEmail())
		);
		Instant backdatedCreatedAt = Instant.now().minus(8, ChronoUnit.DAYS);
		jdbcTemplate.update(
			"update notification_logs set created_at = ? where id = ?",
			Timestamp.from(backdatedCreatedAt), pending.getId()
		);
		emailSender.reset();

		notificationDispatchListener.dispatchNow(pending.getId());

		assertThat(emailSender.messages()).isEmpty();
		assertThat(notificationLogRepository.findById(pending.getId()).orElseThrow())
			.satisfies(expired -> {
				assertThat(expired.getStatus()).isEqualTo(NotificationStatus.FAILED);
				assertThat(expired.getFailureReason()).isEqualTo("DELIVERY_WINDOW_EXPIRED");
				assertThat(expired.getRecipientRetentionExpiresAt())
					.isEqualTo(expired.getCreatedAt().plus(37, ChronoUnit.DAYS));
			});
	}

	@Test
	void supplierOperationalRetryIsFailedOnlyWithinSevenDaysAndRetentionClearsRecipient() throws Exception {
		Supplier supplier = createActivePortalSupplier("retry-retention");
		NotificationLog retryable = notificationLogRepository.saveAndFlush(operationalLog(supplier, supplier.getEmail()));
		retryable.markFailed("EMAIL_PROVIDER_FAILURE");
		retryable.scheduleOperationalCleanup(Instant.now());
		notificationLogRepository.saveAndFlush(retryable);

		mockMvc.perform(post("/api/admin/notifications/{notificationId}/retry", retryable.getId())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk());

		assertThat(notificationLogRepository.findById(retryable.getId()).orElseThrow())
			.satisfies(sent -> {
				assertThat(sent.getStatus()).isEqualTo(NotificationStatus.SENT);
				assertThat(sent.getFailureReason()).isNull();
				assertThat(sent.getRecipientRetentionExpiresAt()).isNotNull();
			});

		NotificationLog expired = notificationLogRepository.saveAndFlush(operationalLog(supplier, supplier.getEmail()));
		expired.markFailed("EMAIL_PROVIDER_FAILURE");
		expired.scheduleOperationalCleanup(Instant.now());
		notificationLogRepository.saveAndFlush(expired);
		jdbcTemplate.update(
			"update notification_logs set created_at = ? where id = ?",
			Timestamp.from(Instant.now().minus(8, ChronoUnit.DAYS)), expired.getId()
		);

		mockMvc.perform(post("/api/admin/notifications/{notificationId}/retry", expired.getId())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isConflict());
		assertThat(notificationLogRepository.findById(expired.getId()).orElseThrow().getStatus())
			.isEqualTo(NotificationStatus.FAILED);

		NotificationLog retained = notificationLogRepository.saveAndFlush(operationalLog(supplier, supplier.getEmail()));
		retained.markFailed("EMAIL_PROVIDER_FAILURE");
		retained.scheduleOperationalCleanup(Instant.now());
		notificationLogRepository.saveAndFlush(retained);
		Instant cleanupAt = retained.getRecipientRetentionExpiresAt();
		assertThat(supplierNotificationRetentionService.candidateIds(cleanupAt)).contains(retained.getId());
		assertThat(supplierNotificationRetentionService.cleanup(retained.getId(), cleanupAt)).isTrue();
		assertThat(notificationLogRepository.findById(retained.getId()).orElseThrow())
			.satisfies(cleaned -> {
				assertThat(cleaned.getRecipient()).isNull();
				assertThat(cleaned.getFailureReason()).isNull();
				assertThat(cleaned.getRecipientAnonymizedAt())
					.isCloseTo(cleanupAt, within(1, ChronoUnit.MICROS));
			});
	}

	@Test
	void supplierOperationalRetryRevalidatesPortalLifecycleBeforeSending() throws Exception {
		Supplier supplier = createActivePortalSupplier("retry-lifecycle-revalidation");
		NotificationLog failed = notificationLogRepository.saveAndFlush(
			operationalLog(supplier, supplier.getEmail())
		);
		failed.markFailed("EMAIL_PROVIDER_FAILURE");
		failed.scheduleOperationalCleanup(Instant.now());
		notificationLogRepository.saveAndFlush(failed);
		supplier.suspendPortal(SupplierSalesAction.KEEP);
		supplierRepository.saveAndFlush(supplier);

		mockMvc.perform(post("/api/admin/notifications/{notificationId}/retry", failed.getId())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk());

		assertThat(emailSender.messages()).isEmpty();
		assertThat(notificationLogRepository.findById(failed.getId()).orElseThrow())
			.satisfies(skipped -> {
				assertThat(skipped.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
				assertThat(skipped.getFailureReason()).isEqualTo("SUPPLIER_AUTHORIZATION_CHANGED");
				assertThat(skipped.getRecipientRetentionExpiresAt()).isNotNull();
			});
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

	private Supplier createActivePortalSupplier(String suffix) {
		UserAccount manager = userAccountRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO,
			"supplier-notification-manager-" + suffix,
			"manager-" + suffix + "@example.com",
			"Supplier manager",
			UserRole.CUSTOMER
		));
		Instant now = Instant.now();
		Supplier supplier = Supplier.portalApplicant(
			"Supplier " + suffix,
			"Manager",
			"010-0000-0000",
			manager.getEmail(),
			null
		);
		supplier.verifyPortalContract(
			"notification-contract-" + suffix,
			now.minusSeconds(60),
			now.plus(30, ChronoUnit.DAYS),
			now,
			TestAuthentication.ADMIN_ID
		);
		supplier.changeSalesStatus(SupplierStatus.ACTIVE, now);
		supplier.bindManager(manager.getId(), now);
		return supplierRepository.saveAndFlush(supplier);
	}

	private NotificationLog operationalLog(Supplier supplier, String recipient) {
		return NotificationLog.supplierOperational(
			supplier.getId(),
			UUID.randomUUID(),
			null,
			NotificationType.SUPPLIER_FULFILLMENT_REQUESTED,
			recipient,
			"supplier_fulfillment_requested",
			"event=FULFILLMENT_REQUESTED, orderNumber=SAFE-1, portalPath=/supplier/orders/SAFE-1"
		);
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

		@Bean
		@Primary
		RecordingEmailSender recordingEmailSender() {
			return new RecordingEmailSender();
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

	static class RecordingEmailSender implements EmailSender {

		private final List<String> messages = new ArrayList<>();
		private String failureMessage;
		private String skippedReason;

		@Override
		public EmailSendResult sendTransactional(String recipient, String subject, String body) {
			if (failureMessage != null) {
				throw new IllegalStateException(failureMessage);
			}
			messages.add(recipient + "|" + subject + "|" + body);
			return skippedReason == null ? EmailSendResult.sent() : EmailSendResult.skipped(skippedReason);
		}

		void fail(String message) {
			failureMessage = message;
		}

		void skip(String reason) {
			skippedReason = reason;
		}

		void reset() {
			failureMessage = null;
			skippedReason = null;
			messages.clear();
		}

		List<String> messages() {
			return messages;
		}
	}
}
