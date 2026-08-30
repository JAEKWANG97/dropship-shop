package com.dropshipshop.api.shipment.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.shipment.domain.ShipmentItem;

import jakarta.persistence.LockModeType;

public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, UUID> {

	@Query("""
		select allocation
		from ShipmentItem allocation
		where allocation.shipment.order.id = :orderId
		order by allocation.orderItem.id
		""")
	List<ShipmentItem> findAllByOrder_IdOrderByOrderItem_IdAsc(@Param("orderId") UUID orderId);

	List<ShipmentItem> findAllByShipment_IdOrderByOrderItem_IdAsc(UUID shipmentId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select allocation
		from ShipmentItem allocation
		where allocation.shipment.order.id = :orderId
		order by allocation.orderItem.id, allocation.shipment.id
		""")
	List<ShipmentItem> findAllByOrderIdForUpdate(@Param("orderId") UUID orderId);

	@Query("""
		select coalesce(sum(allocation.quantity), 0)
		from ShipmentItem allocation
		where allocation.orderItem.id = :orderItemId
		  and allocation.shipment.status <> com.dropshipshop.api.shipment.domain.ShipmentStatus.VOIDED
		""")
	long sumNonVoidedQuantityByOrderItemId(@Param("orderItemId") UUID orderItemId);
}
