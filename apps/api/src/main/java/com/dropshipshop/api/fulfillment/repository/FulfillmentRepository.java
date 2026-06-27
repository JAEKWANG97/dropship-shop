package com.dropshipshop.api.fulfillment.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.fulfillment.domain.Fulfillment;

public interface FulfillmentRepository extends JpaRepository<Fulfillment, UUID> {

	Optional<Fulfillment> findByOrder_Id(UUID orderId);
}
