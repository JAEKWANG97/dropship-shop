package com.dropshipshop.api.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.catalog.domain.ProductChangeHistory;

public interface ProductChangeHistoryRepository extends JpaRepository<ProductChangeHistory, UUID> {

	List<ProductChangeHistory> findAllByProduct_IdOrderByCreatedAtAsc(UUID productId);

	List<ProductChangeHistory> findAllBySubjectProductIdOrderByCreatedAtAsc(UUID subjectProductId);

	boolean existsBySubjectProductId(UUID subjectProductId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update ProductChangeHistory history
		set history.productOption = null
		where history.subjectProductOptionId = :subjectProductOptionId
		""")
	int clearLiveOptionReference(@Param("subjectProductOptionId") UUID subjectProductOptionId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update ProductChangeHistory history
		set history.product = null, history.productOption = null
		where history.subjectProductId = :subjectProductId
		""")
	int clearLiveProductReferences(@Param("subjectProductId") UUID subjectProductId);
}
