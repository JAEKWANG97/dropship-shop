package com.dropshipshop.api.supplierclaim.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTask;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTaskStatus;

import jakarta.persistence.LockModeType;

public interface SupplierClaimTaskRepository extends JpaRepository<SupplierClaimTask, UUID> {

	interface TaskScope {
		UUID getClaimId();
		UUID getOrderId();
		UUID getSupplierId();
	}

	interface TaskItemRow {
		UUID getTaskId();
		String getProductName();
		String getOptionName();
		int getQuantity();
	}

	Optional<SupplierClaimTask> findByClaim_IdAndCreationIdempotencyKey(UUID claimId, String key);

	Optional<SupplierClaimTask> findByIdAndSupplier_Id(UUID id, UUID supplierId);

	@Query("""
		select task.claim.id as claimId,
			task.order.id as orderId,
			task.supplier.id as supplierId
		from SupplierClaimTask task
		where task.id = :taskId
		""")
	Optional<TaskScope> findScopeById(@Param("taskId") UUID taskId);

	@Query("""
		select task.claim.id as claimId,
			task.order.id as orderId,
			task.supplier.id as supplierId
		from SupplierClaimTask task
		where task.id = :taskId
		  and task.supplier.id = :supplierId
		""")
	Optional<TaskScope> findScopeByIdAndSupplierId(
		@Param("taskId") UUID taskId,
		@Param("supplierId") UUID supplierId
	);

	@Query("""
		select task from SupplierClaimTask task
		join fetch task.order customerOrder
		join fetch task.supplier supplier
		where task.supplier.id = :supplierId
		  and (:status is null or task.status = :status)
		order by task.requestedAt desc, task.id desc
		""")
	List<SupplierClaimTask> findSupplierList(
		@Param("supplierId") UUID supplierId,
		@Param("status") SupplierClaimTaskStatus status
	);

	@Query("""
		select task from SupplierClaimTask task
		join fetch task.order customerOrder
		join fetch task.supplier supplier
		where (:status is null or task.status = :status)
		  and (:claimId is null or task.claim.id = :claimId)
		  and (:orderId is null or task.claim.order.id = :orderId)
		order by task.requestedAt desc, task.id desc
		""")
	List<SupplierClaimTask> findAdminList(
		@Param("status") SupplierClaimTaskStatus status,
		@Param("claimId") UUID claimId,
		@Param("orderId") UUID orderId
	);

	@Query("""
		select task.id as taskId,
			item.productName as productName,
			item.optionName as optionName,
			item.quantity as quantity
		from SupplierClaimTask task, OrderItem item
		where task.id in :taskIds
		  and task.supplier.id = :supplierId
		  and item.order.id = task.order.id
		  and item.supplier.id = :supplierId
		order by task.id, item.createdAt, item.id
		""")
	List<TaskItemRow> findSupplierListItems(
		@Param("taskIds") Collection<UUID> taskIds,
		@Param("supplierId") UUID supplierId
	);

	@Query("""
		select task.id as taskId,
			item.productName as productName,
			item.optionName as optionName,
			item.quantity as quantity
		from SupplierClaimTask task, OrderItem item
		where task.id in :taskIds
		  and item.order.id = task.order.id
		  and item.supplier.id = task.supplier.id
		order by task.id, item.createdAt, item.id
		""")
	List<TaskItemRow> findAdminListItems(@Param("taskIds") Collection<UUID> taskIds);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select task from SupplierClaimTask task where task.claim.id = :claimId order by task.id")
	List<SupplierClaimTask> findAllByClaimIdForUpdate(@Param("claimId") UUID claimId);

	@Query("select task.id from SupplierClaimTask task where task.status in :statuses and task.dueAt <= :now order by task.dueAt, task.id")
	List<UUID> findExpiredCandidateIds(
		@Param("statuses") Collection<SupplierClaimTaskStatus> statuses,
		@Param("now") Instant now,
		Pageable pageable
	);
}
