package com.dropshipshop.api.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.dropshipshop.api.catalog.domain.ProductChangeType;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductDetailBlockType;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.SupplierStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class CatalogDtos {

	private CatalogDtos() {
	}

	record SupplierRequest(
		@NotBlank @Size(max = 100) String name,
		@Size(max = 100) String contactName,
		@Size(max = 30) String phone,
		@Email @Size(max = 320) String email,
		String memo,
		SupplierStatus status
	) {
	}

	record SupplierResponse(
		UUID id,
		String name,
		String contactName,
		String phone,
		String email,
		String memo,
		SupplierStatus status
	) {
	}

	record ProductCreateRequest(
		@NotNull UUID supplierId,
		@NotBlank @Size(max = 200) String name,
		@NotBlank @Size(max = 500) String summary,
		@Min(0) Long sourcePrice,
		@Min(0) long basePrice,
		@NotNull ProductCategory categoryCode,
		@NotNull ProductStatus status
	) {
	}

	record ProductUpdateRequest(
		@NotNull UUID supplierId,
		@NotBlank @Size(max = 200) String name,
		@NotBlank @Size(max = 500) String summary,
		@Min(0) Long sourcePrice,
		@Min(0) long basePrice,
		@NotNull ProductCategory categoryCode,
		@NotBlank @Size(max = 500) String reason
	) {
	}

	record PricingPolicyRequest(
		@NotBlank @Size(max = 100) String name,
		@NotNull @DecimalMin("0.00") BigDecimal commissionRate,
		@NotNull @DecimalMin("0.00") BigDecimal taxBufferRate,
		@NotNull @DecimalMin("0.00") BigDecimal overheadRate,
		@NotNull @DecimalMin("0.00") BigDecimal safetyMarginRate,
		@Min(1) int roundingUnit
	) {
	}

	record PricingPolicyResponse(
		UUID id,
		String name,
		BigDecimal commissionRate,
		BigDecimal taxBufferRate,
		BigDecimal overheadRate,
		BigDecimal safetyMarginRate,
		int roundingUnit,
		BigDecimal totalMarkupRate
	) {
	}

	record ProductStatusRequest(
		@NotNull ProductStatus status,
		@NotBlank @Size(max = 500) String reason
	) {
	}

	record ProductOptionRequest(
		@NotBlank @Size(max = 200) String name,
		@Min(0) long additionalPrice,
		ProductOptionStatus status,
		@Size(max = 500) String reason,
		@Size(max = 100) String sourceOptionCode,
		Long sourceAdditionalPrice,
		@Min(0) Long sourceStockQuantity,
		@Min(0) Integer sortOrder
	) {
	}

	record ProductOptionStatusRequest(
		@NotNull ProductOptionStatus status,
		@NotBlank @Size(max = 500) String reason
	) {
	}

	record ProductImageItem(
		@NotNull ProductImageType type,
		@NotBlank @Size(max = 1000) String imageUrl,
		int sortOrder,
		@Size(max = 200) String altText
	) {
	}

	record ProductImagesRequest(
		@NotNull @Size(max = 11) List<@Valid ProductImageItem> images,
		@NotBlank @Size(max = 500) String reason
	) {
	}

	record ProductDetailBlockItem(
		@NotNull ProductDetailBlockType type,
		@Size(max = 1000) String imageUrl,
		String htmlContent,
		int sortOrder,
		@Size(max = 200) String altText
	) {
	}

	record ProductDetailBlocksRequest(
		@NotNull @Size(max = 50) List<@Valid ProductDetailBlockItem> detailBlocks,
		@NotBlank @Size(max = 500) String reason
	) {
	}

	record ProductNoticeRequest(
		@NotBlank String productInfoNotice,
		@NotBlank String shippingInfo,
		@NotBlank String asInfo,
		@NotBlank String returnExchangeInfo,
		@NotBlank @Size(max = 500) String reason
	) {
	}

	record ProductOptionResponse(
		UUID id,
		String name,
		long additionalPrice,
		ProductOptionStatus status,
		@JsonInclude(JsonInclude.Include.NON_NULL) String sourceOptionCode,
		@JsonInclude(JsonInclude.Include.NON_NULL) Long sourceAdditionalPrice,
		@JsonInclude(JsonInclude.Include.NON_NULL) Long sourceStockQuantity,
		@JsonInclude(JsonInclude.Include.NON_NULL) Integer sortOrder
	) {
	}

	record ProductImageResponse(
		UUID id,
		ProductImageType type,
		String imageUrl,
		int sortOrder,
		String altText
	) {
	}

	record ProductImageUploadResponse(
		String imageUrl,
		String objectKey,
		long size,
		String contentType
	) {
	}

	record ProductDetailBlockResponse(
		UUID id,
		ProductDetailBlockType type,
		String imageUrl,
		String htmlContent,
		int sortOrder,
		String altText
	) {
	}

	record ProductNoticeResponse(
		UUID id,
		int version,
		String productInfoNotice,
		String shippingInfo,
		String asInfo,
		String returnExchangeInfo
	) {
	}

	record AdminProductResponse(
		UUID id,
		UUID supplierId,
		String supplierName,
		String name,
		String summary,
		long sourcePrice,
		long basePrice,
		ProductCategory categoryCode,
		ProductStatus status,
		String thumbnailImageUrl,
		int detailVersion
	) {
	}

	record ProductSummaryResponse(
		UUID id,
		String name,
		String summary,
		long basePrice,
		ProductCategory categoryCode,
		ProductStatus status,
		String thumbnailImageUrl
	) {
	}

	record ProductDetailResponse(
		UUID id,
		String name,
		String summary,
		@JsonInclude(JsonInclude.Include.NON_NULL) Long sourcePrice,
		long basePrice,
		ProductCategory categoryCode,
		ProductStatus status,
		String thumbnailImageUrl,
		int detailVersion,
		Integer productNoticeVersion,
		List<ProductImageResponse> images,
		List<ProductOptionResponse> options,
		List<ProductDetailBlockResponse> detailBlocks,
		ProductNoticeResponse productNotice,
		List<PolicyLinkResponse> policyLinks
	) {
	}

	record PolicyLinkResponse(
		String label,
		String href,
		String policyType
	) {
	}

	record ProductChangeHistoryListResponse(
		List<ProductChangeHistoryResponse> changes
	) {
	}

	record ProductChangeHistoryResponse(
		UUID changeId,
		UUID productOptionId,
		UUID adminUserId,
		ProductChangeType changeType,
		String beforeValue,
		String afterValue,
		String reason,
		Instant createdAt
	) {
	}
}
