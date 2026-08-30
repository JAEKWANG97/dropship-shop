package com.dropshipshop.api.order.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderStatus;

import jakarta.persistence.LockModeType;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

	List<CustomerOrder> findAllByOrderByCreatedAtAsc();

	List<CustomerOrder> findAllByPaymentGroup_IdOrderByCreatedAtAsc(UUID paymentGroupId);

	List<CustomerOrder> findAllByStatusOrderByCreatedAtAsc(OrderStatus status);

	@Query(
		value = """
			select customerOrder from CustomerOrder customerOrder
			join fetch customerOrder.user customer
			join fetch customerOrder.supplier supplier
			join fetch customerOrder.paymentGroup paymentGroup
			where customerOrder.status = :status
			and (lower(customerOrder.orderNumber) like :keyword
				or lower(customer.email) like :keyword)
			and customerOrder.createdAt >= :fromTime
			and customerOrder.createdAt < :toTime
			""",
		countQuery = """
			select count(customerOrder) from CustomerOrder customerOrder
			join customerOrder.user customer
			where customerOrder.status = :status
			and (lower(customerOrder.orderNumber) like :keyword
				or lower(customer.email) like :keyword)
			and customerOrder.createdAt >= :fromTime
			and customerOrder.createdAt < :toTime
			"""
	)
	Page<CustomerOrder> findAdminOrders(
		@Param("status") OrderStatus status,
		@Param("keyword") String keyword,
		@Param("fromTime") Instant fromTime,
		@Param("toTime") Instant toTime,
		Pageable pageable
	);

	List<CustomerOrder> findAllByUser_IdAndStatusInOrderByCreatedAtDesc(UUID userId, Collection<OrderStatus> statuses);

	List<CustomerOrder> findTop5ByUser_IdAndStatusNotInOrderByCreatedAtDesc(UUID userId, Collection<OrderStatus> statuses);

	Optional<CustomerOrder> findByIdAndUser_Id(UUID id, UUID userId);

	boolean existsByOrderNumber(String orderNumber);

	@Query("select customerOrder.paymentGroup.id from CustomerOrder customerOrder where customerOrder.id = :orderId")
	Optional<UUID> findPaymentGroupIdByOrderId(@Param("orderId") UUID orderId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select customerOrder
		from CustomerOrder customerOrder
		where customerOrder.paymentGroup.id = :paymentGroupId
		order by customerOrder.id
		""")
	List<CustomerOrder> findAllByPaymentGroupIdForUpdate(@Param("paymentGroupId") UUID paymentGroupId);

	@Query("""
		select distinct customerOrder.supplier.id
		from CustomerOrder customerOrder
		where customerOrder.paymentGroup.id = :paymentGroupId
		order by customerOrder.supplier.id
		""")
	List<UUID> findSupplierIdsByPaymentGroupId(@Param("paymentGroupId") UUID paymentGroupId);
}
