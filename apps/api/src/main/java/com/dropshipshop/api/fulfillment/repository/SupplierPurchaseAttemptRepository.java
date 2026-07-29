package com.dropshipshop.api.fulfillment.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.fulfillment.domain.SupplierPurchaseAttempt;

public interface SupplierPurchaseAttemptRepository extends JpaRepository<SupplierPurchaseAttempt, UUID> {

	List<SupplierPurchaseAttempt> findAllByFulfillment_IdOrderByCreatedAtDesc(UUID fulfillmentId);
}
