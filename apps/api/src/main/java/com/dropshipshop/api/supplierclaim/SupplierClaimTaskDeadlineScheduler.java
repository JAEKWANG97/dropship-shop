package com.dropshipshop.api.supplierclaim;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SupplierClaimTaskDeadlineScheduler {

	private static final Logger log = LoggerFactory.getLogger(SupplierClaimTaskDeadlineScheduler.class);
	private final SupplierClaimTaskService taskService;

	SupplierClaimTaskDeadlineScheduler(SupplierClaimTaskService taskService) {
		this.taskService = taskService;
	}

	@Scheduled(fixedDelayString = "${app.supplier-portal.claim-task-deadline-delay-ms:60000}")
	void closeExpiredTasks() {
		closeExpiredAt(Instant.now());
	}

	int closeExpiredAt(Instant now) {
		int changed = 0;
		for (UUID taskId : taskService.deadlineCandidateIds(now)) {
			try {
				if (taskService.expire(taskId, now)) changed++;
			} catch (RuntimeException exception) {
				log.warn("Supplier claim task deadline close failed: taskId={}", taskId, exception);
			}
		}
		return changed;
	}
}
