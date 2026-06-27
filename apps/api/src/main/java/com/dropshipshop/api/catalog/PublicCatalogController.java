package com.dropshipshop.api.catalog;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
class PublicCatalogController {

	private final CatalogService catalogService;

	PublicCatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping
	List<CatalogDtos.ProductSummaryResponse> listProducts() {
		return catalogService.listPublicProducts();
	}

	@GetMapping("/{productId}")
	CatalogDtos.ProductDetailResponse getProduct(@PathVariable UUID productId) {
		return catalogService.getPublicProduct(productId);
	}
}
