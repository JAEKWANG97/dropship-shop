package com.dropshipshop.api.catalog.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductReviewStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, UUID> {

	@EntityGraph(attributePaths = "supplier")
	List<Product> findAllByStatus(ProductStatus status);

	Optional<Product> findBySupplier_IdAndName(UUID supplierId, String name);

	Optional<Product> findBySourceItemNo(String sourceItemNo);

	@Query("select product.supplier.id from Product product where product.id = :id")
	Optional<UUID> findSupplierIdById(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select product from Product product where product.id = :id")
	Optional<Product> findByIdForUpdate(@Param("id") UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select product from Product product
		where product.id = :id
		and product.supplier.id = :supplierId
		and product.managementChannel = :managementChannel
		""")
	Optional<Product> findByIdAndSupplierIdAndManagementChannelForUpdate(
		@Param("id") UUID id,
		@Param("supplierId") UUID supplierId,
		@Param("managementChannel") ProductManagementChannel managementChannel
	);

	Optional<Product> findByIdAndSupplier_IdAndManagementChannel(
		UUID id,
		UUID supplierId,
		ProductManagementChannel managementChannel
	);

	Page<Product> findAllBySupplier_IdAndManagementChannel(
		UUID supplierId,
		ProductManagementChannel managementChannel,
		Pageable pageable
	);

	@Query("""
		select product from Product product
		where product.sourceItemNo is not null
		and product.managementChannel = com.dropshipshop.api.catalog.domain.ProductManagementChannel.COREABLE
		and (
			product.status = com.dropshipshop.api.catalog.domain.ProductStatus.ACTIVE
				or (product.status = com.dropshipshop.api.catalog.domain.ProductStatus.SOLD_OUT and product.sourceAutoSoldOut = true)
		)
		order by case when product.sourceSyncedAt is null then 0 else 1 end, product.sourceSyncedAt, product.id
		""")
	List<Product> findSourceSyncTargets(Pageable pageable);

	@Query(
		value = """
			select product from Product product
			join product.supplier supplier
			where product.status = com.dropshipshop.api.catalog.domain.ProductStatus.ACTIVE
			and supplier.status = com.dropshipshop.api.catalog.domain.SupplierStatus.ACTIVE
			and product.complianceStatus <> com.dropshipshop.api.catalog.domain.ProductComplianceStatus.REJECTED
			and (
				product.managementChannel = com.dropshipshop.api.catalog.domain.ProductManagementChannel.COREABLE
				or (
					product.managementChannel = com.dropshipshop.api.catalog.domain.ProductManagementChannel.SUPPLIER_PORTAL
					and :portalEnabled = true
					and product.reviewStatus in (
						com.dropshipshop.api.catalog.domain.ProductReviewStatus.AUTO_APPROVED,
						com.dropshipshop.api.catalog.domain.ProductReviewStatus.APPROVED
					)
					and supplier.portalContractStatus = com.dropshipshop.api.catalog.domain.SupplierPortalContractStatus.VERIFIED
					and supplier.portalContractEffectiveAt is not null
					and supplier.portalContractEffectiveAt <= :now
					and (supplier.portalContractExpiresAt is null or :now < supplier.portalContractExpiresAt)
				)
			)
			and (:keyword is null
				or lower(product.name) like :keyword
				or lower(product.summary) like :keyword)
			and product.categoryCode in :categories
			and product.basePrice >= :minPrice
			and (:maxPrice is null or product.basePrice <= :maxPrice)
			""",
		countQuery = """
			select count(product) from Product product
			join product.supplier supplier
			where product.status = com.dropshipshop.api.catalog.domain.ProductStatus.ACTIVE
			and supplier.status = com.dropshipshop.api.catalog.domain.SupplierStatus.ACTIVE
			and product.complianceStatus <> com.dropshipshop.api.catalog.domain.ProductComplianceStatus.REJECTED
			and (
				product.managementChannel = com.dropshipshop.api.catalog.domain.ProductManagementChannel.COREABLE
				or (
					product.managementChannel = com.dropshipshop.api.catalog.domain.ProductManagementChannel.SUPPLIER_PORTAL
					and :portalEnabled = true
					and product.reviewStatus in (
						com.dropshipshop.api.catalog.domain.ProductReviewStatus.AUTO_APPROVED,
						com.dropshipshop.api.catalog.domain.ProductReviewStatus.APPROVED
					)
					and supplier.portalContractStatus = com.dropshipshop.api.catalog.domain.SupplierPortalContractStatus.VERIFIED
					and supplier.portalContractEffectiveAt is not null
					and supplier.portalContractEffectiveAt <= :now
					and (supplier.portalContractExpiresAt is null or :now < supplier.portalContractExpiresAt)
				)
			)
			and (:keyword is null
				or lower(product.name) like :keyword
				or lower(product.summary) like :keyword)
			and product.categoryCode in :categories
			and product.basePrice >= :minPrice
			and (:maxPrice is null or product.basePrice <= :maxPrice)
			"""
	)
	Page<Product> findPublicProducts(
		@Param("keyword") String keyword,
		@Param("categories") List<ProductCategory> categories,
		@Param("minPrice") long minPrice,
		@Param("maxPrice") Long maxPrice,
		@Param("portalEnabled") boolean portalEnabled,
		@Param("now") Instant now,
		Pageable pageable
	);

	default Page<Product> findPublicProducts(
		String keyword,
		List<ProductCategory> categories,
		long minPrice,
		Long maxPrice,
		Pageable pageable
	) {
		return findPublicProducts(keyword, categories, minPrice, maxPrice, false, Instant.now(), pageable);
	}

	@Query("""
		select product.categoryCode as categoryCode, count(product) as productCount
		from Product product
		join product.supplier supplier
		where product.status = com.dropshipshop.api.catalog.domain.ProductStatus.ACTIVE
		and supplier.status = com.dropshipshop.api.catalog.domain.SupplierStatus.ACTIVE
		and product.complianceStatus <> com.dropshipshop.api.catalog.domain.ProductComplianceStatus.REJECTED
		and (
			product.managementChannel = com.dropshipshop.api.catalog.domain.ProductManagementChannel.COREABLE
			or (
				product.managementChannel = com.dropshipshop.api.catalog.domain.ProductManagementChannel.SUPPLIER_PORTAL
				and :portalEnabled = true
				and product.reviewStatus in (
					com.dropshipshop.api.catalog.domain.ProductReviewStatus.AUTO_APPROVED,
					com.dropshipshop.api.catalog.domain.ProductReviewStatus.APPROVED
				)
				and supplier.portalContractStatus = com.dropshipshop.api.catalog.domain.SupplierPortalContractStatus.VERIFIED
				and supplier.portalContractEffectiveAt is not null
				and supplier.portalContractEffectiveAt <= :now
				and (supplier.portalContractExpiresAt is null or :now < supplier.portalContractExpiresAt)
			)
		)
		and (:keyword is null
			or lower(product.name) like :keyword
			or lower(product.summary) like :keyword)
		and product.basePrice >= :minPrice
		and (:maxPrice is null or product.basePrice <= :maxPrice)
		group by product.categoryCode
		""")
	List<ProductCategoryCount> countPublicProductsByCategory(
		@Param("keyword") String keyword,
		@Param("minPrice") long minPrice,
		@Param("maxPrice") Long maxPrice,
		@Param("portalEnabled") boolean portalEnabled,
		@Param("now") Instant now
	);

	default List<ProductCategoryCount> countPublicProductsByCategory(
		String keyword,
		long minPrice,
		Long maxPrice
	) {
		return countPublicProductsByCategory(keyword, minPrice, maxPrice, false, Instant.now());
	}

	@Query("""
		select product from Product product
		join fetch product.supplier supplier
		where product.id = :productId
		and product.status not in (
			com.dropshipshop.api.catalog.domain.ProductStatus.HIDDEN,
			com.dropshipshop.api.catalog.domain.ProductStatus.STOPPED
		)
		and supplier.status = com.dropshipshop.api.catalog.domain.SupplierStatus.ACTIVE
		and product.complianceStatus <> com.dropshipshop.api.catalog.domain.ProductComplianceStatus.REJECTED
		and (
			product.managementChannel = com.dropshipshop.api.catalog.domain.ProductManagementChannel.COREABLE
			or (
				product.managementChannel = com.dropshipshop.api.catalog.domain.ProductManagementChannel.SUPPLIER_PORTAL
				and :portalEnabled = true
				and product.reviewStatus in (
					com.dropshipshop.api.catalog.domain.ProductReviewStatus.AUTO_APPROVED,
					com.dropshipshop.api.catalog.domain.ProductReviewStatus.APPROVED
				)
				and supplier.portalContractStatus = com.dropshipshop.api.catalog.domain.SupplierPortalContractStatus.VERIFIED
				and supplier.portalContractEffectiveAt is not null
				and supplier.portalContractEffectiveAt <= :now
				and (supplier.portalContractExpiresAt is null or :now < supplier.portalContractExpiresAt)
			)
		)
		""")
	Optional<Product> findPublicProductById(
		@Param("productId") UUID productId,
		@Param("portalEnabled") boolean portalEnabled,
		@Param("now") Instant now
	);

	@Query(
		value = """
			select product from Product product
			join fetch product.supplier supplier
			where product.managementChannel = com.dropshipshop.api.catalog.domain.ProductManagementChannel.SUPPLIER_PORTAL
			and product.reviewStatus in :reviewStatuses
			""",
		countQuery = """
			select count(product) from Product product
			where product.managementChannel = com.dropshipshop.api.catalog.domain.ProductManagementChannel.SUPPLIER_PORTAL
			and product.reviewStatus in :reviewStatuses
			"""
	)
	Page<Product> findReviewQueue(
		@Param("reviewStatuses") List<ProductReviewStatus> reviewStatuses,
		Pageable pageable
	);

	@EntityGraph(attributePaths = "supplier")
	@Query("select product from Product product where product.id = :id")
	Optional<Product> findReviewProductById(@Param("id") UUID id);

	@Query("select (count(item) > 0) from CartItem item where item.product.id = :productId")
	boolean existsCartReferenceByProductId(@Param("productId") UUID productId);

	@Query("select (count(item) > 0) from OrderItem item where item.product.id = :productId")
	boolean existsOrderReferenceByProductId(@Param("productId") UUID productId);

	@Query("select (count(item) > 0) from CartItem item where item.productOption.id = :optionId")
	boolean existsCartReferenceByOptionId(@Param("optionId") UUID optionId);

	@Query("select (count(item) > 0) from OrderItem item where item.productOption.id = :optionId")
	boolean existsOrderReferenceByOptionId(@Param("optionId") UUID optionId);

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

	interface ProductCategoryCount {
		ProductCategory getCategoryCode();

		long getProductCount();
	}
}
