package com.dropshipshop.api.shipment.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.domain.ShipmentStatus;

import jakarta.persistence.LockModeType;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

	List<Shipment> findAllByOrder_IdOrderByRegisteredAtAscIdAsc(UUID orderId);

	List<Shipment> findAllByOrder_IdAndStatusNotOrderByRegisteredAtAscIdAsc(
		UUID orderId,
		ShipmentStatus status
	);

	Optional<Shipment> findByOrder_IdAndIdempotencyKey(UUID orderId, String idempotencyKey);

	List<Shipment> findAllByCarrierAndTrackingNumber(String carrier, String trackingNumber);

	boolean existsByOrder_Id(UUID orderId);

	long countByOrder_IdAndStatusNot(UUID orderId, ShipmentStatus status);

	@Query("""
		select count(shipment)
		from Shipment shipment
		where shipment.order.id = :orderId
		  and shipment.status <> com.dropshipshop.api.shipment.domain.ShipmentStatus.VOIDED
		""")
	long countNonVoided(@Param("orderId") UUID orderId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select shipment from Shipment shipment where shipment.id = :id")
	Optional<Shipment> findByIdForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select shipment
		from Shipment shipment
		where shipment.order.id = :orderId
		order by shipment.id
		""")
	List<Shipment> findAllByOrderIdForUpdate(@Param("orderId") UUID orderId);

	@Query("select shipment.order.id from Shipment shipment where shipment.id = :shipmentId")
	Optional<UUID> findOrderIdByShipmentId(@Param("shipmentId") UUID shipmentId);

	@Query("""
		select max(shipment.deliveredAt)
		from Shipment shipment
		where shipment.order.id = :orderId
		  and shipment.status <> com.dropshipshop.api.shipment.domain.ShipmentStatus.VOIDED
		""")
	Optional<Instant> findMaxNonVoidedDeliveredAt(@Param("orderId") UUID orderId);
}
