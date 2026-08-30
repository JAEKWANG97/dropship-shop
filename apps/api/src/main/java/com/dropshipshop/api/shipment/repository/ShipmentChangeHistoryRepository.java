package com.dropshipshop.api.shipment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.shipment.domain.ShipmentChangeHistory;

public interface ShipmentChangeHistoryRepository extends JpaRepository<ShipmentChangeHistory, UUID> {

	Optional<ShipmentChangeHistory> findByShipment_IdAndIdempotencyKey(
		UUID shipmentId,
		String idempotencyKey
	);

	List<ShipmentChangeHistory> findAllByShipment_IdOrderByCreatedAtAscIdAsc(UUID shipmentId);
}
