package com.dropshipshop.api.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.notification.domain.NotificationLog;

@Service
public class SupplierNotificationRetentionService {

	private final NotificationLogRepository repository;

	SupplierNotificationRetentionService(NotificationLogRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public List<UUID> candidateIds(Instant now) {
		return repository.findSupplierOperationalCleanupCandidateIds(now);
	}

	@Transactional
	public boolean cleanup(UUID id, Instant now) {
		NotificationLog log = repository.findByIdForUpdate(id).orElse(null);
		if (log == null || !log.isSupplierOperational()
			|| log.getRecipientAnonymizedAt() != null
			|| log.getRecipientRetentionExpiresAt() == null
			|| now.isBefore(log.getRecipientRetentionExpiresAt())) {
			return false;
		}
		log.anonymizeRecipient(now);
		return true;
	}
}
