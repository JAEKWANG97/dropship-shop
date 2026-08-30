package com.dropshipshop.api.notification;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class SupplierOperationalNotificationScheduler {

	private static final Logger log = LoggerFactory.getLogger(SupplierOperationalNotificationScheduler.class);

	private final NotificationLogRepository repository;
	private final NotificationDispatchListener dispatchListener;

	SupplierOperationalNotificationScheduler(
		NotificationLogRepository repository,
		NotificationDispatchListener dispatchListener
	) {
		this.repository = repository;
		this.dispatchListener = dispatchListener;
	}

	@Scheduled(fixedDelayString = "${app.supplier-portal.notification-outbox-delay-ms:60000}")
	public void recoverPending() {
		recoverPendingBatch();
	}

	int recoverPendingBatch() {
		List<UUID> ids = repository.findPendingSupplierOperationalIds(PageRequest.of(0, 100));
		int dispatched = 0;
		for (UUID id : ids) {
			try {
				dispatchListener.dispatchNow(id);
				dispatched++;
			} catch (RuntimeException exception) {
				log.warn("Supplier operational notification recovery failed: notificationId={}", id, exception);
			}
		}
		return dispatched;
	}
}
