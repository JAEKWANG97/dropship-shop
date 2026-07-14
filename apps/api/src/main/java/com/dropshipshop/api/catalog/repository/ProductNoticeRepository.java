package com.dropshipshop.api.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.catalog.domain.ProductNotice;
import com.dropshipshop.api.catalog.domain.ProductNoticeStatus;

public interface ProductNoticeRepository extends JpaRepository<ProductNotice, UUID> {

	Optional<ProductNotice> findFirstByProduct_IdAndStatusOrderByVersionDesc(UUID productId, ProductNoticeStatus status);

	boolean existsByProduct_IdAndStatus(UUID productId, ProductNoticeStatus status);

	int countByProduct_Id(UUID productId);

	@Query("select distinct notice.product.id from ProductNotice notice where notice.product.id in :productIds and notice.status = :status")
	List<UUID> findProductIdsByStatus(@Param("productIds") List<UUID> productIds, @Param("status") ProductNoticeStatus status);
}
