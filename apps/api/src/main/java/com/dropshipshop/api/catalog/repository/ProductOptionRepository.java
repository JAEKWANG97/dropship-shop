package com.dropshipshop.api.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;

public interface ProductOptionRepository extends JpaRepository<ProductOption, UUID> {

	interface ProductOptionCounts {
		UUID getProductId();

		long getOptionCount();

		long getActiveOptionCount();
	}

	interface OptionOwnership {
		UUID getProductId();

		UUID getSupplierId();
	}

	List<ProductOption> findAllByProduct_IdOrderBySortOrderAscCreatedAtAsc(UUID productId);

	Optional<ProductOption> findByIdAndProduct_Id(UUID id, UUID productId);

	@Query("""
		select option.product.id as productId, option.product.supplier.id as supplierId
		from ProductOption option
		where option.id = :optionId
		""")
	Optional<OptionOwnership> findOwnershipById(@Param("optionId") UUID optionId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select option from ProductOption option
		where option.product.id = :productId
		order by option.id
		""")
	List<ProductOption> findAllByProductIdForUpdate(@Param("productId") UUID productId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select option from ProductOption option
		where option.product.id = :productId
		order by option.id
		""")
	List<ProductOption> findAllByProduct_IdForUpdateOrderByIdAsc(@Param("productId") UUID productId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select option from ProductOption option
		where option.id = :optionId and option.product.id = :productId
		""")
	Optional<ProductOption> findByIdAndProductIdForUpdate(
		@Param("optionId") UUID optionId,
		@Param("productId") UUID productId
	);

	boolean existsByProduct_IdAndStatus(UUID productId, ProductOptionStatus status);

	@Query("""
		select option.product.id as productId,
			count(option.id) as optionCount,
			count(case when option.status = com.dropshipshop.api.catalog.domain.ProductOptionStatus.ACTIVE then 1 else null end) as activeOptionCount
		from ProductOption option
		where option.product.id in :productIds
		group by option.product.id
		""")
	List<ProductOptionCounts> countByProductIds(@Param("productIds") List<UUID> productIds);
}
