package com.dropshipshop.api.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.catalog.domain.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

	List<ProductImage> findAllByProduct_IdOrderBySortOrderAsc(UUID productId);

	void deleteAllByProduct_Id(UUID productId);
}
