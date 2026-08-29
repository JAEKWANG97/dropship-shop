package com.dropshipshop.api.supplierportal.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.supplierportal.domain.FulfillmentHandoverHistory;

public interface FulfillmentHandoverHistoryRepository extends JpaRepository<FulfillmentHandoverHistory, UUID> {

	Optional<FulfillmentHandoverHistory> findByFulfillment_IdAndIdempotencyKey(
		UUID fulfillmentId,
		String idempotencyKey
	);

	List<FulfillmentHandoverHistory> findAllByFulfillment_IdOrderByCreatedAtAsc(UUID fulfillmentId);
}
