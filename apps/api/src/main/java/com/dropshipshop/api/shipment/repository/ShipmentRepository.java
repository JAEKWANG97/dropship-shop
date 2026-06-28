package com.dropshipshop.api.shipment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.shipment.domain.Shipment;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

	Optional<Shipment> findByOrder_Id(UUID orderId);

	List<Shipment> findAllByCarrierAndTrackingNumber(String carrier, String trackingNumber);

	boolean existsByOrder_Id(UUID orderId);
}
