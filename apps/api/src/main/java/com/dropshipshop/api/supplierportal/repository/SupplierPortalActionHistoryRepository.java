package com.dropshipshop.api.supplierportal.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.supplierportal.domain.SupplierPortalActionHistory;

public interface SupplierPortalActionHistoryRepository extends JpaRepository<SupplierPortalActionHistory, UUID> {

	Optional<SupplierPortalActionHistory> findBySupplier_IdAndIdempotencyKey(
		UUID supplierId,
		String idempotencyKey
	);

	boolean existsBySupplier_Id(UUID supplierId);

	List<SupplierPortalActionHistory> findAllBySupplier_IdOrderByCreatedAtAsc(UUID supplierId);
}
