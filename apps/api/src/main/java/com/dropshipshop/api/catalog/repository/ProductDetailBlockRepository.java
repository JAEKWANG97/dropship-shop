package com.dropshipshop.api.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.catalog.domain.ProductDetailBlock;

public interface ProductDetailBlockRepository extends JpaRepository<ProductDetailBlock, UUID> {

	List<ProductDetailBlock> findAllByProduct_IdOrderBySortOrderAsc(UUID productId);

	void deleteAllByProduct_Id(UUID productId);

	@Query("select distinct block.product.id from ProductDetailBlock block where block.product.id in :productIds")
	List<UUID> findProductIdsWithDetailContent(@Param("productIds") List<UUID> productIds);
}
