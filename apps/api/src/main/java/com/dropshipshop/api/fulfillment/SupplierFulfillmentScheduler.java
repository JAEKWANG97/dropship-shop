package com.dropshipshop.api.fulfillment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.dropshipshop.api.supplierfulfillment.SupplierPiiAccessRetentionService;
import com.dropshipshop.api.notification.SupplierNotificationRetentionService;

@Component
class SupplierFulfillmentScheduler {

	private static final Logger log = LoggerFactory.getLogger(SupplierFulfillmentScheduler.class);

	private final SupplierFulfillmentHandoverService handoverService;
	private final SupplierPiiAccessRetentionService accessRetentionService;
	private final SupplierNotificationRetentionService notificationRetentionService;

	SupplierFulfillmentScheduler(
		SupplierFulfillmentHandoverService handoverService,
		SupplierPiiAccessRetentionService accessRetentionService,
		SupplierNotificationRetentionService notificationRetentionService
	) {
		this.handoverService = handoverService;
		this.accessRetentionService = accessRetentionService;
		this.notificationRetentionService = notificationRetentionService;
	}

	@Scheduled(fixedDelayString = "${app.supplier-portal.fulfillment-cutoff-delay-ms:60000}")
	public void handOverExpiredWork() {
		handOverAt(Instant.now());
	}

	int handOverAt(Instant now) {
		List<UUID> ids = handoverService.cutoffCandidateIds(now);
		int changed = 0;
		for (UUID id : ids) {
			try {
				if (handoverService.enforceCutoff(id, now)) {
					changed++;
				}
			} catch (RuntimeException exception) {
				log.warn("Supplier fulfillment cutoff takeover failed: fulfillmentId={}", id, exception);
			}
		}
		return changed;
	}

	@Scheduled(cron = "${app.supplier-portal.pii-log-retention-cron:0 20 3 * * *}", zone = "Asia/Seoul")
	public void cleanupAccessLogs() {
		Instant now = Instant.now();
		try {
			accessRetentionService.cleanupBefore(now.minus(java.time.Duration.ofDays(365)));
		} catch (RuntimeException exception) {
			log.warn("Supplier PII access-log retention failed", exception);
		}
		List<UUID> notificationIds;
		try {
			notificationIds = notificationRetentionService.candidateIds(now);
		} catch (RuntimeException exception) {
			log.warn("Supplier notification retention scan failed", exception);
			return;
		}
		for (UUID id : notificationIds) {
			try {
				notificationRetentionService.cleanup(id, now);
			} catch (RuntimeException exception) {
				log.warn("Supplier notification retention failed: notificationId={}", id, exception);
			}
		}
	}
}
