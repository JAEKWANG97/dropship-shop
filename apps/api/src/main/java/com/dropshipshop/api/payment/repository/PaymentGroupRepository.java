package com.dropshipshop.api.payment.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.payment.domain.PaymentGroup;

public interface PaymentGroupRepository extends JpaRepository<PaymentGroup, UUID> {

	Optional<PaymentGroup> findByCheckoutNumberAndUser_Id(String checkoutNumber, UUID userId);

	boolean existsByCheckoutNumber(String checkoutNumber);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select paymentGroup from PaymentGroup paymentGroup where paymentGroup.id = :id")
	Optional<PaymentGroup> findByIdForUpdate(@Param("id") UUID id);

	@Query("""
		select item.order.paymentGroup.id
		from OrderItem item
		where item.order.paymentGroup.status = com.dropshipshop.api.payment.domain.PaymentGroupStatus.PAYMENT_PENDING
			and item.order.paymentGroup.expiresAt <= :now
			and item.managementChannelSnapshot = com.dropshipshop.api.catalog.domain.ProductManagementChannel.SUPPLIER_PORTAL
			and item.inventoryModeSnapshot = com.dropshipshop.api.catalog.domain.InventoryMode.TRACKED
			and item.reservationStatus = com.dropshipshop.api.order.domain.OrderItemReservationStatus.HELD
		group by item.order.paymentGroup.id, item.order.paymentGroup.expiresAt
		order by item.order.paymentGroup.expiresAt, item.order.paymentGroup.id
		""")
	List<UUID> findExpiryCandidateIds(@Param("now") Instant now, Pageable pageable);
}
