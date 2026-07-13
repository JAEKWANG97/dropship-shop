package com.dropshipshop.api.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.catalog.domain.ProductImage;
import com.dropshipshop.api.catalog.domain.ProductImageType;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

	List<ProductImage> findAllByProduct_IdOrderBySortOrderAsc(UUID productId);

	boolean existsByProduct_IdAndType(UUID productId, ProductImageType type);

	void deleteAllByProduct_Id(UUID productId);
}
