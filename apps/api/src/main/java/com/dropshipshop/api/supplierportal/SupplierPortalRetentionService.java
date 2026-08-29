package com.dropshipshop.api.supplierportal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.notification.NotificationLogRepository;
import com.dropshipshop.api.supplierportal.domain.SupplierApplication;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationStatus;
import com.dropshipshop.api.supplierportal.domain.SupplierInvite;
import com.dropshipshop.api.supplierportal.repository.SupplierApplicationRepository;
import com.dropshipshop.api.supplierportal.repository.SupplierInviteRepository;

@Service
public class SupplierPortalRetentionService {

	private final SupplierApplicationRepository applicationRepository;
	private final SupplierInviteRepository inviteRepository;
	private final SupplierRepository supplierRepository;
	private final NotificationLogRepository notificationLogRepository;

	SupplierPortalRetentionService(
		SupplierApplicationRepository applicationRepository,
		SupplierInviteRepository inviteRepository,
		SupplierRepository supplierRepository,
		NotificationLogRepository notificationLogRepository
	) {
		this.applicationRepository = applicationRepository;
		this.inviteRepository = inviteRepository;
		this.supplierRepository = supplierRepository;
		this.notificationLogRepository = notificationLogRepository;
	}

	@Transactional(readOnly = true)
	public List<UUID> applicationCandidateIds(Instant now) {
		return applicationRepository
			.findTop100ByRetentionExpiresAtLessThanEqualAndAnonymizedAtIsNullOrderByRetentionExpiresAtAsc(now)
			.stream()
			.map(SupplierApplication::getId)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<UUID> inviteCandidateIds(Instant now) {
		return inviteRepository
			.findTop100ByRecipientRetentionExpiresAtLessThanEqualAndRecipientAnonymizedAtIsNullOrderByRecipientRetentionExpiresAtAsc(now)
			.stream()
			.map(SupplierInvite::getId)
			.toList();
	}

	@Transactional
	public boolean cleanupApplication(UUID applicationId, Instant now) {
		SupplierApplication application = applicationRepository.findByIdForUpdate(applicationId).orElse(null);
		if (application == null) {
			return false;
		}
		if (application.getStatus() == SupplierApplicationStatus.SUBMITTED) {
			return application.expireAndAnonymize(now);
		}
		if (application.getStatus() == SupplierApplicationStatus.REJECTED) {
			return application.anonymizeRejected(now);
		}
		return false;
	}

	@Transactional
	public boolean cleanupInvite(UUID inviteId, Instant now) {
		UUID supplierId = inviteRepository.findSupplierIdById(inviteId).orElse(null);
		if (supplierId == null) {
			return false;
		}
		supplierRepository.findByIdForUpdate(supplierId)
			.orElseThrow(() -> new IllegalStateException("Supplier invite has no supplier"));
		SupplierInvite invite = inviteRepository.findByIdForUpdate(inviteId).orElse(null);
		if (invite == null || !invite.anonymizeRecipient(now)) {
			return false;
		}
		notificationLogRepository.anonymizeSupplierInviteRecipients(inviteId, now);
		return true;
	}
}
