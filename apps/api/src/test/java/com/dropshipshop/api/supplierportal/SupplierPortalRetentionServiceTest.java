package com.dropshipshop.api.supplierportal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.notification.NotificationLogRepository;
import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.supplierportal.domain.SupplierApplication;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationReviewAction;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationReviewReasonCode;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationStatus;
import com.dropshipshop.api.supplierportal.domain.SupplierInvite;
import com.dropshipshop.api.supplierportal.repository.SupplierApplicationRepository;
import com.dropshipshop.api.supplierportal.repository.SupplierInviteRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:supplier_portal_retention;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH")
@ActiveProfiles("test")
@Transactional
class SupplierPortalRetentionServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

	@Autowired
	private SupplierPortalRetentionService retentionService;

	@Autowired
	private SupplierApplicationRepository applicationRepository;

	@Autowired
	private SupplierInviteRepository inviteRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private NotificationLogRepository notificationLogRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void expiresAndAnonymizesSubmittedApplicationAtNinetyDays() {
		Instant submittedAt = NOW.minus(90, ChronoUnit.DAYS);
		SupplierApplication application = applicationRepository.saveAndFlush(application(submittedAt, "submitted"));

		assertThat(retentionService.cleanupApplication(application.getId(), NOW.minusNanos(1))).isFalse();
		assertThat(retentionService.cleanupApplication(application.getId(), NOW)).isTrue();
		entityManager.flush();
		entityManager.clear();

		SupplierApplication cleaned = applicationRepository.findById(application.getId()).orElseThrow();
		assertThat(cleaned.getStatus()).isEqualTo(SupplierApplicationStatus.EXPIRED);
		assertThat(cleaned.getSupplierName()).isNull();
		assertThat(cleaned.getContactName()).isNull();
		assertThat(cleaned.getContactEmail()).isNull();
		assertThat(cleaned.getNormalizedContactEmail()).isNull();
		assertThat(cleaned.getContactPhone()).isNull();
		assertThat(cleaned.getMemo()).isNull();
		assertThat(cleaned.getIdempotencyKey()).isNull();
		assertThat(cleaned.getRequestHash()).isNull();
		assertThat(cleaned.getConsentPolicyVersion()).isEqualTo("supplier-privacy-v1");
		assertThat(cleaned.getConsentedAt()).isEqualTo(submittedAt);
		assertThat(cleaned.getAnonymizedAt()).isEqualTo(NOW);
	}

	@Test
	void anonymizesRejectedApplicationNinetyDaysAfterReviewAndKeepsAudit() {
		Instant reviewedAt = NOW.minus(90, ChronoUnit.DAYS);
		SupplierApplication application = application(reviewedAt.minus(1, ChronoUnit.DAYS), "rejected");
		UUID adminId = UUID.randomUUID();
		application.reject(
			adminId,
			SupplierApplicationReviewReasonCode.POLICY_NOT_MET,
			"policy criteria not met",
			"review-key",
			"review-hash",
			"{}",
			reviewedAt
		);
		applicationRepository.saveAndFlush(application);

		assertThat(retentionService.cleanupApplication(application.getId(), NOW)).isTrue();
		entityManager.flush();
		entityManager.clear();

		SupplierApplication cleaned = applicationRepository.findById(application.getId()).orElseThrow();
		assertThat(cleaned.getStatus()).isEqualTo(SupplierApplicationStatus.REJECTED);
		assertThat(cleaned.getReviewAction()).isEqualTo(SupplierApplicationReviewAction.REJECT);
		assertThat(cleaned.getReviewReasonCode()).isEqualTo(SupplierApplicationReviewReasonCode.POLICY_NOT_MET);
		assertThat(cleaned.getReviewedByAdminId()).isEqualTo(adminId);
		assertThat(cleaned.getReviewedAt()).isEqualTo(reviewedAt);
		assertThat(cleaned.getConsentPolicyVersion()).isEqualTo("supplier-privacy-v1");
		assertThat(cleaned.getReviewReason()).isNull();
		assertThat(cleaned.getReviewIdempotencyKey()).isNull();
		assertThat(cleaned.getReviewRequestHash()).isNull();
		assertThat(cleaned.getReviewResultSnapshot()).isNull();
		assertThat(cleaned.getContactEmail()).isNull();
		assertThat(cleaned.getAnonymizedAt()).isEqualTo(NOW);
	}

	@Test
	void anonymizesExpiredInviteAndLinkedNotificationButKeepsDigestAndTerminalEvidence() {
		Supplier supplier = supplierRepository.saveAndFlush(new Supplier(
			"Retention supplier",
			"Manager",
			null,
			"retention@example.com",
			null
		));
		Instant issuedAt = NOW.minus(38, ChronoUnit.DAYS);
		Instant expiresAt = issuedAt.plus(7, ChronoUnit.DAYS);
		SupplierInvite invite = inviteRepository.saveAndFlush(SupplierInvite.issue(
			supplier,
			"retention@example.com",
			"digest-retention",
			"issuance-key",
			"issuance-hash",
			expiresAt,
			UUID.randomUUID(),
			issuedAt
		));
		NotificationLog notification = notificationLogRepository.saveAndFlush(NotificationLog.supplierInvitation(
			supplier.getId(),
			invite.getId(),
			"retention@example.com",
			"supplierInviteId=%s".formatted(invite.getId())
		));
		notification.markFailed("delivery failed");
		notificationLogRepository.saveAndFlush(notification);

		assertThat(retentionService.cleanupInvite(invite.getId(), NOW)).isTrue();
		assertThat(retentionService.cleanupInvite(invite.getId(), NOW)).isFalse();
		entityManager.clear();

		SupplierInvite cleanedInvite = inviteRepository.findById(invite.getId()).orElseThrow();
		NotificationLog cleanedNotification = notificationLogRepository.findById(notification.getId()).orElseThrow();
		assertThat(cleanedInvite.getRecipientEmail()).isNull();
		assertThat(cleanedInvite.getIssuanceIdempotencyKey()).isNull();
		assertThat(cleanedInvite.getIssuanceRequestHash()).isNull();
		assertThat(cleanedInvite.getTokenDigest()).isEqualTo("digest-retention");
		assertThat(cleanedInvite.getExpiresAt()).isEqualTo(expiresAt);
		assertThat(cleanedInvite.getRecipientAnonymizedAt()).isEqualTo(NOW);
		assertThat(cleanedNotification.getRecipient()).isNull();
		assertThat(cleanedNotification.getRecipientAnonymizedAt()).isEqualTo(NOW);
		assertThat(cleanedNotification.getPayloadSnapshot()).contains(invite.getId().toString());
		assertThat(cleanedNotification.getFailureReason()).isEqualTo("delivery failed");
	}

	@Test
	void consumedInviteUsesTheEarlierTerminalRetentionDeadline() {
		Supplier supplier = supplierRepository.saveAndFlush(new Supplier(
			"Consumed retention supplier",
			"Manager",
			null,
			"consumed-retention@example.com",
			null
		));
		Instant issuedAt = NOW.minus(40, ChronoUnit.DAYS);
		Instant consumedAt = issuedAt.plus(1, ChronoUnit.DAYS);
		SupplierInvite invite = SupplierInvite.issue(
			supplier,
			"consumed-retention@example.com",
			"digest-consumed-retention",
			"consumed-key",
			"consumed-hash",
			NOW.plus(30, ChronoUnit.DAYS),
			UUID.randomUUID(),
			issuedAt
		);
		UUID consumedBy = UUID.randomUUID();
		invite.consume(consumedBy, consumedAt);
		inviteRepository.saveAndFlush(invite);

		assertThat(retentionService.cleanupInvite(invite.getId(), NOW)).isTrue();
		entityManager.clear();

		SupplierInvite cleaned = inviteRepository.findById(invite.getId()).orElseThrow();
		assertThat(cleaned.getConsumedAt()).isEqualTo(consumedAt);
		assertThat(cleaned.getConsumedByUserId()).isEqualTo(consumedBy);
		assertThat(cleaned.getTokenDigest()).isEqualTo("digest-consumed-retention");
		assertThat(cleaned.getRecipientEmail()).isNull();
	}

	private SupplierApplication application(Instant submittedAt, String suffix) {
		return SupplierApplication.submit(
			"Supplier " + suffix,
			"Manager " + suffix,
			suffix + "@example.com",
			suffix + "@example.com",
			"010-1234-5678",
			"memo " + suffix,
			"submit-key-" + suffix,
			"submit-hash-" + suffix,
			"supplier-privacy-v1",
			submittedAt
		);
	}
}
