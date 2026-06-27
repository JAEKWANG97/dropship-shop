package com.dropshipshop.api.order.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.order.domain.CustomerOrder;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

	List<CustomerOrder> findAllByPaymentGroup_IdOrderByCreatedAtAsc(UUID paymentGroupId);

	boolean existsByOrderNumber(String orderNumber);
}
