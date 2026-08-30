package com.dropshipshop.api.supplierclaim.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.supplierclaim.domain.SupplierClaimFact;

import jakarta.persistence.LockModeType;

public interface SupplierClaimFactRepository extends JpaRepository<SupplierClaimFact, UUID> {

	Optional<SupplierClaimFact> findByTask_IdAndTask_Supplier_IdAndIdempotencyKey(
		UUID taskId,
		UUID supplierId,
		String key
	);

	List<SupplierClaimFact> findAllByTask_IdOrderByCreatedAtAscIdAsc(UUID taskId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select fact from SupplierClaimFact fact where fact.task.id = :taskId order by fact.id")
	List<SupplierClaimFact> findAllByTaskIdForUpdate(@Param("taskId") UUID taskId);
}
