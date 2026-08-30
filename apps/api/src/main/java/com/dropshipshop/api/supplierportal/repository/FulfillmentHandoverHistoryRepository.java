package com.dropshipshop.api.supplierportal.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.supplierportal.domain.FulfillmentHandoverHistory;

public interface FulfillmentHandoverHistoryRepository extends JpaRepository<FulfillmentHandoverHistory, UUID> {

	Optional<FulfillmentHandoverHistory> findByFulfillment_IdAndIdempotencyKey(
		UUID fulfillmentId,
		String idempotencyKey
	);

	List<FulfillmentHandoverHistory> findAllByFulfillment_IdOrderByCreatedAtAsc(UUID fulfillmentId);

	Optional<FulfillmentHandoverHistory> findFirstByFulfillment_IdOrderByCreatedAtDescIdDesc(UUID fulfillmentId);

	@Query("""
		select history
		from FulfillmentHandoverHistory history
		join fetch history.fulfillment fulfillment
		where fulfillment.id in :fulfillmentIds
		order by fulfillment.id, history.createdAt desc, history.id desc
		""")
	List<FulfillmentHandoverHistory> findAllLatestFirst(
		@Param("fulfillmentIds") Collection<UUID> fulfillmentIds
	);
}
