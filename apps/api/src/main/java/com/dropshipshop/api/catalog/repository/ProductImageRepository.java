package com.dropshipshop.api.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.catalog.domain.ProductImage;
import com.dropshipshop.api.catalog.domain.ProductImageType;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

	List<ProductImage> findAllByProduct_IdOrderBySortOrderAsc(UUID productId);

	boolean existsByProduct_IdAndType(UUID productId, ProductImageType type);

	void deleteAllByProduct_Id(UUID productId);

	@Query("select distinct image.product.id from ProductImage image where image.product.id in :productIds and image.type = :type")
	List<UUID> findProductIdsByType(@Param("productIds") List<UUID> productIds, @Param("type") ProductImageType type);
}
