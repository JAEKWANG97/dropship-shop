package com.dropshipshop.api.order.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

	List<OrderItem> findAllByOrder_IdOrderByCreatedAtAsc(UUID orderId);

	@Query("""
		select item
		from OrderItem item
		where item.order.id = :orderId
		  and item.supplier.id = :supplierId
		order by item.createdAt, item.id
		""")
	List<OrderItem> findAllByOrderIdAndSupplierId(
		@Param("orderId") UUID orderId,
		@Param("supplierId") UUID supplierId
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select item
		from OrderItem item
		where item.order.id = :orderId
		order by item.id
		""")
	List<OrderItem> findAllByOrderIdForUpdate(@Param("orderId") UUID orderId);

	long countByOrder_Id(UUID orderId);

	boolean existsByProductOption_IdAndOrder_Status(UUID productOptionId, OrderStatus orderStatus);

	List<OrderItem> findAllByOrder_PaymentGroup_IdOrderByIdAsc(UUID paymentGroupId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select item
		from OrderItem item
		where item.order.paymentGroup.id = :paymentGroupId
		order by item.id
		""")
	List<OrderItem> findAllByPaymentGroupIdForUpdate(@Param("paymentGroupId") UUID paymentGroupId);

	@Query("""
		select distinct item.product.id
		from OrderItem item
		where item.order.paymentGroup.id = :paymentGroupId
		order by item.product.id
		""")
	List<UUID> findProductIdsByPaymentGroupId(@Param("paymentGroupId") UUID paymentGroupId);
}
