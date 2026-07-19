package com.dropshipshop.api.order.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.order.domain.AdminOrderActionHistory;

public interface AdminOrderActionHistoryRepository extends JpaRepository<AdminOrderActionHistory, UUID> {

	List<AdminOrderActionHistory> findAllByOrderByCreatedAtDesc();

	List<AdminOrderActionHistory> findAllByOrder_IdOrderByCreatedAtDesc(UUID orderId);
}
