package com.dropshipshop.api.order.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.order.domain.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

	List<OrderItem> findAllByOrder_IdOrderByCreatedAtAsc(UUID orderId);
}
