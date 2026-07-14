package com.dropshipshop.api.catalog.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	@EntityGraph(attributePaths = "supplier")
	List<Product> findAllByStatus(ProductStatus status);

	@Query(
		value = """
			select product from Product product
			join fetch product.supplier supplier
			where (:keyword is null
				or lower(product.name) like :keyword
				or lower(product.summary) like :keyword
				or lower(supplier.name) like :keyword)
			and (:status is null or product.status = :status)
			and (:category is null or product.categoryCode = :category)
			and (:supplierId is null or supplier.id = :supplierId)
			""",
		countQuery = """
			select count(product) from Product product
			join product.supplier supplier
			where (:keyword is null
				or lower(product.name) like :keyword
				or lower(product.summary) like :keyword
				or lower(supplier.name) like :keyword)
			and (:status is null or product.status = :status)
			and (:category is null or product.categoryCode = :category)
			and (:supplierId is null or supplier.id = :supplierId)
			"""
	)
	Page<Product> findAdminProducts(
		@Param("keyword") String keyword,
		@Param("status") ProductStatus status,
		@Param("category") ProductCategory category,
		@Param("supplierId") UUID supplierId,
		Pageable pageable
	);
}
