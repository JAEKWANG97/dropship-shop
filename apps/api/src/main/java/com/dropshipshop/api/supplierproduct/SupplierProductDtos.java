package com.dropshipshop.api.supplierproduct;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.InventoryMode;
import com.dropshipshop.api.catalog.domain.ProductDetailBlockType;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.domain.ProductReviewReasonCode;
import com.dropshipshop.api.catalog.domain.SupplierAvailability;
import com.dropshipshop.api.common.money.MoneyMath;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;

final class SupplierProductDtos {
	private SupplierProductDtos() {
	}

	record ProductCreateRequest(
		@NotBlank @Size(max = 200) String name,
		@NotBlank @Size(max = 500) String summary,
		@Min(0) @Max(MoneyMath.MAX_SUPPLIER_UNIT_COST) long sourcePrice,
		@Min(1) @Max(99) int minimumOrderQuantity,
		@Min(1) @Max(99) int orderQuantityStep,
		@NotNull ProductCategory categoryCode,
		@Null Object supplierId,
		@Null Object basePrice,
		@Null Object status,
		@Null Object complianceStatus,
		@Null Object reviewStatus,
		@Null Object sourceItemNo,
		@Null Object managementChannel,
		@Null Object inventoryMode,
		@Null Object onHandQuantity,
		@Null Object reservedQuantity,
		@Null Object supplierAvailability
	) {
	}

	record ProductUpdateRequest(
		@NotNull @Min(0) Long expectedVersion,
		@NotBlank @Size(max = 200) String name,
		@NotBlank @Size(max = 500) String summary,
		@Min(0) @Max(MoneyMath.MAX_SUPPLIER_UNIT_COST) long sourcePrice,
		@Min(1) @Max(99) int minimumOrderQuantity,
		@Min(1) @Max(99) int orderQuantityStep,
		@NotNull ProductCategory categoryCode,
		@Null Object supplierId,
		@Null Object basePrice,
		@Null Object status,
		@Null Object complianceStatus,
		@Null Object reviewStatus,
		@Null Object sourceItemNo,
		@Null Object managementChannel,
		@Null Object inventoryMode,
		@Null Object onHandQuantity,
		@Null Object reservedQuantity,
		@Null Object supplierAvailability
	) {
	}

	record VersionRequest(@NotNull @Min(0) Long expectedVersion) {
	}

	record OptionRequest(
		@NotNull @Min(0) Long expectedVersion,
		@NotBlank @Size(max = 200) String name,
		@Size(max = 100) String sourceOptionCode,
		@Min(0) @Max(MoneyMath.MAX_SUPPLIER_UNIT_COST) long sourceAdditionalPrice,
		@Min(0) Integer sortOrder,
		@Null Object additionalPrice,
		@Null Object status,
		@Null Object sourceStockQuantity,
		@Null Object inventoryMode,
		@Null Object onHandQuantity,
		@Null Object reservedQuantity,
		@Null Object supplierAvailability
	) {
	}

	record InventoryUpdateRequest(
		@NotNull @Min(0) Long expectedInventoryVersion,
		@NotNull SupplierAvailability supplierAvailability,
		@NotNull InventoryMode inventoryMode,
		@Min(0) Long onHandQuantity,
		@Null Object reservedQuantity,
		@Null Object availableQuantity,
		@Null Object inventoryVersion
	) {
	}

	record ImageOrderRequest(
		@NotNull @Min(0) Long expectedVersion,
		@NotNull @Size(min = 1, max = 11) List<@Valid ImageOrderItem> images
	) {
	}

	record ImageOrderItem(
		@NotNull UUID imageId,
		@NotNull ProductImageType type,
		@Min(0) int sortOrder,
		@Size(max = 200) String altText
	) {
	}

	record DetailBlocksRequest(
		@NotNull @Min(0) Long expectedVersion,
		@NotNull @Size(max = 50) List<@Valid DetailBlockItem> detailBlocks
	) {
	}

	record DetailBlockItem(
		@NotNull ProductDetailBlockType type,
		UUID productImageId,
		String htmlContent,
		@Min(0) int sortOrder,
		@Size(max = 200) String altText,
		@Null Object imageUrl,
		@Null Object storageObjectKey
	) {
	}

	record NoticeRequest(
		@NotNull @Min(0) Long expectedVersion,
		@NotBlank String productInfoNotice,
		@NotBlank String shippingInfo,
		@NotBlank String asInfo,
		@NotBlank String returnExchangeInfo,
		@NotNull @Size(max = 100) List<@Valid NoticeRowItem> noticeRows
	) {
	}

	record NoticeRowItem(
		@NotBlank @Size(max = 500) String label,
		@NotBlank String value
	) {
	}

	record ProductListResponse(List<ProductResponse> products) {
	}

	record ProductResponse(
		UUID id,
		long version,
		String name,
		String summary,
		long sourcePrice,
		int minimumOrderQuantity,
		int orderQuantityStep,
		ProductCategory categoryCode,
		SupplierDisplayStatus supplierDisplayStatus,
		@JsonInclude(JsonInclude.Include.NON_NULL) ProductReviewReasonCode reviewReasonCode,
		@JsonInclude(JsonInclude.Include.NON_NULL) String reviewMessage,
		SupplierNextAction nextAction,
		boolean deletable,
		@JsonInclude(JsonInclude.Include.NON_NULL) Instant firstSubmittedAt,
		List<OptionResponse> options,
		List<ImageResponse> images,
		List<DetailBlockResponse> detailBlocks,
		@JsonInclude(JsonInclude.Include.NON_NULL) NoticeResponse productNotice
	) {
	}

	record OptionResponse(
		UUID id,
		String name,
		@JsonInclude(JsonInclude.Include.NON_NULL) String sourceOptionCode,
		long sourceAdditionalPrice,
		int sortOrder,
		boolean deletable,
		long inventoryVersion,
		SupplierAvailability supplierAvailability,
		InventoryMode inventoryMode,
		Long onHandQuantity,
		long reservedQuantity,
		Long availableQuantity
	) {
	}

	record InventoryResponse(
		UUID optionId,
		long inventoryVersion,
		SupplierAvailability supplierAvailability,
		InventoryMode inventoryMode,
		Long onHandQuantity,
		long reservedQuantity,
		Long availableQuantity
	) {
	}

	record InventoryConflictDetails(InventoryResponse currentInventory) {
	}

	record ImageResponse(
		UUID id,
		ProductImageType type,
		String imageUrl,
		int sortOrder,
		String altText,
		boolean deletable
	) {
	}

	record DetailBlockResponse(
		UUID id,
		ProductDetailBlockType type,
		@JsonInclude(JsonInclude.Include.NON_NULL) UUID productImageId,
		@JsonInclude(JsonInclude.Include.NON_NULL) String htmlContent,
		int sortOrder,
		String altText
	) {
	}

	record NoticeResponse(
		UUID id,
		int version,
		String productInfoNotice,
		String shippingInfo,
		String asInfo,
		String returnExchangeInfo,
		List<NoticeRowItem> noticeRows
	) {
	}

	enum SupplierDisplayStatus {
		EDITING,
		APPROVED,
		UNDER_REVIEW,
		CHANGES_REQUESTED,
		REJECTED,
		PAUSED_BY_COREABLE
	}

	enum SupplierNextAction {
		WAIT,
		EDIT_AND_RESUBMIT,
		CONTACT_COREABLE,
		NONE
	}
}
