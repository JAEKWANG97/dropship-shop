package com.dropshipshop.api.supplierfulfillment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface SupplierPiiAccessLogRepository extends JpaRepository<SupplierPiiAccessLog, UUID> {

	List<SupplierPiiAccessLog> findAllByOrderByAccessedAtDesc();

	List<SupplierPiiAccessLog> findAllByOrder_IdOrderByAccessedAtDesc(UUID orderId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	long deleteByAccessedAtBefore(Instant cutoff);
}
