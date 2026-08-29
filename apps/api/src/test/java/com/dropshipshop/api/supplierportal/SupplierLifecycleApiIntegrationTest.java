package com.dropshipshop.api.supplierportal;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalContractStatus;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.domain.SupplierSalesAction;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
import com.dropshipshop.api.fulfillment.domain.FulfillmentHandoverReasonCode;
import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.supplierportal.domain.FulfillmentHandoverHistory;
import com.dropshipshop.api.supplierportal.domain.SupplierInvite;
import com.dropshipshop.api.supplierportal.repository.FulfillmentHandoverHistoryRepository;
import com.dropshipshop.api.supplierportal.repository.SupplierInviteRepository;
import com.dropshipshop.api.supplierportal.repository.SupplierPortalActionHistoryRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:supplier_lifecycle;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierLifecycleApiIntegrationTest {

	private static final String ALLOWED_ORIGIN = "http://localhost:3000";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private SupplierInviteRepository inviteRepository;

	@Autowired
	private SupplierPortalHasher hasher;

	@Autowired
	private SupplierPortalActionHistoryRepository actionHistoryRepository;

	@Autowired
	private PaymentGroupRepository paymentGroupRepository;

	@Autowired
	private CustomerOrderRepository orderRepository;

	@Autowired
	private FulfillmentRepository fulfillmentRepository;

	@Autowired
	private FulfillmentHandoverHistoryRepository handoverHistoryRepository;

	@Test
	void suspensionPausesSalesOnceAndHandsOpenPortalWorkToCoreableOnce() throws Exception {
		ManagerFixture fixture = activeSupplier("suspend-handover");
		Fulfillment fulfillment = portalFulfillment(fixture, "suspend-handover");
		String body = portalStatusBody("SUSPENDED", "PAUSE", "Supplier operations paused");

		mockMvc.perform(patch("/api/admin/suppliers/{supplierId}/portal-status", fixture.supplier().getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "suspend-handover-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.portalStatus", is("SUSPENDED")))
			.andExpect(jsonPath("$.salesStatus", is("INACTIVE")))
			.andExpect(jsonPath("$.managerUserId", is(fixture.manager().getId().toString())));

		mockMvc.perform(patch("/api/admin/suppliers/{supplierId}/portal-status", fixture.supplier().getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "suspend-handover-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.portalStatus", is("SUSPENDED")))
			.andExpect(jsonPath("$.salesStatus", is("INACTIVE")));

		mockMvc.perform(patch("/api/admin/suppliers/{supplierId}/portal-status", fixture.supplier().getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "suspend-handover-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(portalStatusBody("SUSPENDED", "KEEP", "Changed sales decision")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));

		Supplier storedSupplier = supplierRepository.findById(fixture.supplier().getId()).orElseThrow();
		assertEquals(SupplierPortalStatus.SUSPENDED, storedSupplier.getPortalStatus());
		assertEquals(SupplierStatus.INACTIVE, storedSupplier.getStatus());
		assertEquals(fixture.manager().getId(), storedSupplier.getManagerUserId());
		assertEquals(1, actionHistoryRepository.findAllBySupplier_IdOrderByCreatedAtAsc(storedSupplier.getId()).size());

		Fulfillment handedOver = fulfillmentRepository.findById(fulfillment.getId()).orElseThrow();
		assertEquals(FulfillmentChannel.SUPPLIER_PORTAL, handedOver.getChannel());
		assertEquals(FulfillmentOperationalOwner.COREABLE, handedOver.getOperationalOwner());
		assertEquals(FulfillmentHandoverReasonCode.PORTAL_SUSPENDED.name(), handedOver.getHandedOverReason());
		assertEquals(TestAuthentication.ADMIN_ID, handedOver.getHandedOverByAdminId());
		assertNotNull(handedOver.getHandedOverAt());

		var histories = handoverHistoryRepository.findAllByFulfillment_IdOrderByCreatedAtAsc(fulfillment.getId());
		assertEquals(1, histories.size());
		FulfillmentHandoverHistory history = histories.getFirst();
		assertEquals(FulfillmentHandoverReasonCode.PORTAL_SUSPENDED, history.getReasonCode());
		assertEquals(TestAuthentication.ADMIN_ID, history.getActorAdminId());
	}

	@Test
	void managerDisconnectAndContactChangeKeepPortalSalesAndManagerStateSeparate() throws Exception {
		ManagerFixture disconnected = activeSupplier("manager-disconnect");
		String disconnectBody = managerDisconnectBody("KEEP", "Manager relationship ended");

		mockMvc.perform(post("/api/admin/suppliers/{supplierId}/manager-disconnect", disconnected.supplier().getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "manager-disconnect-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(disconnectBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.portalStatus", is("PENDING_ACTIVATION")))
			.andExpect(jsonPath("$.salesStatus", is("ACTIVE")));

		mockMvc.perform(post("/api/admin/suppliers/{supplierId}/manager-disconnect", disconnected.supplier().getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "manager-disconnect-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(disconnectBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.portalStatus", is("PENDING_ACTIVATION")))
			.andExpect(jsonPath("$.salesStatus", is("ACTIVE")));

		Supplier disconnectedStored = supplierRepository.findById(disconnected.supplier().getId()).orElseThrow();
		assertNull(disconnectedStored.getManagerUserId());
		assertNull(disconnectedStored.getContactEmailVerifiedAt());
		assertEquals("manager-disconnect@supplier.example", disconnectedStored.getEmail());
		assertEquals(SupplierStatus.ACTIVE, disconnectedStored.getStatus());
		assertEquals(1, actionHistoryRepository.findAllBySupplier_IdOrderByCreatedAtAsc(disconnectedStored.getId()).size());

		ManagerFixture changed = activeSupplier("contact-change");
		long inviteCount = inviteRepository.count();
		String contactBody = contactEmailBody(
			"NEW.CONTACT@Example.com",
			"PAUSE",
			"Portal contact replaced"
		);

		mockMvc.perform(patch("/api/admin/suppliers/{supplierId}/contact-email", changed.supplier().getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "contact-change-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(contactBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contactEmail", is("new.contact@example.com")))
			.andExpect(jsonPath("$.portalStatus", is("PENDING_ACTIVATION")))
			.andExpect(jsonPath("$.salesStatus", is("INACTIVE")));

		mockMvc.perform(patch("/api/admin/suppliers/{supplierId}/contact-email", changed.supplier().getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "contact-change-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(contactBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contactEmail", is("new.contact@example.com")));

		mockMvc.perform(patch("/api/admin/suppliers/{supplierId}/contact-email", changed.supplier().getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "contact-change-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(contactEmailBody("other.contact@example.com", "PAUSE", "Portal contact replaced")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("IDEMPOTENCY_CONFLICT")));

		Supplier changedStored = supplierRepository.findById(changed.supplier().getId()).orElseThrow();
		assertEquals("new.contact@example.com", changedStored.getEmail());
		assertNull(changedStored.getManagerUserId());
		assertNull(changedStored.getContactEmailVerifiedAt());
		assertEquals(SupplierPortalStatus.PENDING_ACTIVATION, changedStored.getPortalStatus());
		assertEquals(SupplierStatus.INACTIVE, changedStored.getStatus());
		assertEquals(inviteCount + 1, inviteRepository.count());
		assertEquals(1, actionHistoryRepository.findAllBySupplier_IdOrderByCreatedAtAsc(changedStored.getId()).size());
	}

	@Test
	void reissueRejectsSuppliersThatAlreadyHaveAManager() throws Exception {
		ManagerFixture fixture = activeSupplier("reissue-guard");
		long inviteCount = inviteRepository.count();

		mockMvc.perform(post("/api/admin/suppliers/{supplierId}/invite/reissue", fixture.supplier().getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "reissue-guard-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reasonCode":"ADMIN_REISSUE"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("INVITE_REISSUE_NOT_ALLOWED")));

		assertEquals(inviteCount, inviteRepository.count());
		Supplier stored = supplierRepository.findById(fixture.supplier().getId()).orElseThrow();
		assertEquals(fixture.manager().getId(), stored.getManagerUserId());
		assertEquals(SupplierPortalStatus.ACTIVE, stored.getPortalStatus());
	}

	@Test
	void reissueNamespacesTheInviteKeyAwayFromApplicationIssuance() throws Exception {
		Supplier supplier = supplierRepository.saveAndFlush(Supplier.portalApplicant(
			"Reissue Namespace Supplier",
			"Reissue Contact",
			"010-0000-0000",
			"reissue-namespace@supplier.example",
			null
		));
		String collisionKey = "application:" + UUID.randomUUID();
		Instant now = Instant.now();
		String issuanceHash = hasher.hmac(
			"supplier-invite-issuance",
			supplier.getId().toString(),
			collisionKey,
			supplier.getEmail()
		);
		SupplierInvite original = inviteRepository.saveAndFlush(SupplierInvite.issue(
			supplier,
			supplier.getEmail(),
			"a".repeat(64),
			collisionKey,
			issuanceHash,
			now.plusSeconds(3600),
			TestAuthentication.ADMIN_ID,
			now
		));
		long inviteCount = inviteRepository.count();

		mockMvc.perform(post("/api/admin/suppliers/{supplierId}/invite/reissue", supplier.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", collisionKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reasonCode":"ADMIN_REISSUE"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("PENDING")));

		SupplierInvite revoked = inviteRepository.findById(original.getId()).orElseThrow();
		SupplierInvite replacement = inviteRepository.findAll().stream()
			.filter(invite -> invite.getSupplier().getId().equals(supplier.getId()) && invite.isOpen())
			.findFirst()
			.orElseThrow();
		assertNotNull(revoked.getRevokedAt());
		assertEquals(inviteCount + 1, inviteRepository.count());
		org.junit.jupiter.api.Assertions.assertNotEquals(original.getId(), replacement.getId());
	}

	@Test
	void permanentlyDisabledPortalCannotUseLegacyPatchOrBypassContract() throws Exception {
		Supplier supplier = supplierRepository.saveAndFlush(Supplier.portalApplicant(
			"Permanent Portal Supplier",
			"Permanent Contact",
			"010-0000-0000",
			"permanent@supplier.example",
			null
		));

		mockMvc.perform(patch("/api/admin/suppliers/{supplierId}/portal-status", supplier.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "permanent-disable-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(portalStatusBody("DISABLED", "PAUSE", "Portal relationship ended")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.portalStatus", is("DISABLED")))
			.andExpect(jsonPath("$.salesStatus", is("INACTIVE")));

		mockMvc.perform(patch("/api/admin/suppliers/{supplierId}", supplier.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name":"Legacy Bypass",
					  "contactName":"Legacy Contact",
					  "phone":"010-9999-9999",
					  "email":"legacy-bypass@example.com",
					  "memo":"Legacy bypass",
					  "status":"ACTIVE"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("BUSINESS_RULE_VIOLATION")));

		mockMvc.perform(patch("/api/admin/suppliers/{supplierId}/sales-status", supplier.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "permanent-sales-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"status":"ACTIVE","reason":"Attempt portal sales restart"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("CONTRACT_NOT_VERIFIED")));

		Supplier stored = supplierRepository.findById(supplier.getId()).orElseThrow();
		assertEquals("Permanent Portal Supplier", stored.getName());
		assertEquals(SupplierStatus.INACTIVE, stored.getStatus());
		assertEquals(SupplierPortalStatus.DISABLED, stored.getPortalStatus());
	}

	@Test
	void portalAndSalesActivationRequireCurrentContractAndOverdueExpiryCommitsBeforeConflict() throws Exception {
		ManagerFixture unverified = suspendedUnverifiedSupplier("contract-unverified");

		mockMvc.perform(patch("/api/admin/suppliers/{supplierId}/portal-status", unverified.supplier().getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "unverified-portal-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(portalStatusBody("ACTIVE", "KEEP", "Resume portal")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("CONTRACT_NOT_VERIFIED")));

		mockMvc.perform(patch("/api/admin/suppliers/{supplierId}/sales-status", unverified.supplier().getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "unverified-sales-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"status":"ACTIVE","reason":"Resume sales"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("CONTRACT_NOT_VERIFIED")));

		Supplier unverifiedStored = supplierRepository.findById(unverified.supplier().getId()).orElseThrow();
		assertEquals(SupplierPortalContractStatus.UNVERIFIED, unverifiedStored.getPortalContractStatus());
		assertEquals(SupplierPortalStatus.SUSPENDED, unverifiedStored.getPortalStatus());
		assertEquals(SupplierStatus.INACTIVE, unverifiedStored.getStatus());

		ManagerFixture overdue = activeSupplier("contract-overdue");
		Instant now = Instant.now();
		overdue.supplier().suspendPortal(SupplierSalesAction.KEEP);
		overdue.supplier().verifyPortalContract(
			"contract-overdue",
			now.minusSeconds(7200),
			now.minusSeconds(3600),
			now,
			TestAuthentication.ADMIN_ID
		);
		supplierRepository.saveAndFlush(overdue.supplier());

		mockMvc.perform(patch("/api/admin/suppliers/{supplierId}/portal-status", overdue.supplier().getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "overdue-portal-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(portalStatusBody("ACTIVE", "KEEP", "Resume overdue portal")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("CONTRACT_NOT_VERIFIED")));

		Supplier expiredStored = supplierRepository.findById(overdue.supplier().getId()).orElseThrow();
		assertEquals(SupplierPortalContractStatus.EXPIRED, expiredStored.getPortalContractStatus());
		assertEquals(SupplierPortalStatus.SUSPENDED, expiredStored.getPortalStatus());
		assertEquals(SupplierStatus.INACTIVE, expiredStored.getStatus());
	}

	private ManagerFixture activeSupplier(String suffix) {
		Instant now = Instant.now();
		ManagerFixture fixture = managerFixture(suffix);
		fixture.supplier().bindManager(fixture.manager().getId(), now);
		fixture.supplier().verifyPortalContract(
			"contract-" + suffix,
			now.minusSeconds(60),
			now.plusSeconds(3600),
			now,
			TestAuthentication.ADMIN_ID
		);
		fixture.supplier().changeSalesStatus(SupplierStatus.ACTIVE, now);
		supplierRepository.saveAndFlush(fixture.supplier());
		return fixture;
	}

	private ManagerFixture suspendedUnverifiedSupplier(String suffix) {
		ManagerFixture fixture = managerFixture(suffix);
		fixture.supplier().bindManager(fixture.manager().getId(), Instant.now());
		fixture.supplier().suspendPortal(SupplierSalesAction.KEEP);
		supplierRepository.saveAndFlush(fixture.supplier());
		return fixture;
	}

	private ManagerFixture managerFixture(String suffix) {
		UserAccount manager = userAccountRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO,
			"supplier-lifecycle-" + suffix,
			suffix + "@manager.example",
			"Supplier Manager " + suffix,
			UserRole.CUSTOMER
		));
		Supplier supplier = supplierRepository.saveAndFlush(Supplier.portalApplicant(
			"Supplier " + suffix,
			"Contact " + suffix,
			"010-0000-0000",
			suffix + "@supplier.example",
			null
		));
		return new ManagerFixture(manager, supplier);
	}

	private Fulfillment portalFulfillment(ManagerFixture fixture, String suffix) {
		Instant now = Instant.now();
		PaymentGroup paymentGroup = paymentGroupRepository.saveAndFlush(new PaymentGroup(
			"CHECKOUT-" + suffix,
			fixture.manager(),
			10_000,
			now.plusSeconds(1800)
		));
		CustomerOrder order = orderRepository.saveAndFlush(new CustomerOrder(
			"ORDER-" + suffix,
			fixture.manager(),
			fixture.supplier(),
			paymentGroup,
			new ShippingAddressSnapshot("Receiver", "010-1111-2222", "12345", "Seoul test road", "101"),
			10_000,
			paymentGroup.getExpiresAt()
		));
		Fulfillment fulfillment = new Fulfillment(order);
		fulfillment.routeToSupplierPortal(now, now.plusSeconds(3600));
		return fulfillmentRepository.saveAndFlush(fulfillment);
	}

	private static String portalStatusBody(String portalStatus, String salesAction, String reason) {
		return """
			{
			  "portalStatus": "%s",
			  "salesAction": "%s",
			  "reason": "%s"
			}
			""".formatted(portalStatus, salesAction, reason);
	}

	private static String managerDisconnectBody(String salesAction, String reason) {
		return """
			{"salesAction":"%s","reason":"%s"}
			""".formatted(salesAction, reason);
	}

	private static String contactEmailBody(String contactEmail, String salesAction, String reason) {
		return """
			{"contactEmail":"%s","salesAction":"%s","reason":"%s"}
			""".formatted(contactEmail, salesAction, reason);
	}

	private record ManagerFixture(UserAccount manager, Supplier supplier) {
	}
}

@SpringBootTest(properties = {
	"app.supplier-portal.enabled=false",
	"spring.datasource.url=jdbc:h2:mem:supplier_lifecycle_feature_off;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierLifecycleFeatureOffApiIntegrationTest {

	private static final String ALLOWED_ORIGIN = "http://localhost:3000";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private SupplierInviteRepository inviteRepository;

	@Test
	void featureOffFailsClosedBeforeReissueMutation() throws Exception {
		Supplier supplier = supplierRepository.saveAndFlush(Supplier.portalApplicant(
			"Feature Off Supplier",
			"Feature Off Contact",
			"010-0000-0000",
			"feature-off@supplier.example",
			null
		));
		long inviteCount = inviteRepository.count();

		mockMvc.perform(post("/api/admin/suppliers/{supplierId}/invite/reissue", supplier.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "feature-off-reissue-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reasonCode":"ADMIN_REISSUE"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("SUPPLIER_PORTAL_NOT_RELEASED")));

		assertEquals(inviteCount, inviteRepository.count());
		Supplier stored = supplierRepository.findById(supplier.getId()).orElseThrow();
		assertEquals(SupplierPortalStatus.PENDING_ACTIVATION, stored.getPortalStatus());
		assertNull(stored.getManagerUserId());
	}
}
