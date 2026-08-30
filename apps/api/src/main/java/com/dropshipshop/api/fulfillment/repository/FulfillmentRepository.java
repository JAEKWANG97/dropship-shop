package com.dropshipshop.api.fulfillment.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;
import com.dropshipshop.api.fulfillment.domain.SupplierPurchaseStatus;

import jakarta.persistence.LockModeType;

public interface FulfillmentRepository extends JpaRepository<Fulfillment, UUID> {

	Optional<Fulfillment> findByOrder_Id(UUID orderId);

	@Query("select fulfillment.channel from Fulfillment fulfillment where fulfillment.order.id = :orderId")
	Optional<FulfillmentChannel> findChannelByOrderId(@Param("orderId") UUID orderId);

	@Query("select fulfillment.id from Fulfillment fulfillment where fulfillment.order.id = :orderId")
	Optional<UUID> findIdByOrderId(@Param("orderId") UUID orderId);

	List<Fulfillment> findTop20ByPurchaseStatusOrderByCreatedAtAsc(SupplierPurchaseStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select f from Fulfillment f where f.id = :id")
	Optional<Fulfillment> findByIdForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select fulfillment
		from Fulfillment fulfillment
		where fulfillment.supplier.id = :supplierId
			and fulfillment.channel = com.dropshipshop.api.fulfillment.domain.FulfillmentChannel.SUPPLIER_PORTAL
			and fulfillment.operationalOwner = com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner.SUPPLIER
			and fulfillment.status in (
				com.dropshipshop.api.fulfillment.domain.FulfillmentStatus.PENDING,
				com.dropshipshop.api.fulfillment.domain.FulfillmentStatus.ORDERED
			)
		order by fulfillment.id
		""")
	List<Fulfillment> findOpenPortalSupplierOwnedForUpdate(@Param("supplierId") UUID supplierId);

	boolean existsBySupplier_IdAndChannelAndOperationalOwner(
		UUID supplierId,
		FulfillmentChannel channel,
		FulfillmentOperationalOwner operationalOwner
	);

	boolean existsByOrder_PaymentGroup_Id(UUID paymentGroupId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select fulfillment
		from Fulfillment fulfillment
		where fulfillment.order.paymentGroup.id = :paymentGroupId
		order by fulfillment.id
		""")
	List<Fulfillment> findAllByPaymentGroupIdForUpdate(@Param("paymentGroupId") UUID paymentGroupId);

	@Query("""
		select fulfillment.id
		from Fulfillment fulfillment
		where fulfillment.channel = com.dropshipshop.api.fulfillment.domain.FulfillmentChannel.SUPPLIER_PORTAL
			and fulfillment.operationalOwner = com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner.SUPPLIER
			and fulfillment.status in (
				com.dropshipshop.api.fulfillment.domain.FulfillmentStatus.PENDING,
				com.dropshipshop.api.fulfillment.domain.FulfillmentStatus.ORDERED
			)
			and fulfillment.piiAccessCutoffAt <= :now
		order by fulfillment.piiAccessCutoffAt, fulfillment.id
		""")
	List<UUID> findTopExpiredPortalCandidateIds(@Param("now") Instant now, org.springframework.data.domain.Pageable pageable);

	@Query("""
		select fulfillment
		from Fulfillment fulfillment
		join fetch fulfillment.order customerOrder
		where fulfillment.supplier.id = :supplierId
			and customerOrder.supplier.id = :supplierId
			and fulfillment.channel = com.dropshipshop.api.fulfillment.domain.FulfillmentChannel.SUPPLIER_PORTAL
			and fulfillment.operationalOwner = com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner.SUPPLIER
			and fulfillment.status in (
				com.dropshipshop.api.fulfillment.domain.FulfillmentStatus.PENDING,
				com.dropshipshop.api.fulfillment.domain.FulfillmentStatus.ORDERED
			)
			and customerOrder.paymentGroup.status <> com.dropshipshop.api.payment.domain.PaymentGroupStatus.PAYMENT_EXCEPTION
			and not exists (
				select refund.id from Refund refund
				where (refund.order = customerOrder or refund.paymentGroup = customerOrder.paymentGroup)
					and refund.reason in (
						com.dropshipshop.api.refund.domain.RefundReason.LATE_DEPOSIT_EXCEPTION,
						com.dropshipshop.api.refund.domain.RefundReason.SALE_UNAVAILABLE_AT_DEPOSIT,
						com.dropshipshop.api.refund.domain.RefundReason.PAYMENT_AMOUNT_MISMATCH
					)
			)
		order by fulfillment.requestedAt, fulfillment.id
		""")
	List<Fulfillment> findSupplierQueue(@Param("supplierId") UUID supplierId);

	@Query("""
		select fulfillment
		from Fulfillment fulfillment
		join fetch fulfillment.order customerOrder
		where fulfillment.supplier.id = :supplierId
			and customerOrder.supplier.id = :supplierId
			and customerOrder.orderNumber = :orderNumber
			and fulfillment.channel = com.dropshipshop.api.fulfillment.domain.FulfillmentChannel.SUPPLIER_PORTAL
			and customerOrder.paymentGroup.status <> com.dropshipshop.api.payment.domain.PaymentGroupStatus.PAYMENT_EXCEPTION
			and not exists (
				select refund.id from Refund refund
				where (refund.order = customerOrder or refund.paymentGroup = customerOrder.paymentGroup)
					and refund.reason in (
						com.dropshipshop.api.refund.domain.RefundReason.LATE_DEPOSIT_EXCEPTION,
						com.dropshipshop.api.refund.domain.RefundReason.SALE_UNAVAILABLE_AT_DEPOSIT,
						com.dropshipshop.api.refund.domain.RefundReason.PAYMENT_AMOUNT_MISMATCH
					)
			)
		""")
	Optional<Fulfillment> findSupplierDetail(
		@Param("supplierId") UUID supplierId,
		@Param("orderNumber") String orderNumber
	);

	@Query("""
		select fulfillment
		from Fulfillment fulfillment
		join fetch fulfillment.order customerOrder
		join fetch fulfillment.supplier supplier
		where fulfillment.supplier.id = :supplierId
			and customerOrder.supplier.id = :supplierId
			and customerOrder.id in :orderIds
			and fulfillment.channel = com.dropshipshop.api.fulfillment.domain.FulfillmentChannel.SUPPLIER_PORTAL
			and customerOrder.paymentGroup.status <> com.dropshipshop.api.payment.domain.PaymentGroupStatus.PAYMENT_EXCEPTION
			and not exists (
				select refund.id from Refund refund
				where (refund.order = customerOrder or refund.paymentGroup = customerOrder.paymentGroup)
					and refund.reason in (
						com.dropshipshop.api.refund.domain.RefundReason.LATE_DEPOSIT_EXCEPTION,
						com.dropshipshop.api.refund.domain.RefundReason.SALE_UNAVAILABLE_AT_DEPOSIT,
						com.dropshipshop.api.refund.domain.RefundReason.PAYMENT_AMOUNT_MISMATCH
					)
			)
		order by customerOrder.id
		""")
	List<Fulfillment> findSupplierDetailCandidates(
		@Param("supplierId") UUID supplierId,
		@Param("orderIds") Collection<UUID> orderIds
	);

	@Query("""
		select customerOrder.id
		from Fulfillment fulfillment
		join fulfillment.order customerOrder
		where fulfillment.supplier.id = :supplierId
			and customerOrder.supplier.id = :supplierId
			and customerOrder.orderNumber = :orderNumber
			and fulfillment.channel = com.dropshipshop.api.fulfillment.domain.FulfillmentChannel.SUPPLIER_PORTAL
			and customerOrder.paymentGroup.status <> com.dropshipshop.api.payment.domain.PaymentGroupStatus.PAYMENT_EXCEPTION
			and not exists (
				select refund.id from Refund refund
				where (refund.order = customerOrder or refund.paymentGroup = customerOrder.paymentGroup)
					and refund.reason in (
						com.dropshipshop.api.refund.domain.RefundReason.LATE_DEPOSIT_EXCEPTION,
						com.dropshipshop.api.refund.domain.RefundReason.SALE_UNAVAILABLE_AT_DEPOSIT,
						com.dropshipshop.api.refund.domain.RefundReason.PAYMENT_AMOUNT_MISMATCH
					)
			)
		""")
	Optional<UUID> findSupplierDetailOrderId(
		@Param("supplierId") UUID supplierId,
		@Param("orderNumber") String orderNumber
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select fulfillment from Fulfillment fulfillment where fulfillment.order.id = :orderId")
	Optional<Fulfillment> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}
