package com.dropshipshop.api.catalog;

import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.catalog.domain.ProductDetailBlockType;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.SupplierStatus;

import jakarta.validation.Valid;
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
		@Min(0) long basePrice,
		@NotNull ProductStatus status
	) {
	}

	record ProductUpdateRequest(
		@NotNull UUID supplierId,
		@NotBlank @Size(max = 200) String name,
		@NotBlank @Size(max = 500) String summary,
		@Min(0) long basePrice,
		@NotBlank @Size(max = 500) String reason
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
		@Size(max = 500) String reason
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
		ProductOptionStatus status
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
		long basePrice,
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
		ProductStatus status,
		String thumbnailImageUrl
	) {
	}

	record ProductDetailResponse(
		UUID id,
		String name,
		String summary,
		long basePrice,
		ProductStatus status,
		String thumbnailImageUrl,
		int detailVersion,
		Integer productNoticeVersion,
		List<ProductImageResponse> images,
		List<ProductOptionResponse> options,
		List<ProductDetailBlockResponse> detailBlocks,
		ProductNoticeResponse productNotice
	) {
	}
}
