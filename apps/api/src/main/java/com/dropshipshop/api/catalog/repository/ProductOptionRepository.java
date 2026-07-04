package com.dropshipshop.api.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.catalog.domain.ProductOption;

public interface ProductOptionRepository extends JpaRepository<ProductOption, UUID> {

	List<ProductOption> findAllByProduct_IdOrderBySortOrderAscCreatedAtAsc(UUID productId);

	Optional<ProductOption> findByIdAndProduct_Id(UUID id, UUID productId);
}
