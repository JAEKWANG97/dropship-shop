package com.dropshipshop.api.order.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.order.domain.OrderStatusHistory;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {

	List<OrderStatusHistory> findAllByOrder_IdOrderByCreatedAtAsc(UUID orderId);
}
