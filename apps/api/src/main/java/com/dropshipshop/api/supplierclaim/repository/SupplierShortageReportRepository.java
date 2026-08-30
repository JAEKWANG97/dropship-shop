package com.dropshipshop.api.supplierclaim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.supplierclaim.domain.SupplierShortageReport;
import com.dropshipshop.api.supplierclaim.domain.SupplierShortageStatus;

import jakarta.persistence.LockModeType;

public interface SupplierShortageReportRepository extends JpaRepository<SupplierShortageReport, UUID> {

	Optional<SupplierShortageReport> findBySupplier_IdAndIdempotencyKey(UUID supplierId, String idempotencyKey);

	Optional<SupplierShortageReport> findByOrder_Id(UUID orderId);

	Optional<SupplierShortageReport> findByIdAndSupplier_Id(UUID id, UUID supplierId);

	@Query("""
		select report from SupplierShortageReport report
		join fetch report.order customerOrder
		join fetch report.supplier supplier
		where report.supplier.id = :supplierId
		  and (:status is null or report.status = :status)
		order by report.createdAt desc, report.id desc
		""")
	List<SupplierShortageReport> findSupplierList(
		@Param("supplierId") UUID supplierId,
		@Param("status") SupplierShortageStatus status
	);

	@Query("""
		select report from SupplierShortageReport report
		join fetch report.order customerOrder
		join fetch report.supplier supplier
		where (:status is null or report.status = :status)
		  and (:orderId is null or report.order.id = :orderId)
		order by report.createdAt desc, report.id desc
		""")
	List<SupplierShortageReport> findAdminList(
		@Param("status") SupplierShortageStatus status,
		@Param("orderId") UUID orderId
	);

	@Query("select report.order.id from SupplierShortageReport report where report.id = :id")
	Optional<UUID> findOrderIdById(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select report from SupplierShortageReport report where report.id = :id")
	Optional<SupplierShortageReport> findByIdForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select report from SupplierShortageReport report where report.order.id = :orderId")
	Optional<SupplierShortageReport> findByOrderIdForUpdate(@Param("orderId") UUID orderId);
}
