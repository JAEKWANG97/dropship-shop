package com.dropshipshop.api.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.catalog.domain.ProductDetailBlock;

public interface ProductDetailBlockRepository extends JpaRepository<ProductDetailBlock, UUID> {

	List<ProductDetailBlock> findAllByProduct_IdOrderBySortOrderAsc(UUID productId);

	void deleteAllByProduct_Id(UUID productId);
}
