package com.dropshipshop.api.refund.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.refund.domain.Refund;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

	List<Refund> findAllByOrderByCreatedAtAsc();

	Optional<Refund> findByOrder_Id(UUID orderId);
}
