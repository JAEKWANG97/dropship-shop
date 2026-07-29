package com.dropshipshop.api.fulfillment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.SupplierPurchaseStatus;

import jakarta.persistence.LockModeType;

public interface FulfillmentRepository extends JpaRepository<Fulfillment, UUID> {

	Optional<Fulfillment> findByOrder_Id(UUID orderId);

	List<Fulfillment> findTop20ByPurchaseStatusOrderByCreatedAtAsc(SupplierPurchaseStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from Fulfillment f where f.id = :id")
	Optional<Fulfillment> findByIdForUpdate(@Param("id") UUID id);
}
