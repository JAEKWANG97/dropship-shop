package com.dropshipshop.api.procurement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class DomeggookPurchaseScheduler {

	private static final Logger log = LoggerFactory.getLogger(DomeggookPurchaseScheduler.class);
	private static final long CATALOG_REQUEST_DELAY_MS = 1000;

	private final DomeggookProperties properties;
	private final DomeggookPurchaseService service;
	private final DomeggookCatalogSyncService catalogSyncService;

	DomeggookPurchaseScheduler(
		DomeggookProperties properties,
		DomeggookPurchaseService service,
		DomeggookCatalogSyncService catalogSyncService
	) {
		this.properties = properties;
		this.service = service;
		this.catalogSyncService = catalogSyncService;
	}

	@Scheduled(fixedDelayString = "${app.domeggook.order-interval-ms:60000}")
	void orderReadyPurchases() {
		if (!properties.autoOrderEnabled()) return;
		service.processingFulfillmentIds().forEach(id -> {
			try {
				service.reconcile(serviceOrderId(id));
			} catch (RuntimeException exception) {
				log.warn("Domeggook reconciliation failed for fulfillment {} ({})", id, exception.getClass().getSimpleName());
			}
		});
		service.readyFulfillmentIds().forEach(service::process);
	}

	@Scheduled(fixedDelayString = "${app.domeggook.sync-interval-ms:600000}")
	void syncOrderedPurchases() {
		if (!properties.enabled()) return;
		java.util.stream.Stream.concat(
			service.orderedFulfillmentIds().stream(),
			service.cancelRequestedFulfillmentIds().stream()
		).forEach(id -> {
			try {
				service.sync(id);
			} catch (RuntimeException exception) {
				log.warn("Domeggook order sync failed for fulfillment {} ({})", id, exception.getClass().getSimpleName());
			}
		});
	}

	@Scheduled(fixedDelayString = "${app.domeggook.catalog-sync-interval-ms:3600000}")
	void syncCatalog() {
		if (!properties.catalogSyncEnabled()) return;
		boolean apply = !properties.catalogSyncDryRun();
		for (java.util.UUID id : catalogSyncService.targetProductIds(properties.catalogSyncBatchSize())) {
			try {
				DomeggookCatalogSyncService.SyncResult result = catalogSyncService.sync(id, apply);
				if (!apply) {
					log.info("Domeggook catalog dry-run product={} available={} sourcePrice={} options={}",
						result.productId(), result.available(), result.sourcePrice(), result.optionCount());
				}
			} catch (RuntimeException exception) {
				log.warn("Domeggook catalog sync failed for product {} ({})", id, exception.getClass().getSimpleName());
			}
			if (!pauseCatalogRequests()) break;
		}
	}

	private boolean pauseCatalogRequests() {
		try {
			Thread.sleep(CATALOG_REQUEST_DELAY_MS);
			return true;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private java.util.UUID serviceOrderId(java.util.UUID fulfillmentId) {
		return service.orderId(fulfillmentId);
	}
}
