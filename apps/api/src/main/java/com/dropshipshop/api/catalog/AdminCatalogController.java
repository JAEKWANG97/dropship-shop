package com.dropshipshop.api.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Validated
class AdminCatalogController {

	private final CatalogService catalogService;
	private final CurrentUser currentUser;

	AdminCatalogController(CatalogService catalogService, CurrentUser currentUser) {
		this.catalogService = catalogService;
		this.currentUser = currentUser;
	}

	@GetMapping("/suppliers")
	List<CatalogDtos.SupplierResponse> listSuppliers() {
		return catalogService.listSuppliers();
	}

	@PostMapping("/suppliers")
	@ResponseStatus(HttpStatus.CREATED)
	CatalogDtos.SupplierResponse createSupplier(@Valid @RequestBody CatalogDtos.SupplierRequest request) {
		return catalogService.createSupplier(request);
	}

	@GetMapping("/suppliers/{supplierId}")
	CatalogDtos.SupplierResponse getSupplier(@PathVariable UUID supplierId) {
		return catalogService.getSupplier(supplierId);
	}

	@PatchMapping("/suppliers/{supplierId}")
	CatalogDtos.SupplierResponse updateSupplier(
		@PathVariable UUID supplierId,
		@Valid @RequestBody CatalogDtos.SupplierRequest request
	) {
		return catalogService.updateSupplier(supplierId, request);
	}

	@GetMapping("/products")
	CatalogDtos.AdminProductPageResponse listProducts(
		@RequestParam(required = false) String q,
		@RequestParam(required = false) ProductStatus status,
		@RequestParam(required = false) ProductCategory category,
		@RequestParam(required = false) UUID supplierId,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
	) {
		return catalogService.listAdminProducts(q, status, category, supplierId, page, size);
	}

	@GetMapping("/pricing-policy")
	CatalogDtos.PricingPolicyResponse getPricingPolicy() {
		return catalogService.getPricingPolicy();
	}

	@PutMapping("/pricing-policy")
	CatalogDtos.PricingPolicyResponse updatePricingPolicy(
		@Valid @RequestBody CatalogDtos.PricingPolicyRequest request
	) {
		return catalogService.updatePricingPolicy(request);
	}

	@PostMapping("/products")
	@ResponseStatus(HttpStatus.CREATED)
	CatalogDtos.AdminProductResponse createProduct(@Valid @RequestBody CatalogDtos.ProductCreateRequest request) {
		return catalogService.createProduct(request);
	}

	@GetMapping("/products/{productId}")
	CatalogDtos.ProductDetailResponse getProduct(@PathVariable UUID productId) {
		return catalogService.getAdminProduct(productId);
	}

	@GetMapping("/products/{productId}/changes")
	CatalogDtos.ProductChangeHistoryListResponse listProductChanges(@PathVariable UUID productId) {
		return catalogService.listProductChanges(productId);
	}

	@PatchMapping("/products/{productId}")
	CatalogDtos.AdminProductResponse updateProduct(
		@PathVariable UUID productId,
		@Valid @RequestBody CatalogDtos.ProductUpdateRequest request,
		Authentication authentication
	) {
		return catalogService.updateProduct(productId, request, currentUser.id(authentication));
	}

	@PatchMapping("/products/{productId}/status")
	CatalogDtos.AdminProductResponse updateProductStatus(
		@PathVariable UUID productId,
		@Valid @RequestBody CatalogDtos.ProductStatusRequest request,
		Authentication authentication
	) {
		return catalogService.updateProductStatus(productId, request, currentUser.id(authentication));
	}

	@PostMapping("/products/{productId}/options")
	@ResponseStatus(HttpStatus.CREATED)
	CatalogDtos.ProductOptionResponse createOption(
		@PathVariable UUID productId,
		@Valid @RequestBody CatalogDtos.ProductOptionRequest request
	) {
		return catalogService.createOption(productId, request);
	}

	@PatchMapping("/products/{productId}/options/{optionId}")
	CatalogDtos.ProductOptionResponse updateOption(
		@PathVariable UUID productId,
		@PathVariable UUID optionId,
		@Valid @RequestBody CatalogDtos.ProductOptionRequest request,
		Authentication authentication
	) {
		return catalogService.updateOption(productId, optionId, request, currentUser.id(authentication));
	}

	@PatchMapping("/products/{productId}/options/{optionId}/status")
	CatalogDtos.ProductOptionResponse updateOptionStatus(
		@PathVariable UUID productId,
		@PathVariable UUID optionId,
		@Valid @RequestBody CatalogDtos.ProductOptionStatusRequest request,
		Authentication authentication
	) {
		return catalogService.updateOptionStatus(productId, optionId, request, currentUser.id(authentication));
	}

	@PutMapping("/products/{productId}/images")
	CatalogDtos.ProductDetailResponse replaceImages(
		@PathVariable UUID productId,
		@Valid @RequestBody CatalogDtos.ProductImagesRequest request,
		Authentication authentication
	) {
		return catalogService.replaceImages(productId, request, currentUser.id(authentication));
	}

	@PostMapping("/products/{productId}/images/upload")
	CatalogDtos.ProductImageUploadResponse uploadImage(
		@PathVariable UUID productId,
		@RequestPart("file") MultipartFile file
	) {
		return catalogService.uploadImage(productId, file);
	}

	@PutMapping("/products/{productId}/detail-blocks")
	CatalogDtos.ProductDetailResponse replaceDetailBlocks(
		@PathVariable UUID productId,
		@Valid @RequestBody CatalogDtos.ProductDetailBlocksRequest request,
		Authentication authentication
	) {
		return catalogService.replaceDetailBlocks(productId, request, currentUser.id(authentication));
	}

	@PutMapping("/products/{productId}/notice")
	CatalogDtos.ProductDetailResponse updateNotice(
		@PathVariable UUID productId,
		@Valid @RequestBody CatalogDtos.ProductNoticeRequest request,
		Authentication authentication
	) {
		return catalogService.updateNotice(productId, request, currentUser.id(authentication));
	}
}
