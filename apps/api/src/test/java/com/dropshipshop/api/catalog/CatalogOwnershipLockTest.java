package com.dropshipshop.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.dropshipshop.api.catalog.cleanup.ProductImageCleanupService;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductComplianceStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.pricing.CatalogPriceCalculator;
import com.dropshipshop.api.catalog.repository.PricingPolicyRepository;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;
import com.dropshipshop.api.catalog.repository.ProductDetailBlockRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.StorefrontSalesProperties;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.common.storage.FileStorage;
import com.dropshipshop.api.common.storage.ImageFileValidator;
import com.dropshipshop.api.policy.CustomerPolicyLinkService;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;
import com.dropshipshop.api.supplierportal.SupplierPortalInputPolicy;
import com.dropshipshop.api.supplierproduct.ProductSaleability;
import com.dropshipshop.api.notification.NotificationService;

class CatalogOwnershipLockTest {

	@Test
	void rejectsAdminMutationWhenSupplierChangedDuringLockAcquisition() {
		UUID productId = UUID.randomUUID();
		UUID discoveredSupplierId = UUID.randomUUID();
		UUID requestedSupplierId = UUID.randomUUID();
		SupplierRepository supplierRepository = mock(SupplierRepository.class);
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductOptionRepository optionRepository = mock(ProductOptionRepository.class);
		Supplier discoveredSupplier = mock(Supplier.class);
		Supplier requestedSupplier = mock(Supplier.class);
		Product lockedProduct = mock(Product.class);
		when(productRepository.findSupplierIdById(productId)).thenReturn(Optional.of(discoveredSupplierId));
		when(supplierRepository.findByIdForUpdate(discoveredSupplierId)).thenReturn(Optional.of(discoveredSupplier));
		when(supplierRepository.findByIdForUpdate(requestedSupplierId)).thenReturn(Optional.of(requestedSupplier));
		when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(lockedProduct));
		when(lockedProduct.getSupplier()).thenReturn(requestedSupplier);
		when(requestedSupplier.getId()).thenReturn(requestedSupplierId);
		CatalogService service = new CatalogService(
			supplierRepository,
			productRepository,
			mock(PricingPolicyRepository.class),
			optionRepository,
			mock(ProductImageRepository.class),
			mock(ProductDetailBlockRepository.class),
			mock(ProductNoticeRepository.class),
			mock(ProductChangeHistoryRepository.class),
			mock(CustomerPolicyLinkService.class),
			mock(FileStorage.class),
			mock(ImageFileValidator.class),
			mock(StorefrontSalesProperties.class),
			mock(SupplierPortalFeatureGate.class),
			mock(SupplierPortalInputPolicy.class),
			mock(ProductImageCleanupService.class),
			mock(CatalogPriceCalculator.class),
			mock(ProductSaleability.class),
			mock(NotificationService.class)
		);
		CatalogDtos.ProductUpdateRequest request = new CatalogDtos.ProductUpdateRequest(
			requestedSupplierId,
			"Product",
			"Summary",
			1_000L,
			null,
			null,
			1_300L,
			1,
			1,
			ProductCategory.PPE_WORK_GLOVES,
			ProductComplianceStatus.NOT_REQUIRED,
			"Supplier transfer",
			null
		);

		assertThatThrownBy(() -> service.updateProduct(productId, request, UUID.randomUUID()))
			.isInstanceOfSatisfying(ApiErrorException.class, exception -> {
				assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(exception.getCode()).isEqualTo(ApiErrorCode.PRODUCT_VERSION_CONFLICT);
			});

		verify(optionRepository, never()).findAllByProductIdForUpdate(productId);
	}
}
