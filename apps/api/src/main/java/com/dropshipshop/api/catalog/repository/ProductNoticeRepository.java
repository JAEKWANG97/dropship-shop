package com.dropshipshop.api.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.catalog.domain.ProductNotice;
import com.dropshipshop.api.catalog.domain.ProductNoticeStatus;

public interface ProductNoticeRepository extends JpaRepository<ProductNotice, UUID> {

	Optional<ProductNotice> findFirstByProduct_IdAndStatusOrderByVersionDesc(UUID productId, ProductNoticeStatus status);

	int countByProduct_Id(UUID productId);
}
