package com.dropshipshop.api.catalog.cleanup;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ProductImageCleanupScheduler {
	private final ProductImageCleanupService cleanupService;

	ProductImageCleanupScheduler(ProductImageCleanupService cleanupService) {
		this.cleanupService = cleanupService;
	}

	@Scheduled(fixedDelayString = "${app.catalog.image-cleanup-interval-ms:60000}")
	void processDueJobs() {
		cleanupService.processDueJobs(Instant.now());
	}
}
