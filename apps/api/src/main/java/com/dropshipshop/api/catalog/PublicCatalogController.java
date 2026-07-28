package com.dropshipshop.api.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.catalog.domain.ProductCategory;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/products")
class PublicCatalogController {

	private final CatalogService catalogService;

	PublicCatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping
	CatalogDtos.PublicProductPageResponse listProducts(
		@RequestParam(required = false) String q,
		@RequestParam(required = false) ProductCategory category,
		@RequestParam(required = false) List<ProductCategory> categories,
		@RequestParam(defaultValue = "0") @Min(0) long minPrice,
		@RequestParam(required = false) @Min(0) Long maxPrice,
		@RequestParam(defaultValue = "latest") String sort,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "24") @Min(1) @Max(100) int size
	) {
		return catalogService.listPublicProducts(q, category, categories, minPrice, maxPrice, sort, page, size);
	}

	@GetMapping("/{productId}")
	CatalogDtos.ProductDetailResponse getProduct(@PathVariable UUID productId) {
		return catalogService.getPublicProduct(productId);
	}
}
