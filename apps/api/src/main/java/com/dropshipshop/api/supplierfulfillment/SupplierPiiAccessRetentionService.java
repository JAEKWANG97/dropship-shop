package com.dropshipshop.api.supplierfulfillment;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierPiiAccessRetentionService {

	private final SupplierPiiAccessLogRepository repository;

	SupplierPiiAccessRetentionService(SupplierPiiAccessLogRepository repository) {
		this.repository = repository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public long cleanupBefore(Instant cutoff) {
		return repository.deleteByAccessedAtBefore(cutoff);
	}
}
