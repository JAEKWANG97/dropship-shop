package com.dropshipshop.api.supplierproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.InventoryMode;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductComplianceStatus;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductReviewStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.domain.SupplierAvailability;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;

class ProductSaleabilityTest {

	private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");
	private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private final SupplierPortalFeatureGate featureGate = mock(SupplierPortalFeatureGate.class);
	private final ProductSaleability saleability = new ProductSaleability(
		featureGate,
		Clock.fixed(NOW, ZoneOffset.UTC)
	);

	@Test
	void coreableIgnoresPortalReleaseAndContractButRequiresActiveSupplier() {
		when(featureGate.isEnabled()).thenReturn(false);
		Supplier supplier = supplierWithoutContract();
		Product product = product(supplier, ProductManagementChannel.COREABLE);

		assertThat(saleability.isProductSellable(product)).isTrue();

		supplier.updateLegacy("supplier", null, null, null, null, SupplierStatus.INACTIVE);
		assertThat(saleability.isProductSellable(product)).isFalse();
	}

	@Test
	void complianceRejectedCoreableProductIsNotSellable() {
		Supplier supplier = supplierWithoutContract();
		Product product = product(supplier, ProductManagementChannel.COREABLE);
		product.updateComplianceStatus(ProductComplianceStatus.REJECTED);

		assertThat(saleability.isProductSellable(product)).isFalse();
	}

	@Test
	void supplierPortalRequiresReleaseApprovedReviewValidContractAndActiveSupplier() {
		when(featureGate.isEnabled()).thenReturn(true);

		Supplier validSupplier = supplierWithContract(NOW.minusSeconds(60), NOW.plusSeconds(60));
		Product approved = approvedPortalProduct(validSupplier, ProductReviewStatus.APPROVED);
		Product autoApproved = approvedPortalProduct(validSupplier, ProductReviewStatus.AUTO_APPROVED);
		assertThat(saleability.isProductSellable(approved)).isTrue();
		assertThat(saleability.isProductSellable(autoApproved)).isTrue();

		Product draft = product(validSupplier, ProductManagementChannel.SUPPLIER_PORTAL);
		assertThat(saleability.isProductSellable(draft)).isFalse();

		Supplier unverified = supplierWithoutContract();
		assertThat(saleability.isProductSellable(
			approvedPortalProduct(unverified, ProductReviewStatus.APPROVED)
		)).isFalse();

		Supplier expired = supplierWithContract(NOW.minusSeconds(120), NOW);
		assertThat(saleability.isProductSellable(
			approvedPortalProduct(expired, ProductReviewStatus.APPROVED)
		)).isFalse();

		validSupplier.updateLegacy("supplier", null, null, null, null, SupplierStatus.INACTIVE);
		assertThat(saleability.isProductSellable(approved)).isFalse();

		when(featureGate.isEnabled()).thenReturn(false);
		Supplier anotherValidSupplier = supplierWithContract(NOW.minusSeconds(60), NOW.plusSeconds(60));
		assertThat(saleability.isProductSellable(
			approvedPortalProduct(anotherValidSupplier, ProductReviewStatus.APPROVED)
		)).isFalse();
	}

	@Test
	void sellableOptionMustBeActive() {
		when(featureGate.isEnabled()).thenReturn(true);
		Product product = approvedPortalProduct(
			supplierWithContract(NOW.minusSeconds(60), NOW.plusSeconds(60)),
			ProductReviewStatus.APPROVED
		);

		ProductOption active = new ProductOption(product, "active", 0, ProductOptionStatus.ACTIVE);
		active.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 2L);
		assertThat(saleability.isSellable(product, active)).isTrue();
		assertThat(saleability.isSellable(
			product,
			new ProductOption(product, "sold out", 0, ProductOptionStatus.SOLD_OUT)
		)).isFalse();
		assertThat(saleability.isSellable(
			product,
			new ProductOption(product, "stopped", 0, ProductOptionStatus.STOPPED)
		)).isFalse();
	}

	@Test
	void trackedSaleabilityUsesRequestedQuantityAndSupplierAvailability() {
		when(featureGate.isEnabled()).thenReturn(true);
		Product product = approvedPortalProduct(
			supplierWithContract(NOW.minusSeconds(60), NOW.plusSeconds(60)),
			ProductReviewStatus.APPROVED
		);
		ProductOption option = new ProductOption(product, "tracked", 0, ProductOptionStatus.ACTIVE);
		option.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 3L);

		assertThat(saleability.isSellable(product, option, 3)).isTrue();
		assertThat(saleability.isSellable(product, option, 4)).isFalse();

		option.updateInventory(SupplierAvailability.UNAVAILABLE, InventoryMode.TRACKED, 3L);
		assertThat(saleability.isSellable(product, option, 1)).isFalse();
	}

	private Product approvedPortalProduct(Supplier supplier, ProductReviewStatus reviewStatus) {
		Product product = product(supplier, ProductManagementChannel.SUPPLIER_PORTAL);
		product.updateReview(reviewStatus, null, null);
		return product;
	}

	private Product product(Supplier supplier, ProductManagementChannel channel) {
		return new Product(
			supplier,
			"product",
			"summary",
			1_000,
			1_300,
			ProductCategory.PPE_SAFETY_HELMET,
			ProductStatus.ACTIVE,
			channel
		);
	}

	private Supplier supplierWithoutContract() {
		return new Supplier("supplier", null, null, null, null);
	}

	private Supplier supplierWithContract(Instant effectiveAt, Instant expiresAt) {
		Supplier supplier = supplierWithoutContract();
		supplier.verifyPortalContract("v1", effectiveAt, expiresAt, NOW.minusSeconds(120), ADMIN_ID);
		return supplier;
	}
}
