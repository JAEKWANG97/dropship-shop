package com.dropshipshop.api.supplierfulfillment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dropshipshop.api.auth.security.AuthenticatedUser;
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
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.claim.domain.ClaimReason;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.domain.ClaimType;
import com.dropshipshop.api.claim.domain.RequestedAction;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.fulfillment.SupplierFulfillmentHandoverService;
import com.dropshipshop.api.fulfillment.domain.FulfillmentHandoverReasonCode;
import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;
import com.dropshipshop.api.notification.NotificationLogRepository;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.refund.domain.RefundReason;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.supplierportal.repository.FulfillmentHandoverHistoryRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest(properties = {
	"app.supplier-portal.enabled=true",
	"app.cors.allowed-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierFulfillmentApiIntegrationTest {

	@Autowired MockMvc mockMvc;
	@Autowired UserAccountRepository userRepository;
	@Autowired SupplierRepository supplierRepository;
	@Autowired ProductRepository productRepository;
	@Autowired ProductOptionRepository optionRepository;
	@Autowired PaymentGroupRepository paymentGroupRepository;
	@Autowired CustomerOrderRepository orderRepository;
	@Autowired OrderItemRepository orderItemRepository;
	@Autowired FulfillmentRepository fulfillmentRepository;
	@Autowired ClaimRepository claimRepository;
	@Autowired SupplierPiiAccessLogRepository accessLogRepository;
	@Autowired SupplierPiiAccessGrantRepository grantRepository;
	@Autowired SupplierFulfillmentHandoverService handoverService;
	@Autowired FulfillmentHandoverHistoryRepository handoverHistoryRepository;
	@Autowired RefundRepository refundRepository;
	@Autowired NotificationLogRepository notificationLogRepository;

	@Test
	void enforcesSupplierAndAdminRoleBoundariesForEveryB103PrivacyEndpoint() throws Exception {
		Fixture fixture = paidPortalOrder("endpoint-role-matrix");

		mockMvc.perform(get("/api/supplier/orders"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/supplier/orders")
				.with(authentication(customer(fixture.customer().getId()))))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/supplier/orders")
				.with(authentication(admin(fixture.admin().getId()))))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber())
				.with(authentication(customer(fixture.customer().getId()))))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber())
				.with(authentication(admin(fixture.admin().getId()))))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/admin/supplier-pii-access-logs"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/admin/supplier-pii-access-logs")
				.with(authentication(customer(fixture.customer().getId()))))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/supplier-pii-access-logs")
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isForbidden());

		String takeoverBody = "{\"reason\":\"COREABLE_FULFILLMENT_TAKEOVER\"}";
		mockMvc.perform(post("/api/admin/orders/{orderId}/portal-takeover", fixture.order().getId())
				.header("Idempotency-Key", "takeover-unauthorized-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(takeoverBody))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/admin/orders/{orderId}/portal-takeover", fixture.order().getId())
				.header("Idempotency-Key", "takeover-customer-forbidden-1")
				.with(authentication(customer(fixture.customer().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(takeoverBody))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/orders/{orderId}/portal-takeover", fixture.order().getId())
				.header("Idempotency-Key", "takeover-supplier-forbidden-1")
				.with(authentication(supplier(fixture.manager().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(takeoverBody))
			.andExpect(status().isForbidden());

		String grantBody = """
			{"action":"GRANTED","expectedLatestGrantId":null,"accessUntil":"%s","reason":"RETURN_COORDINATION_REQUIRED"}
			""".formatted(Instant.now().plus(10, ChronoUnit.DAYS));
		assertAdminOnlyClaimMutation("/api/admin/claims/{claimId}/supplier-pii-access-grants",
			fixture, grantBody, "grant-role-matrix");
		String revokeBody = """
			{"expectedLatestGrantId":"%s","reason":"CLAIM_ACCESS_NO_LONGER_REQUIRED"}
			""".formatted(UUID.randomUUID());
		assertAdminOnlyClaimMutation("/api/admin/claims/{claimId}/supplier-pii-access-grants/revoke",
			fixture, revokeBody, "revoke-role-matrix");
	}

	@Test
	void exposesPiiFreeListAndNoStoreMinimumPiiDetailThenMasksTerminalWork() throws Exception {
		Fixture fixture = paidPortalOrder("minimum-pii");

		MvcResult list = mockMvc.perform(get("/api/supplier/orders")
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orders[0].orderNumber", is(fixture.order().getOrderNumber())))
			.andExpect(jsonPath("$.orders[0].status", is("FULFILLMENT_REQUESTED")))
			.andReturn();
		assertThat(list.getResponse().getContentAsString())
			.doesNotContain("recipient", "phone", "address", "deliveryMemo", "payment", "refund");

		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber())
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.piiAccessLevel", is("FULL")))
			.andExpect(jsonPath("$.piiBasis", is("NORMAL_WINDOW")))
			.andExpect(jsonPath("$.recipient.name", is("Receiver")))
			.andExpect(jsonPath("$.recipient.phone", is("01011112222")))
			.andExpect(jsonPath("$.recipient.deliveryMemo", is("Leave at door")))
			.andExpect(jsonPath("$.items[0].allocatedQuantity", is(0)))
			.andExpect(jsonPath("$.items[0].remainingQuantity", is(1)));
		assertThat(accessLogRepository.findAllByOrder_IdOrderByAccessedAtDesc(fixture.order().getId())).hasSize(1);

		mockMvc.perform(post("/api/admin/orders/{orderId}/supplier-work-start", fixture.order().getId())
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Legacy supplier work\"}"))
			.andExpect(status().isConflict());
		mockMvc.perform(post("/api/admin/orders/{orderId}/supplier-order-completed", fixture.order().getId())
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"supplierOrderNumber":"LEGACY-1","supplierResponseMemo":"Memo","reason":"Legacy completion"}
					"""))
			.andExpect(status().isConflict());
		mockMvc.perform(post("/api/admin/orders/{orderId}/shipments", fixture.order().getId())
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"carrier\":\"CJ_LOGISTICS\",\"trackingNumber\":\"1234567890\"}"))
			.andExpect(status().isConflict());

		approveSupplierShortage(fixture, "minimum-pii-terminal");

		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber())
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.piiAccessLevel", is("MASKED")))
			.andExpect(jsonPath("$.piiBasis", is("TERMINAL_STATE")))
			.andExpect(jsonPath("$.recipient.name", is("R**")))
			.andExpect(jsonPath("$.recipient.phone", is("*******2222")))
			.andExpect(jsonPath("$.recipient.postalCode").doesNotExist())
			.andExpect(jsonPath("$.recipient.address1").doesNotExist())
			.andExpect(jsonPath("$.recipient.deliveryMemo").doesNotExist());
		assertThat(accessLogRepository.findAllByOrder_IdOrderByAccessedAtDesc(fixture.order().getId()))
			.extracting(SupplierPiiAccessLog::getAccessReason)
			.containsExactly(SupplierPiiAccessReason.TERMINAL_MASKED, SupplierPiiAccessReason.NORMAL_FULL);
	}

	@Test
	void excludesPaymentExceptionFulfillmentArtifactFromSupplierReadsWithoutLogging() throws Exception {
		Fixture fixture = paidPortalOrder("payment-exception-artifact");
		PaymentGroup paymentGroup = paymentGroupRepository.findById(fixture.order().getPaymentGroup().getId())
			.orElseThrow();
		paymentGroup.markPaymentException();
		paymentGroupRepository.saveAndFlush(paymentGroup);
		assertThat(fulfillmentRepository.findByOrder_Id(fixture.order().getId())).isPresent();

		MvcResult list = mockMvc.perform(get("/api/supplier/orders")
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(list.getResponse().getContentAsString())
			.doesNotContain(fixture.order().getOrderNumber());
		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber())
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isNotFound());
		assertThat(accessLogRepository.findAllByOrder_IdOrderByAccessedAtDesc(fixture.order().getId())).isEmpty();
	}

	@Test
	void returnsOnlyCurrentSupplierPortalItemsWithStableOrderItemIds() throws Exception {
		Fixture fixture = paidPortalOrder("item-allowlist");
		OrderItem ownPortalItem = orderItemRepository
			.findAllByOrder_IdOrderByCreatedAtAsc(fixture.order().getId()).getFirst();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Supplier foreignSupplier = supplierRepository.save(Supplier.portalApplicant(
			"Foreign supplier " + suffix, "Manager", "010-3333-4444",
			"foreign-" + suffix + "@example.com", null));
		Product foreignPortalProduct = productRepository.save(new Product(
			foreignSupplier, "Foreign portal item " + suffix, "Must not be returned", 10_000, 12_000,
			ProductCategory.PPE_SAFETY_HELMET, ProductStatus.ACTIVE, ProductManagementChannel.SUPPLIER_PORTAL));
		ProductOption foreignPortalOption = new ProductOption(
			foreignPortalProduct, "Foreign option", 0, ProductOptionStatus.ACTIVE);
		foreignPortalOption.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.UNTRACKED, null);
		foreignPortalOption = optionRepository.save(foreignPortalOption);
		orderItemRepository.save(new OrderItem(
			fixture.order(), foreignPortalProduct, foreignPortalOption, 1, 1, Instant.now()));

		Product coreableProduct = productRepository.save(new Product(
			fixture.supplier(), "Coreable item " + suffix, "Must not be returned", 10_000, 12_000,
			ProductCategory.PPE_SAFETY_HELMET, ProductStatus.ACTIVE, ProductManagementChannel.COREABLE));
		ProductOption coreableOption = optionRepository.save(new ProductOption(
			coreableProduct, "Coreable option", 0, ProductOptionStatus.ACTIVE));
		orderItemRepository.saveAndFlush(new OrderItem(
			fixture.order(), coreableProduct, coreableOption, 1, 1, Instant.now()));

		MvcResult list = mockMvc.perform(get("/api/supplier/orders")
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(list.getResponse().getContentAsString())
			.contains(fixture.order().getOrderNumber(), ownPortalItem.getProductName())
			.doesNotContain(foreignPortalProduct.getName(), coreableProduct.getName());
		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber())
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()", is(1)))
			.andExpect(jsonPath("$.items[0].orderItemId", is(ownPortalItem.getId().toString())))
			.andExpect(jsonPath("$.items[0].productName", is(ownPortalItem.getProductName())));
	}

	@Test
	void cancellationApprovalImmediatelyHandsPortalFulfillmentToCoreable() throws Exception {
		Fixture fixture = paidPortalOrder("cancel-terminal-handover");
		Claim claim = claimRepository.saveAndFlush(new Claim(
			fixture.order(), fixture.customer(), ClaimType.CANCEL, ClaimReason.DEFECT,
			ClaimStatus.REQUESTED, RequestedAction.REFUND, "Customer-safe claim memo"
		));

		mockMvc.perform(post("/api/admin/claims/{claimId}/approve", claim.getId())
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Cancellation approved\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderStatus", is("REFUND_REQUESTED")));

		assertTerminalHandover(fixture);
	}

	@Test
	void returnRefundStartImmediatelyHandsPortalFulfillmentToCoreable() throws Exception {
		Fixture fixture = paidPortalOrder("return-terminal-handover");
		Claim claim = claimRepository.saveAndFlush(new Claim(
			fixture.order(), fixture.customer(), ClaimType.RETURN, ClaimReason.DEFECT,
			ClaimStatus.RETURN_RECEIVED, RequestedAction.REFUND, "Customer-safe claim memo"
		));

		mockMvc.perform(post("/api/admin/claims/{claimId}/return-refund", claim.getId())
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Return inspection completed\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderStatus", is("REFUND_REQUESTED")));

		assertTerminalHandover(fixture);
	}

	@Test
	void manualRefundCompletionImmediatelyHandsPortalFulfillmentToCoreable() throws Exception {
		Fixture fixture = paidPortalOrder("manual-refund-terminal-handover");
		Refund refund = new Refund(fixture.order(), RefundReason.CUSTOMER_CANCEL);
		refund.approve(fixture.admin().getId(), "Refund approved", Instant.now());
		refund = refundRepository.saveAndFlush(refund);

		mockMvc.perform(post("/api/admin/refunds/{refundId}/manual-complete", refund.getId())
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "transferredAmount": 12000,
					  "reason": "Manual refund completed",
					  "bankName": "Test bank",
					  "accountNumber": "123-456",
					  "accountHolder": "Receiver",
					  "transferredAt": "2020-07-19T09:00:00Z",
					  "transactionReference": "PORTAL-MANUAL-REFUND-1"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.orderStatus", is("REFUNDED")));

		assertTerminalHandover(fixture);
	}

	@Test
	void hidesCrossTenantAndAdminTakeoverAndDoesNotLogFailures() throws Exception {
		Fixture owner = paidPortalOrder("owner");
		Fixture other = paidPortalOrder("other");
		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", other.order().getOrderNumber())
				.with(authentication(supplier(owner.manager().getId()))))
			.andExpect(status().isNotFound());
		assertThat(accessLogRepository.findAllByOrder_IdOrderByAccessedAtDesc(other.order().getId())).isEmpty();
		mockMvc.perform(post("/api/admin/orders/{orderId}/portal-takeover", owner.order().getId())
				.header("Idempotency-Key", "portal-takeover-pii-reason-1")
				.with(authentication(admin(owner.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Address: 123 Main Street Apt 4B\"}"))
			.andExpect(status().isBadRequest());
		assertThat(fulfillmentRepository.findByOrder_Id(owner.order().getId()).orElseThrow()
			.getOperationalOwner().name()).isEqualTo("SUPPLIER");

		String body = "{\"reason\":\"COREABLE_FULFILLMENT_TAKEOVER\"}";
		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(post("/api/admin/orders/{orderId}/portal-takeover", owner.order().getId())
					.header("Idempotency-Key", "portal-takeover-owner-1")
					.with(authentication(admin(owner.admin().getId())))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.operationalOwner", is("COREABLE")));
		}
		mockMvc.perform(post("/api/admin/orders/{orderId}/portal-takeover", owner.order().getId())
				.header("Idempotency-Key", "portal-takeover-owner-1")
				.with(authentication(admin(owner.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"SUPPLIER_SUPPORT_REQUIRED\"}"))
			.andExpect(status().isConflict());
		mockMvc.perform(post("/api/admin/orders/{orderId}/portal-takeover", owner.order().getId())
				.header("Idempotency-Key", "portal-takeover-owner-2")
				.with(authentication(admin(owner.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isConflict());
		var ownerFulfillment = fulfillmentRepository.findByOrder_Id(owner.order().getId()).orElseThrow();
		assertThat(handoverHistoryRepository.findAllByFulfillment_IdOrderByCreatedAtAsc(ownerFulfillment.getId()))
			.hasSize(1);
		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", owner.order().getOrderNumber())
				.with(authentication(supplier(owner.manager().getId()))))
			.andExpect(status().isNotFound());
		assertThat(accessLogRepository.findAllByOrder_IdOrderByAccessedAtDesc(owner.order().getId())).isEmpty();

		Claim claim = claimRepository.saveAndFlush(new Claim(
			owner.order(), owner.customer(), ClaimType.CANCEL, ClaimReason.DEFECT,
			ClaimStatus.APPROVED, RequestedAction.REFUND, "Customer-safe claim memo"
		));
		mockMvc.perform(post("/api/admin/claims/{claimId}/supplier-pii-access-grants", claim.getId())
				.header("Idempotency-Key", "claim-after-admin-takeover-1")
				.with(authentication(admin(owner.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"GRANTED","expectedLatestGrantId":null,"accessUntil":"%s","reason":"RETURN_COORDINATION_REQUIRED"}
					""".formatted(Instant.now().plus(10, ChronoUnit.DAYS))))
			.andExpect(status().isNotFound());
		assertThat(grantRepository.findByClaim_IdAndIdempotencyKey(
			claim.getId(), "claim-after-admin-takeover-1")).isEmpty();
	}

	@Test
	void cutoffIsInclusiveAndUnicodeAndShortPhoneAreMaskedDeterministically() throws Exception {
		Fixture fixture = paidPortalOrder("cutoff-mask", "🦕민", "12-34");
		var fulfillment = fulfillmentRepository.findByOrder_Id(fixture.order().getId()).orElseThrow();
		Instant exactCutoff = Instant.now().plus(1, ChronoUnit.DAYS);
		fulfillment.shortenPiiAccessCutoffAt(exactCutoff);
		fulfillmentRepository.saveAndFlush(fulfillment);
		assertThat(handoverService.enforceCutoffLazy(fulfillment.getId(), exactCutoff)).isTrue();
		assertThat(handoverService.enforceCutoffLazy(fulfillment.getId(), exactCutoff)).isFalse();

		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber())
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.piiAccessLevel", is("MASKED")))
			.andExpect(jsonPath("$.piiBasis", is("PII_CUTOFF")))
			.andExpect(jsonPath("$.recipient.name", is("🦕**")))
			.andExpect(jsonPath("$.recipient.phone", is("****")));
		assertThat(accessLogRepository.findAllByOrder_IdOrderByAccessedAtDesc(fixture.order().getId()))
			.extracting(SupplierPiiAccessLog::getAccessReason)
			.containsExactly(SupplierPiiAccessReason.EXPIRED_MASKED);

		CustomerOrder terminalOrder = orderRepository.findById(fixture.order().getId()).orElseThrow();
		terminalOrder.markOutOfStock();
		orderRepository.saveAndFlush(terminalOrder);
		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber())
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.piiBasis", is("TERMINAL_STATE")));
		assertThat(accessLogRepository.findAllByOrder_IdOrderByAccessedAtDesc(fixture.order().getId()))
			.extracting(SupplierPiiAccessLog::getAccessReason)
			.containsExactly(SupplierPiiAccessReason.TERMINAL_MASKED, SupplierPiiAccessReason.EXPIRED_MASKED);
	}

	@Test
	void detailGetLazilyHandsOverAnExpiredCutoffWithoutSchedulerHelp() throws Exception {
		Fixture fixture = paidPortalOrder("lazy-cutoff");
		var fulfillment = fulfillmentRepository.findByOrder_Id(fixture.order().getId()).orElseThrow();
		fulfillment.shortenPiiAccessCutoffAt(Instant.now().minusSeconds(1));
		fulfillmentRepository.saveAndFlush(fulfillment);

		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber())
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.piiAccessLevel", is("MASKED")))
			.andExpect(jsonPath("$.piiBasis", is("PII_CUTOFF")));

		assertThat(fulfillmentRepository.findByOrder_Id(fixture.order().getId()).orElseThrow()
			.getOperationalOwner().name()).isEqualTo("COREABLE");
		assertThat(accessLogRepository.findAllByOrder_IdOrderByAccessedAtDesc(fixture.order().getId()))
			.extracting(SupplierPiiAccessLog::getAccessReason)
			.containsExactly(SupplierPiiAccessReason.EXPIRED_MASKED);
	}

	@Test
	void inactivePortalReturnsForbiddenWithoutWritingPiiAccessLog() throws Exception {
		Fixture fixture = paidPortalOrder("inactive-portal");
		Supplier supplier = supplierRepository.findById(fixture.supplier().getId()).orElseThrow();
		supplier.suspendPortal(com.dropshipshop.api.catalog.domain.SupplierSalesAction.KEEP);
		supplierRepository.saveAndFlush(supplier);

		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber())
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isForbidden());
		assertThat(accessLogRepository.findAllByOrder_IdOrderByAccessedAtDesc(fixture.order().getId())).isEmpty();
	}

	@Test
	void grantRejectsDisallowedClaimAndExpiredContractWithoutWritingRowsOrAccessLogs() throws Exception {
		Fixture disallowed = paidPortalOrder("grant-disallowed-status");
		approveSupplierShortage(disallowed, "grant-disallowed-terminal");
		Claim rejected = claimRepository.saveAndFlush(new Claim(
			disallowed.order(), disallowed.customer(), ClaimType.CANCEL, ClaimReason.DEFECT,
			ClaimStatus.REJECTED, RequestedAction.REFUND, "Customer-safe claim memo"
		));
		mockMvc.perform(post("/api/admin/claims/{claimId}/supplier-pii-access-grants", rejected.getId())
				.header("Idempotency-Key", "claim-disallowed-status-1")
				.with(authentication(admin(disallowed.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"GRANTED","expectedLatestGrantId":null,"accessUntil":"%s","reason":"RETURN_COORDINATION_REQUIRED"}
					""".formatted(Instant.now().plus(10, ChronoUnit.DAYS))))
			.andExpect(status().isConflict());
		assertThat(grantRepository.findByClaim_IdAndIdempotencyKey(
			rejected.getId(), "claim-disallowed-status-1")).isEmpty();

		Fixture expired = paidPortalOrder("grant-expired-contract");
		approveSupplierShortage(expired, "grant-expired-terminal");
		Claim approved = claimRepository.saveAndFlush(new Claim(
			expired.order(), expired.customer(), ClaimType.CANCEL, ClaimReason.DEFECT,
			ClaimStatus.APPROVED, RequestedAction.REFUND, "Customer-safe claim memo"
		));
		Supplier expiredSupplier = supplierRepository.findById(expired.supplier().getId()).orElseThrow();
		assertThat(expiredSupplier.lazilyExpireContract(Instant.now().plus(91, ChronoUnit.DAYS))).isTrue();
		supplierRepository.saveAndFlush(expiredSupplier);
		mockMvc.perform(post("/api/admin/claims/{claimId}/supplier-pii-access-grants", approved.getId())
				.header("Idempotency-Key", "claim-expired-contract-1")
				.with(authentication(admin(expired.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"GRANTED","expectedLatestGrantId":null,"accessUntil":"%s","reason":"RETURN_COORDINATION_REQUIRED"}
					""".formatted(Instant.now().plus(10, ChronoUnit.DAYS))))
			.andExpect(status().isForbidden());
		assertThat(grantRepository.findByClaim_IdAndIdempotencyKey(
			approved.getId(), "claim-expired-contract-1")).isEmpty();
		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", expired.order().getOrderNumber())
				.with(authentication(supplier(expired.manager().getId()))))
			.andExpect(status().isForbidden());
		assertThat(accessLogRepository.findAllByOrder_IdOrderByAccessedAtDesc(expired.order().getId())).isEmpty();
	}

	@Test
	void claimGrantReopensReadOnlyFullAccessAndRevokeMasksAgain() throws Exception {
		Fixture fixture = paidPortalOrder("claim-grant");
		approveSupplierShortage(fixture, "claim-grant-terminal");
		Claim claim = claimRepository.saveAndFlush(new Claim(
			fixture.order(), fixture.customer(), ClaimType.CANCEL, ClaimReason.DEFECT,
			ClaimStatus.APPROVED, RequestedAction.REFUND, "Customer-safe claim memo"
		));
		Instant deadline = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
		mockMvc.perform(post("/api/admin/claims/{claimId}/supplier-pii-access-grants", claim.getId())
				.header("Idempotency-Key", "claim-grant-too-long-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"GRANTED","expectedLatestGrantId":null,"accessUntil":"%s","reason":"RETURN_COORDINATION_REQUIRED"}
					""".formatted(Instant.now().plus(31, ChronoUnit.DAYS))))
			.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/admin/claims/{claimId}/supplier-pii-access-grants", claim.getId())
				.header("Idempotency-Key", "claim-grant-pii-reason-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"GRANTED","expectedLatestGrantId":null,"accessUntil":"%s","reason":"Delivery memo says leave at door"}
					""".formatted(deadline)))
			.andExpect(status().isBadRequest());
		assertThat(grantRepository.findByClaim_IdAndIdempotencyKey(
			claim.getId(), "claim-grant-pii-reason-1")).isEmpty();
		MvcResult grant = mockMvc.perform(post("/api/admin/claims/{claimId}/supplier-pii-access-grants", claim.getId())
				.header("Idempotency-Key", "claim-grant-command-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"GRANTED","expectedLatestGrantId":null,"accessUntil":"%s","reason":"RETURN_COORDINATION_REQUIRED"}
					""".formatted(deadline)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.action", is("GRANTED")))
			.andReturn();
		String grantId = com.jayway.jsonpath.JsonPath.read(grant.getResponse().getContentAsString(), "$.grantId");
		assertThat(notificationLogRepository.findAllByOrderByCreatedAtAsc())
			.filteredOn(log -> claim.getId().equals(log.getClaimId()))
			.filteredOn(log -> log.getType() == NotificationType.SUPPLIER_CLAIM_WORK_REQUESTED)
			.isEmpty();
		mockMvc.perform(post("/api/admin/claims/{claimId}/supplier-pii-access-grants", claim.getId())
				.header("Idempotency-Key", "claim-grant-command-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"GRANTED","expectedLatestGrantId":null,"accessUntil":"%s","reason":"RETURN_COORDINATION_REQUIRED"}
					""".formatted(deadline)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grantId", is(grantId)));
		mockMvc.perform(post("/api/admin/claims/{claimId}/supplier-pii-access-grants", claim.getId())
				.header("Idempotency-Key", "claim-grant-command-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"GRANTED","expectedLatestGrantId":null,"accessUntil":"%s","reason":"EXCHANGE_COORDINATION_REQUIRED"}
					""".formatted(deadline)))
			.andExpect(status().isConflict());

		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber())
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.piiAccessLevel", is("FULL")))
			.andExpect(jsonPath("$.piiBasis", is("CLAIM_GRANT")))
			.andExpect(jsonPath("$.recipient.address1", is("Seoul test road")));

		Instant extendedDeadline = Instant.now().plus(15, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
		mockMvc.perform(post(
				"/api/admin/claims/{claimId}/supplier-pii-access-grants", claim.getId())
				.header("Idempotency-Key", "claim-extend-stale-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"EXTENDED","expectedLatestGrantId":"%s","accessUntil":"%s","reason":"RETURN_COORDINATION_REQUIRED"}
					""".formatted(UUID.randomUUID(), extendedDeadline)))
			.andExpect(status().isConflict());
		assertThat(grantRepository.findByClaim_IdAndIdempotencyKey(
			claim.getId(), "claim-extend-stale-1")).isEmpty();
		MvcResult extension = mockMvc.perform(post(
				"/api/admin/claims/{claimId}/supplier-pii-access-grants", claim.getId())
				.header("Idempotency-Key", "claim-extend-command-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"action":"EXTENDED","expectedLatestGrantId":"%s","accessUntil":"%s","reason":"RETURN_COORDINATION_REQUIRED"}
					""".formatted(grantId, extendedDeadline)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.action", is("EXTENDED")))
			.andReturn();
		String extensionId = com.jayway.jsonpath.JsonPath.read(extension.getResponse().getContentAsString(), "$.grantId");

		MvcResult revoke = mockMvc.perform(post(
				"/api/admin/claims/{claimId}/supplier-pii-access-grants/revoke", claim.getId())
				.header("Idempotency-Key", "claim-revoke-command-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedLatestGrantId":"%s","reason":"CLAIM_ACCESS_NO_LONGER_REQUIRED"}
					""".formatted(extensionId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.action", is("REVOKED")))
			.andReturn();
		String revokeId = com.jayway.jsonpath.JsonPath.read(revoke.getResponse().getContentAsString(), "$.grantId");
		mockMvc.perform(post(
				"/api/admin/claims/{claimId}/supplier-pii-access-grants/revoke", claim.getId())
				.header("Idempotency-Key", "claim-revoke-command-1")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedLatestGrantId":"%s","reason":"CLAIM_ACCESS_NO_LONGER_REQUIRED"}
					""".formatted(extensionId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.grantId", is(revokeId)));

		mockMvc.perform(get("/api/supplier/orders/{orderNumber}", fixture.order().getOrderNumber())
				.with(authentication(supplier(fixture.manager().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.piiAccessLevel", is("MASKED")));
		mockMvc.perform(post("/api/admin/claims/{claimId}/supplier-pii-access-grants/revoke", claim.getId())
				.header("Idempotency-Key", "claim-revoke-command-2")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedLatestGrantId":"%s","reason":"CLAIM_ACCESS_NO_LONGER_REQUIRED"}
					""".formatted(revokeId)))
			.andExpect(status().isConflict());

		mockMvc.perform(get("/api/admin/supplier-pii-access-logs")
				.with(authentication(admin(fixture.admin().getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.logs[0].orderNumber").isString())
			.andExpect(jsonPath("$.logs[0].actorUserId").isString());
	}

	private void assertTerminalHandover(Fixture fixture) {
		var fulfillment = fulfillmentRepository.findByOrder_Id(fixture.order().getId()).orElseThrow();
		assertThat(fulfillment.getOperationalOwner()).isEqualTo(FulfillmentOperationalOwner.COREABLE);
		assertThat(handoverHistoryRepository.findAllByFulfillment_IdOrderByCreatedAtAsc(fulfillment.getId()))
			.extracting(history -> history.getReasonCode())
			.containsExactly(FulfillmentHandoverReasonCode.TERMINAL_STATE);
	}

	private void approveSupplierShortage(Fixture fixture, String keyPrefix) throws Exception {
		MvcResult submitted = mockMvc.perform(post(
				"/api/supplier/orders/{orderNumber}/shortage-reports", fixture.order().getOrderNumber())
				.header(HttpHeaders.ORIGIN, "http://localhost:3000")
				.header("Idempotency-Key", keyPrefix + "-submit")
				.with(authentication(supplier(fixture.manager().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reasonCode\":\"OUT_OF_STOCK\"}"))
			.andExpect(status().isOk())
			.andReturn();
		String reportId = com.jayway.jsonpath.JsonPath.read(
			submitted.getResponse().getContentAsString(), "$.reportId");
		mockMvc.perform(post("/api/admin/supplier-shortage-reports/{reportId}/approve", reportId)
				.header(HttpHeaders.ORIGIN, "http://localhost:3000")
				.header("Idempotency-Key", keyPrefix + "-approve")
				.with(authentication(admin(fixture.admin().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"expectedStatus\":\"REPORTED\",\"reviewReasonCode\":\"SHORTAGE_CONFIRMED\"}"))
			.andExpect(status().isOk());
	}

	private void assertAdminOnlyClaimMutation(
		String path,
		Fixture fixture,
		String body,
		String keyPrefix
	) throws Exception {
		UUID claimId = UUID.randomUUID();
		mockMvc.perform(post(path, claimId)
				.header("Idempotency-Key", keyPrefix + "-unauthorized")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post(path, claimId)
				.header("Idempotency-Key", keyPrefix + "-customer")
				.with(authentication(customer(fixture.customer().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());
		mockMvc.perform(post(path, claimId)
				.header("Idempotency-Key", keyPrefix + "-supplier")
				.with(authentication(supplier(fixture.manager().getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());
	}

	private Fixture paidPortalOrder(String label) throws Exception {
		return paidPortalOrder(label, "Receiver", "010-1111-2222");
	}

	private Fixture paidPortalOrder(String label, String recipientName, String recipientPhone) throws Exception {
		String suffix = label + "-" + UUID.randomUUID().toString().substring(0, 8);
		UserAccount admin = userRepository.save(new UserAccount(
			SocialProvider.KAKAO, "admin-" + suffix, "admin-" + suffix + "@example.com", "Admin", UserRole.ADMIN));
		UserAccount manager = userRepository.save(new UserAccount(
			SocialProvider.KAKAO, "manager-" + suffix, "manager-" + suffix + "@example.com", "Manager", UserRole.CUSTOMER));
		UserAccount customer = userRepository.save(new UserAccount(
			SocialProvider.GOOGLE, "customer-" + suffix, "customer-" + suffix + "@example.com", "Customer", UserRole.CUSTOMER));
		Instant now = Instant.now();
		Supplier supplier = Supplier.portalApplicant(
			"Supplier " + suffix, "Manager", "010-2222-3333", manager.getEmail(), null);
		supplier.verifyPortalContract("contract-" + suffix, now.minusSeconds(60), now.plus(90, ChronoUnit.DAYS),
			now, admin.getId());
		supplier.bindManager(manager.getId(), now);
		supplier.changeSalesStatus(SupplierStatus.ACTIVE, now);
		supplier = supplierRepository.save(supplier);
		Product product = new Product(supplier, "Product " + suffix, "Portal product", 10_000, 12_000,
			ProductCategory.PPE_WORK_GLOVES, ProductStatus.ACTIVE, ProductManagementChannel.SUPPLIER_PORTAL);
		product.updateReview(ProductReviewStatus.APPROVED, null, null);
		product = productRepository.save(product);
		ProductOption option = new ProductOption(product, "Default", 0, ProductOptionStatus.ACTIVE);
		option.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 5L);
		option.reserve(1);
		option = optionRepository.save(option);
		PaymentGroup paymentGroup = new PaymentGroup("P-" + UUID.randomUUID(), customer, 12_000,
			now.plusSeconds(3600));
		paymentGroup.confirmPolicy(now);
		paymentGroup = paymentGroupRepository.save(paymentGroup);
		CustomerOrder order = orderRepository.save(new CustomerOrder(
			"O-" + UUID.randomUUID(), customer, supplier, paymentGroup,
			new ShippingAddressSnapshot(
				recipientName, recipientPhone, "12345", "Seoul test road", "101", "Leave at door"),
			12_000, paymentGroup.getExpiresAt()
		));
		orderItemRepository.save(new OrderItem(order, product, option, 1, 1, now));

		mockMvc.perform(post("/api/admin/orders/{orderId}/confirm-deposit", order.getId())
				.header("Idempotency-Key", "deposit-" + suffix)
				.with(authentication(admin(admin.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "actualDepositorName":"Receiver",
					  "actualAmount":12000,
					  "depositedAt":"%s",
					  "transactionReference":"REF-%s",
					  "reason":"Bank receipt verified"
					}
					""".formatted(now.minusSeconds(30), suffix)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fulfillment.channel", is("SUPPLIER_PORTAL")));
		CustomerOrder storedOrder = orderRepository.findById(order.getId()).orElseThrow();
		assertThat(fulfillmentRepository.findByOrder_Id(order.getId())).isPresent();
		return new Fixture(admin, manager, customer, supplier, storedOrder);
	}

	private Authentication supplier(UUID userId) {
		return authWith(userId, UserRole.CUSTOMER, "ROLE_SUPPLIER");
	}

	private Authentication customer(UUID userId) {
		return authWith(userId, UserRole.CUSTOMER, "ROLE_CUSTOMER");
	}

	private Authentication admin(UUID userId) {
		return authWith(userId, UserRole.ADMIN, "ROLE_ADMIN");
	}

	private Authentication authWith(UUID userId, UserRole role, String authority) {
		return new UsernamePasswordAuthenticationToken(
			new AuthenticatedUser(userId, role), null, List.of(new SimpleGrantedAuthority(authority))
		);
	}

	private record Fixture(
		UserAccount admin,
		UserAccount manager,
		UserAccount customer,
		Supplier supplier,
		CustomerOrder order
	) {
	}
}
