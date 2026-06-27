package com.dropshipshop.api.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	@EntityGraph(attributePaths = "supplier")
	List<Product> findAllByStatus(ProductStatus status);

	@EntityGraph(attributePaths = "supplier")
	List<Product> findAllByOrderByCreatedAtDesc();
}
