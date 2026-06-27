package com.dropshipshop.api.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
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
	List<CatalogDtos.AdminProductResponse> listProducts() {
		return catalogService.listAdminProducts();
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
