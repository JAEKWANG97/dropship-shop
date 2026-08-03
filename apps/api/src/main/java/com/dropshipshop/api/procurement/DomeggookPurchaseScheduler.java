package com.dropshipshop.api.procurement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class DomeggookPurchaseScheduler {

	private static final Logger log = LoggerFactory.getLogger(DomeggookPurchaseScheduler.class);

	private final DomeggookProperties properties;
	private final DomeggookPurchaseService service;

	DomeggookPurchaseScheduler(DomeggookProperties properties, DomeggookPurchaseService service) {
		this.properties = properties;
		this.service = service;
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

	private java.util.UUID serviceOrderId(java.util.UUID fulfillmentId) {
		return service.orderId(fulfillmentId);
	}
}
