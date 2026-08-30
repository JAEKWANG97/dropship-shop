package com.dropshipshop.api.supplierproduct.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.supplierproduct.domain.SupplierInventoryChangeHistory;

public interface SupplierInventoryChangeHistoryRepository
	extends JpaRepository<SupplierInventoryChangeHistory, UUID> {

	Optional<SupplierInventoryChangeHistory> findBySupplier_IdAndSubjectProductOptionIdAndIdempotencyKey(
		UUID supplierId,
		UUID subjectProductOptionId,
		String idempotencyKey
	);

	List<SupplierInventoryChangeHistory> findAllBySubjectProductOptionIdOrderByCreatedAtAsc(
		UUID subjectProductOptionId
	);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update SupplierInventoryChangeHistory history
		set history.productOption = null
		where history.subjectProductOptionId in :subjectProductOptionIds
		""")
	int clearLiveOptionReferences(@Param("subjectProductOptionIds") List<UUID> subjectProductOptionIds);
}
