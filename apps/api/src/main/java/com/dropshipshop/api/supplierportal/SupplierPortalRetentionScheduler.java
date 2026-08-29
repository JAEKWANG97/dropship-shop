package com.dropshipshop.api.supplierportal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SupplierPortalRetentionScheduler {

	private static final Logger log = LoggerFactory.getLogger(SupplierPortalRetentionScheduler.class);

	private final SupplierPortalRetentionService retentionService;

	SupplierPortalRetentionScheduler(SupplierPortalRetentionService retentionService) {
		this.retentionService = retentionService;
	}

	@Scheduled(cron = "${app.supplier-portal.retention-cron:0 40 3 * * *}", zone = "Asia/Seoul")
	public void cleanupExpiredData() {
		cleanupAt(Instant.now());
	}

	CleanupSummary cleanupAt(Instant now) {
		int applicationsCleaned = 0;
		int invitesCleaned = 0;
		int failures = 0;

		List<UUID> applicationIds;
		try {
			applicationIds = retentionService.applicationCandidateIds(now);
		} catch (RuntimeException exception) {
			log.warn("Supplier application retention candidate lookup failed", exception);
			applicationIds = List.of();
			failures++;
		}
		for (UUID applicationId : applicationIds) {
			try {
				if (retentionService.cleanupApplication(applicationId, now)) {
					applicationsCleaned++;
				}
			} catch (RuntimeException exception) {
				failures++;
				log.warn("Supplier application retention failed: applicationId={}", applicationId, exception);
			}
		}

		List<UUID> inviteIds;
		try {
			inviteIds = retentionService.inviteCandidateIds(now);
		} catch (RuntimeException exception) {
			log.warn("Supplier invite retention candidate lookup failed", exception);
			inviteIds = List.of();
			failures++;
		}
		for (UUID inviteId : inviteIds) {
			try {
				if (retentionService.cleanupInvite(inviteId, now)) {
					invitesCleaned++;
				}
			} catch (RuntimeException exception) {
				failures++;
				log.warn("Supplier invite retention failed: inviteId={}", inviteId, exception);
			}
		}

		return new CleanupSummary(applicationsCleaned, invitesCleaned, failures);
	}

	record CleanupSummary(int applicationsCleaned, int invitesCleaned, int failures) {
	}
}
