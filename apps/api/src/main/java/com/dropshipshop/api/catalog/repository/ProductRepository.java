package com.dropshipshop.api.catalog.repository;

import java.util.List;
import java.util.Optional;
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

	Optional<Product> findBySupplier_IdAndName(UUID supplierId, String name);

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
			and (
				:saleReady is null
				or (:saleReady = true
					and product.basePrice > 0
					and product.complianceStatus <> com.dropshipshop.api.catalog.domain.ProductComplianceStatus.REJECTED
					and exists (select image.id from ProductImage image where image.product = product and image.type = com.dropshipshop.api.catalog.domain.ProductImageType.THUMBNAIL)
					and exists (select option.id from ProductOption option where option.product = product and option.status = com.dropshipshop.api.catalog.domain.ProductOptionStatus.ACTIVE)
					and exists (select notice.id from ProductNotice notice where notice.product = product and notice.status = com.dropshipshop.api.catalog.domain.ProductNoticeStatus.ACTIVE)
				)
				or (:saleReady = false and (
					product.basePrice <= 0
					or product.complianceStatus = com.dropshipshop.api.catalog.domain.ProductComplianceStatus.REJECTED
					or not exists (select image.id from ProductImage image where image.product = product and image.type = com.dropshipshop.api.catalog.domain.ProductImageType.THUMBNAIL)
					or not exists (select option.id from ProductOption option where option.product = product and option.status = com.dropshipshop.api.catalog.domain.ProductOptionStatus.ACTIVE)
					or not exists (select notice.id from ProductNotice notice where notice.product = product and notice.status = com.dropshipshop.api.catalog.domain.ProductNoticeStatus.ACTIVE)
				))
			)
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
			and (
				:saleReady is null
				or (:saleReady = true
					and product.basePrice > 0
					and product.complianceStatus <> com.dropshipshop.api.catalog.domain.ProductComplianceStatus.REJECTED
					and exists (select image.id from ProductImage image where image.product = product and image.type = com.dropshipshop.api.catalog.domain.ProductImageType.THUMBNAIL)
					and exists (select option.id from ProductOption option where option.product = product and option.status = com.dropshipshop.api.catalog.domain.ProductOptionStatus.ACTIVE)
					and exists (select notice.id from ProductNotice notice where notice.product = product and notice.status = com.dropshipshop.api.catalog.domain.ProductNoticeStatus.ACTIVE)
				)
				or (:saleReady = false and (
					product.basePrice <= 0
					or product.complianceStatus = com.dropshipshop.api.catalog.domain.ProductComplianceStatus.REJECTED
					or not exists (select image.id from ProductImage image where image.product = product and image.type = com.dropshipshop.api.catalog.domain.ProductImageType.THUMBNAIL)
					or not exists (select option.id from ProductOption option where option.product = product and option.status = com.dropshipshop.api.catalog.domain.ProductOptionStatus.ACTIVE)
					or not exists (select notice.id from ProductNotice notice where notice.product = product and notice.status = com.dropshipshop.api.catalog.domain.ProductNoticeStatus.ACTIVE)
				))
			)
			"""
	)
	Page<Product> findAdminProducts(
		@Param("keyword") String keyword,
		@Param("status") ProductStatus status,
		@Param("category") ProductCategory category,
		@Param("supplierId") UUID supplierId,
		@Param("saleReady") Boolean saleReady,
		Pageable pageable
	);
}
