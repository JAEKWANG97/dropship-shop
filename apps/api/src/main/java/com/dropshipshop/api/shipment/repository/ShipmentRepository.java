package com.dropshipshop.api.shipment.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.shipment.domain.Shipment;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

	Optional<Shipment> findByOrder_Id(UUID orderId);

	boolean existsByOrder_Id(UUID orderId);
}
