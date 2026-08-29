package com.dropshipshop.api.supplierportal;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalContractStatus;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.policy.PolicyDocumentRepository;
import com.dropshipshop.api.policy.domain.PolicyDocument;
import com.dropshipshop.api.policy.domain.PolicyDocumentStatus;
import com.dropshipshop.api.policy.domain.PolicyDocumentType;
import com.dropshipshop.api.supplierportal.domain.SupplierApplication;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationApprovalMode;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationReviewReasonCode;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationStatus;
import com.dropshipshop.api.supplierportal.repository.SupplierApplicationRepository;
import com.dropshipshop.api.supplierportal.repository.SupplierInviteRepository;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:supplier_application_review;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierApplicationReviewApiIntegrationTest {

	private static final String ALLOWED_ORIGIN = "http://localhost:3000";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PolicyDocumentRepository policyDocumentRepository;

	@Autowired
	private SupplierApplicationRepository applicationRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private SupplierInviteRepository inviteRepository;

	@Test
	void requiresTheExactCurrentlyEffectiveActivePrivacyPolicy() throws Exception {
		String currentVersion = activatePolicy("supplier-privacy-exact", Instant.now().minusSeconds(60));

		mockMvc.perform(get("/api/policies/SUPPLIER_APPLICATION_PRIVACY/current"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type", is("SUPPLIER_APPLICATION_PRIVACY")))
			.andExpect(jsonPath("$.version", is(currentVersion)))
			.andExpect(jsonPath("$.status", is("ACTIVE")));

		mockMvc.perform(applicationRequest(
				"policy-ok-key",
				"Policy Supplier",
				"Policy Owner",
				"policy-owner@example.com",
				currentVersion
			).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.accepted", is(true)));

		mockMvc.perform(applicationRequest(
				"policy-stale-key",
				"Stale Policy Supplier",
				"Stale Owner",
				"policy-stale@example.com",
				"stale-policy-version"
			).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("POLICY_VERSION_MISMATCH")));
		assertTrue(applicationRepository.findByIdempotencyKey("policy-stale-key").isEmpty());

		String futureVersion = activatePolicy("supplier-privacy-future", Instant.now().plus(1, ChronoUnit.HOURS));
		mockMvc.perform(applicationRequest(
				"policy-future-key",
				"Future Policy Supplier",
				"Future Owner",
				"policy-future@example.com",
				futureVersion
			).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code", is("POLICY_UNAVAILABLE")));
		assertTrue(applicationRepository.findByIdempotencyKey("policy-future-key").isEmpty());
	}

	@Test
	void replaysAnIdenticalSubmissionAndRejectsChangedOrDuplicateNormalizedEmailPayloads() throws Exception {
		String policyVersion = activatePolicy("supplier-privacy-idempotency", Instant.now().minusSeconds(60));
		MockHttpServletRequestBuilder original = applicationRequest(
			"submission-replay-key",
			"  Replay   Supplier  ",
			"  Replay   Owner  ",
			"Replay.Owner@Example.COM",
			policyVersion
		).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);

		mockMvc.perform(original)
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.accepted", is(true)));
		mockMvc.perform(applicationRequest(
				"submission-replay-key",
				"  Replay   Supplier  ",
				"  Replay   Owner  ",
				"Replay.Owner@Example.COM",
				policyVersion
			).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.accepted", is(true)));

		SupplierApplication stored = applicationRepository.findByIdempotencyKey("submission-replay-key").orElseThrow();
		assertEquals("Replay Supplier", stored.getSupplierName());
		assertEquals("Replay Owner", stored.getContactName());
		assertEquals("replay.owner@example.com", stored.getContactEmail());
		assertEquals("replay.owner@example.com", stored.getNormalizedContactEmail());

		mockMvc.perform(applicationRequest(
				"submission-replay-key",
				"Changed Supplier",
				"Replay Owner",
				"Replay.Owner@Example.COM",
				policyVersion
			).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("APPLICATION_CONFLICT")));

		mockMvc.perform(applicationRequest(
				"normalized-email-key",
				"Duplicate Email Supplier",
				"Another Owner",
				"replay.owner@EXAMPLE.com",
				policyVersion
			).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("APPLICATION_CONFLICT")));
		assertTrue(applicationRepository.findByIdempotencyKey("normalized-email-key").isEmpty());
	}

	@Test
	void createNewApprovalIsInactiveUnverifiedAndIdempotent() throws Exception {
		String policyVersion = activatePolicy("supplier-privacy-create", Instant.now().minusSeconds(60));
		submit("create-application-key", "Create Supplier", "Create Owner", "create.owner@example.com", policyVersion);
		SupplierApplication application = applicationRepository.findByIdempotencyKey("create-application-key").orElseThrow();
		String reviewKey = "create-review-key";
		String body = approvalBody("CREATE_NEW", null, "Application criteria verified");
		long supplierCount = supplierRepository.count();
		long inviteCount = inviteRepository.count();

		MvcResult first = mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/approve", application.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", reviewKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("APPROVED")))
			.andExpect(jsonPath("$.portalStatus", is("PENDING_ACTIVATION")))
			.andExpect(jsonPath("$.salesStatus", is("INACTIVE")))
			.andReturn();

		UUID supplierId = UUID.fromString(fieldFrom(first, "supplierId"));
		UUID inviteId = UUID.fromString(fieldFrom(first, "inviteId"));
		Supplier supplier = supplierRepository.findById(supplierId).orElseThrow();
		assertEquals(SupplierStatus.INACTIVE, supplier.getStatus());
		assertEquals(SupplierPortalStatus.PENDING_ACTIVATION, supplier.getPortalStatus());
		assertEquals(SupplierPortalContractStatus.UNVERIFIED, supplier.getPortalContractStatus());
		assertEquals("create.owner@example.com", supplier.getEmail());
		assertTrue(inviteRepository.findById(inviteId).isPresent());

		mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/approve", application.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", reviewKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplierId", is(supplierId.toString())))
			.andExpect(jsonPath("$.inviteId", is(inviteId.toString())));

		mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/approve", application.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", reviewKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(approvalBody("CREATE_NEW", null, "Changed review reason")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("APPLICATION_CONFLICT")));

		SupplierApplication approved = applicationRepository.findById(application.getId()).orElseThrow();
		assertEquals(SupplierApplicationStatus.APPROVED, approved.getStatus());
		assertEquals(SupplierApplicationApprovalMode.CREATE_NEW, approved.getApprovalMode());
		assertNull(approved.getRetentionExpiresAt());
		assertEquals(supplierCount + 1, supplierRepository.count());
		assertEquals(inviteCount + 1, inviteRepository.count());
		assertEquals(inviteId, inviteRepository.findBySupplier_IdAndIssuanceIdempotencyKey(
			supplierId,
			"application:" + application.getId()
		).orElseThrow().getId());
	}

	@Test
	void linkExistingApprovalPreservesSalesAndRejectKeepsPiiForNinetyDays() throws Exception {
		String policyVersion = activatePolicy("supplier-privacy-link-reject", Instant.now().minusSeconds(60));
		Supplier legacy = supplierRepository.saveAndFlush(new Supplier(
			"Legacy Supplier",
			"Legacy Owner",
			"010-1000-1000",
			"legacy.old@example.com",
			"Legacy supplier"
		));
		long supplierCount = supplierRepository.count();

		submit("link-application-key", "Linked Supplier", "Linked Owner", "LINKED.Owner@Example.com", policyVersion);
		SupplierApplication linkApplication = applicationRepository.findByIdempotencyKey("link-application-key").orElseThrow();
		mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/approve", linkApplication.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "link-review-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(approvalBody("LINK_EXISTING", legacy.getId(), "Legacy relationship verified")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplierId", is(legacy.getId().toString())))
			.andExpect(jsonPath("$.portalStatus", is("PENDING_ACTIVATION")))
			.andExpect(jsonPath("$.salesStatus", is("ACTIVE")));

		Supplier linked = supplierRepository.findById(legacy.getId()).orElseThrow();
		assertEquals(supplierCount, supplierRepository.count());
		assertEquals(SupplierStatus.ACTIVE, linked.getStatus());
		assertEquals(SupplierPortalStatus.PENDING_ACTIVATION, linked.getPortalStatus());
		assertEquals("linked.owner@example.com", linked.getEmail());

		submit("reject-application-key", "Rejected Supplier", "Rejected Owner", "reject.owner@example.com", policyVersion);
		SupplierApplication rejectedApplication = applicationRepository.findByIdempotencyKey("reject-application-key").orElseThrow();
		String rejectBody = rejectionBody("POLICY_NOT_MET", "Operating criteria not met");
		mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/reject", rejectedApplication.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "reject-review-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(rejectBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("REJECTED")))
			.andExpect(jsonPath("$.supplierId").doesNotExist())
			.andExpect(jsonPath("$.inviteId").doesNotExist());

		mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/reject", rejectedApplication.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "reject-review-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(rejectBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("REJECTED")));

		SupplierApplication rejected = applicationRepository.findById(rejectedApplication.getId()).orElseThrow();
		assertEquals(SupplierApplicationStatus.REJECTED, rejected.getStatus());
		assertEquals(SupplierApplicationReviewReasonCode.POLICY_NOT_MET, rejected.getReviewReasonCode());
		assertEquals(TestAuthentication.ADMIN_ID, rejected.getReviewedByAdminId());
		assertEquals(Duration.ofDays(90), Duration.between(rejected.getReviewedAt(), rejected.getRetentionExpiresAt()));
		assertEquals("reject.owner@example.com", rejected.getContactEmail());
		assertNull(rejected.getAnonymizedAt());
	}

	@Test
	void expiredApprovalCommitsAnonymizationBeforeReturningConflict() throws Exception {
		Instant submittedAt = Instant.now().minus(91, ChronoUnit.DAYS);
		SupplierApplication expired = applicationRepository.saveAndFlush(SupplierApplication.submit(
			"Expired Supplier",
			"Expired Owner",
			"expired.owner@example.com",
			"expired.owner@example.com",
			"010-2000-2000",
			"Expired memo",
			"expired-submit-key",
			"expired-request-hash",
			"expired-policy",
			submittedAt
		));
		long supplierCount = supplierRepository.count();
		long inviteCount = inviteRepository.count();

		mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/approve", expired.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "expired-review-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(approvalBody("CREATE_NEW", null, "Expired review attempt")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("APPLICATION_EXPIRED")));

		SupplierApplication stored = applicationRepository.findById(expired.getId()).orElseThrow();
		assertEquals(SupplierApplicationStatus.EXPIRED, stored.getStatus());
		assertNotNull(stored.getAnonymizedAt());
		assertNull(stored.getSupplierName());
		assertNull(stored.getContactName());
		assertNull(stored.getContactEmail());
		assertNull(stored.getNormalizedContactEmail());
		assertNull(stored.getIdempotencyKey());
		assertEquals(supplierCount, supplierRepository.count());
		assertEquals(inviteCount, inviteRepository.count());
	}

	@Test
	void enforcesAdminAuthorizationAllowedOriginAndApplicationValidation() throws Exception {
		mockMvc.perform(get("/api/admin/supplier-applications"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/admin/supplier-applications")
				.with(authentication(TestAuthentication.customer())))
			.andExpect(status().isForbidden());

		String body = applicationJson(
			"Boundary Supplier",
			"Boundary Owner",
			"boundary.owner@example.com",
			"unused-policy",
			true
		);
		mockMvc.perform(post("/api/supplier-applications")
				.header("Idempotency-Key", "missing-origin-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code", is("ORIGIN_NOT_ALLOWED")));
		mockMvc.perform(post("/api/supplier-applications")
				.header(HttpHeaders.ORIGIN, "https://attacker.example")
				.header("Idempotency-Key", "wrong-origin-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/supplier-applications")
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
		mockMvc.perform(post("/api/supplier-applications")
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "privacy-false-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(applicationJson(
					"Boundary Supplier",
					"Boundary Owner",
					"boundary.owner@example.com",
					"unused-policy",
					false
				)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

		mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/approve", UUID.randomUUID())
				.with(authentication(TestAuthentication.customer()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "customer-review-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content(approvalBody("CREATE_NEW", null, "Unauthorized review attempt")))
			.andExpect(status().isForbidden());
	}

	private void submit(
		String idempotencyKey,
		String supplierName,
		String contactName,
		String contactEmail,
		String policyVersion
	) throws Exception {
		mockMvc.perform(applicationRequest(idempotencyKey, supplierName, contactName, contactEmail, policyVersion)
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.accepted", is(true)));
	}

	private MockHttpServletRequestBuilder applicationRequest(
		String idempotencyKey,
		String supplierName,
		String contactName,
		String contactEmail,
		String policyVersion
	) {
		return post("/api/supplier-applications")
			.header("Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(applicationJson(supplierName, contactName, contactEmail, policyVersion, true));
	}

	private String activatePolicy(String version, Instant effectiveFrom) {
		policyDocumentRepository.findByTypeAndStatus(
			PolicyDocumentType.SUPPLIER_APPLICATION_PRIVACY,
			PolicyDocumentStatus.ACTIVE
		).ifPresent(active -> {
			active.archive();
			policyDocumentRepository.saveAndFlush(active);
		});
		PolicyDocument policy = new PolicyDocument(
			PolicyDocumentType.SUPPLIER_APPLICATION_PRIVACY,
			version,
			"Supplier application privacy",
			"Collect supplier and contact details for application review.",
			effectiveFrom
		);
		policy.activate();
		policyDocumentRepository.saveAndFlush(policy);
		return version;
	}

	private static String applicationJson(
		String supplierName,
		String contactName,
		String contactEmail,
		String policyVersion,
		boolean privacyAgreed
	) {
		return """
			{
			  "supplierName": "%s",
			  "contactName": "%s",
			  "contactEmail": "%s",
			  "contactPhone": "+82 10-1234-5678",
			  "memo": "Supplier application memo",
			  "privacyAgreed": %s,
			  "consentPolicyVersion": "%s"
			}
			""".formatted(supplierName, contactName, contactEmail, privacyAgreed, policyVersion);
	}

	private static String approvalBody(String mode, UUID existingSupplierId, String reason) {
		String existing = existingSupplierId == null ? "null" : "\"" + existingSupplierId + "\"";
		return """
			{
			  "approvalMode": "%s",
			  "existingSupplierId": %s,
			  "reviewReasonCode": "APPLICATION_APPROVED",
			  "internalReason": "%s"
			}
			""".formatted(mode, existing, reason);
	}

	private static String rejectionBody(String reasonCode, String reason) {
		return """
			{
			  "reviewReasonCode": "%s",
			  "internalReason": "%s"
			}
			""".formatted(reasonCode, reason);
	}

	private String fieldFrom(MvcResult result, String fieldName) throws Exception {
		String body = result.getResponse().getContentAsString();
		String marker = "\"" + fieldName + "\":\"";
		int start = body.indexOf(marker);
		if (start < 0) {
			throw new IllegalStateException("Field not found: " + fieldName + " in " + body);
		}
		int valueStart = start + marker.length();
		int valueEnd = body.indexOf('"', valueStart);
		return body.substring(valueStart, valueEnd);
	}
}

@SpringBootTest(properties = {
	"app.supplier-portal.enabled=false",
	"spring.datasource.url=jdbc:h2:mem:supplier_application_feature_off;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierApplicationFeatureOffBoundaryIntegrationTest {

	private static final String ALLOWED_ORIGIN = "http://localhost:3000";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SupplierApplicationRepository applicationRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private SupplierInviteRepository inviteRepository;

	@Autowired
	private SupplierPortalHasher hasher;

	@Test
	void hidesPublicSubmissionAndFailsClosedBeforeApprovalMutationWhileAllowingRejection() throws Exception {
		mockMvc.perform(post("/api/supplier-applications")
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "feature-off-public-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierName": "Hidden Supplier",
					  "contactName": "Hidden Owner",
					  "contactEmail": "hidden.owner@example.com",
					  "privacyAgreed": true,
					  "consentPolicyVersion": "hidden-policy"
					}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));

		Instant now = Instant.now();
		SupplierApplication application = applicationRepository.saveAndFlush(SupplierApplication.submit(
			"Feature Off Supplier",
			"Feature Off Owner",
			"feature.off@example.com",
			"feature.off@example.com",
			null,
			null,
			"feature-off-submit-key",
			"feature-off-request-hash",
			"feature-off-policy",
			now
		));
		long supplierCount = supplierRepository.count();
		long inviteCount = inviteRepository.count();

		mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/approve", application.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "feature-off-approve-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "approvalMode": "CREATE_NEW",
					  "existingSupplierId": null,
					  "reviewReasonCode": "APPLICATION_APPROVED",
					  "internalReason": "Feature gate approval test"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("SUPPLIER_PORTAL_NOT_RELEASED")));

		SupplierApplication stillSubmitted = applicationRepository.findById(application.getId()).orElseThrow();
		assertEquals(SupplierApplicationStatus.SUBMITTED, stillSubmitted.getStatus());
		assertEquals(supplierCount, supplierRepository.count());
		assertEquals(inviteCount, inviteRepository.count());

		SupplierApplication overdue = applicationRepository.saveAndFlush(SupplierApplication.submit(
			"Feature Off Overdue Supplier",
			"Feature Off Overdue Owner",
			"feature.off.overdue@example.com",
			"feature.off.overdue@example.com",
			null,
			null,
			"feature-off-overdue-submit-key",
			"feature-off-overdue-request-hash",
			"feature-off-policy",
			now.minus(91, ChronoUnit.DAYS)
		));
		mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/approve", overdue.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "feature-off-overdue-approve-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "approvalMode": "CREATE_NEW",
					  "existingSupplierId": null,
					  "reviewReasonCode": "APPLICATION_APPROVED",
					  "internalReason": "Feature gate overdue approval test"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("SUPPLIER_PORTAL_NOT_RELEASED")));

		SupplierApplication overdueStillSubmitted = applicationRepository.findById(overdue.getId()).orElseThrow();
		assertEquals(SupplierApplicationStatus.SUBMITTED, overdueStillSubmitted.getStatus());
		assertNull(overdueStillSubmitted.getAnonymizedAt());
		assertEquals("feature.off.overdue@example.com", overdueStillSubmitted.getContactEmail());
		assertEquals(supplierCount, supplierRepository.count());
		assertEquals(inviteCount, inviteRepository.count());

		mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/reject", application.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", "feature-off-reject-key")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reviewReasonCode": "OUT_OF_SCOPE",
					  "internalReason": "Feature gate rejection test"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("REJECTED")));

		SupplierApplication rejected = applicationRepository.findById(application.getId()).orElseThrow();
		assertEquals(SupplierApplicationStatus.REJECTED, rejected.getStatus());
		assertEquals(supplierCount, supplierRepository.count());
		assertEquals(inviteCount, inviteRepository.count());
	}

	@Test
	void featureOffChecksApplicationScopeAndReturnsOnlyStoredCompletedReplay() throws Exception {
		String replayKey = "feature-off-completed-replay-key";
		String reason = "Completed approval replay";
		Instant now = Instant.now();
		Supplier supplier = supplierRepository.saveAndFlush(Supplier.portalApplicant(
			"Completed Replay Supplier",
			"Completed Replay Owner",
			null,
			"completed.replay@example.com",
			null
		));
		SupplierApplication application = SupplierApplication.submit(
			"Completed Replay Supplier",
			"Completed Replay Owner",
			"completed.replay@example.com",
			"completed.replay@example.com",
			null,
			null,
			"completed-replay-submit-key",
			"completed-replay-submit-hash",
			"feature-off-policy",
			now
		);
		applicationRepository.saveAndFlush(application);
		UUID inviteId = UUID.randomUUID();
		Instant inviteExpiresAt = now.plus(7, ChronoUnit.DAYS);
		String requestHash = hasher.hmac(
			"supplier-application-review",
			"APPROVE",
			replayKey,
			"CREATE_NEW",
			null,
			"APPLICATION_APPROVED",
			reason
		);
		application.approve(
			supplier,
			SupplierApplicationApprovalMode.CREATE_NEW,
			null,
			TestAuthentication.ADMIN_ID,
			SupplierApplicationReviewReasonCode.APPLICATION_APPROVED,
			reason,
			replayKey,
			requestHash,
			"""
				{"applicationId":"%s","status":"APPROVED","supplierId":"%s","inviteId":"%s","inviteExpiresAt":"%s","portalStatus":"PENDING_ACTIVATION","salesStatus":"INACTIVE"}
				""".formatted(application.getId(), supplier.getId(), inviteId, inviteExpiresAt),
			now
		);
		applicationRepository.saveAndFlush(application);
		long supplierCount = supplierRepository.count();
		long inviteCount = inviteRepository.count();

		mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/approve", UUID.randomUUID())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", replayKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(completedApprovalBody(reason)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code", is("RESOURCE_NOT_FOUND")));

		mockMvc.perform(post("/api/admin/supplier-applications/{applicationId}/approve", application.getId())
				.with(authentication(TestAuthentication.admin()))
				.header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
				.header("Idempotency-Key", replayKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(completedApprovalBody(reason)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplierId", is(supplier.getId().toString())))
			.andExpect(jsonPath("$.inviteId", is(inviteId.toString())));

		assertEquals(supplierCount, supplierRepository.count());
		assertEquals(inviteCount, inviteRepository.count());
	}

	private static String completedApprovalBody(String reason) {
		return """
			{
			  "approvalMode":"CREATE_NEW",
			  "existingSupplierId":null,
			  "reviewReasonCode":"APPLICATION_APPROVED",
			  "internalReason":"%s"
			}
			""".formatted(reason);
	}
}
