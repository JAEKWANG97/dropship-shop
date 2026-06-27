package com.dropshipshop.api.order.repository;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderStatus;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

	List<CustomerOrder> findAllByPaymentGroup_IdOrderByCreatedAtAsc(UUID paymentGroupId);

	List<CustomerOrder> findAllByUser_IdAndStatusInOrderByCreatedAtDesc(UUID userId, Collection<OrderStatus> statuses);

	Optional<CustomerOrder> findByIdAndUser_Id(UUID id, UUID userId);

	boolean existsByOrderNumber(String orderNumber);
}
