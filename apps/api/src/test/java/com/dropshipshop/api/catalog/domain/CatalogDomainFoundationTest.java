package com.dropshipshop.api.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class CatalogDomainFoundationTest {

	@Test
	void portalProductStartsAsVersionedDraftAndKeepsFirstSubmission() {
		Product product = product(ProductManagementChannel.SUPPLIER_PORTAL);
		Instant first = Instant.parse("2026-08-30T00:00:00Z");

		product.incrementVersion();
		product.markFirstSubmitted(first);
		product.markFirstSubmitted(first.plusSeconds(60));

		assertThat(product.getManagementChannel()).isEqualTo(ProductManagementChannel.SUPPLIER_PORTAL);
		assertThat(product.getReviewStatus()).isEqualTo(ProductReviewStatus.DRAFT);
		assertThat(product.getVersion()).isEqualTo(1);
		assertThat(product.hasVersion(1)).isTrue();
		assertThat(product.getFirstSubmittedAt()).isEqualTo(first);
	}

	@Test
	void reviewReasonAndSupplierMessageFollowTheDurableContract() {
		Product product = product(ProductManagementChannel.SUPPLIER_PORTAL);

		assertThatThrownBy(() -> product.updateReview(ProductReviewStatus.REVIEW_REQUIRED, null, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> product.updateReview(
			ProductReviewStatus.SUPPLEMENT_REQUESTED,
			ProductReviewReasonCode.SUPPLEMENT_REQUIRED,
			"line one\nline two"
		)).isInstanceOf(IllegalArgumentException.class);

		product.updateReview(
			ProductReviewStatus.SUPPLEMENT_REQUESTED,
			ProductReviewReasonCode.SUPPLEMENT_REQUIRED,
			"필수 정보를 보완해 주세요."
		);
		assertThat(product.getReviewReasonCode()).isEqualTo(ProductReviewReasonCode.SUPPLEMENT_REQUIRED);
	}

	@Test
	void supplierCostsCannotBecomeNegative() {
		Product product = product(ProductManagementChannel.COREABLE);

		assertThatThrownBy(() -> product.updateSourcePricing(-1, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ProductOption(
			product,
			"option",
			0,
			ProductOptionStatus.ACTIVE,
			"code",
			-1L,
			null,
			0
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void optionPriceCannotPushTheCustomerUnitPriceAboveTheCap() {
		Product product = new Product(
			new Supplier("supplier", null, null, null, null),
			"product",
			"summary",
			1_000,
			900_000_000,
			ProductCategory.PPE_SAFETY_HELMET,
			ProductStatus.HIDDEN
		);

		assertThatThrownBy(() -> new ProductOption(
			product,
			"option",
			100_000_001,
			ProductOptionStatus.ACTIVE
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("unitPrice");
	}

	@Test
	void pricingPolicyVersionOnlyMovesForwardOnUpdates() {
		PricingPolicy policy = policy();

		assertThat(policy.getVersion()).isEqualTo(1);
		policy.update(
			"updated",
			new BigDecimal("5.00"),
			new BigDecimal("10.00"),
			new BigDecimal("5.00"),
			new BigDecimal("5.00"),
			100
		);
		assertThat(policy.getVersion()).isEqualTo(2);
	}

	@Test
	void optionInventoryDefaultsFollowManagementChannel() {
		ProductOption coreable = new ProductOption(
			product(ProductManagementChannel.COREABLE), "coreable", 0, ProductOptionStatus.ACTIVE
		);
		ProductOption portal = new ProductOption(
			product(ProductManagementChannel.SUPPLIER_PORTAL), "portal", 0, ProductOptionStatus.ACTIVE
		);

		assertThat(coreable.getInventoryMode()).isEqualTo(InventoryMode.UNTRACKED);
		assertThat(coreable.getOnHandQuantity()).isNull();
		assertThat(coreable.getReservedQuantity()).isZero();
		assertThat(portal.getInventoryMode()).isEqualTo(InventoryMode.TRACKED);
		assertThat(portal.getOnHandQuantity()).isZero();
		assertThat(portal.getReservedQuantity()).isZero();
		assertThat(portal.getSupplierAvailability()).isEqualTo(SupplierAvailability.AVAILABLE);
	}

	@Test
	void trackedInventoryPreservesLedgerInvariantsAndVersionsEveryMutation() {
		ProductOption option = new ProductOption(
			product(ProductManagementChannel.SUPPLIER_PORTAL), "portal", 0, ProductOptionStatus.ACTIVE
		);

		option.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 10L);
		option.reserve(4);
		assertThat(option.getAvailableQuantity()).isEqualTo(6);
		assertThat(option.getInventoryVersion()).isEqualTo(2);
		assertThatThrownBy(() -> option.updateInventory(
			SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 3L
		)).isInstanceOf(IllegalStateException.class);

		option.consumeReservation(2);
		option.releaseReservation(2);
		option.reacquireAndConsume(1);

		assertThat(option.getOnHandQuantity()).isEqualTo(7);
		assertThat(option.getReservedQuantity()).isZero();
		assertThat(option.getAvailableQuantity()).isEqualTo(7);
		assertThat(option.getInventoryVersion()).isEqualTo(5);
	}

	@Test
	void untrackedInventoryRejectsQuantitiesAndActiveReservationsBlockModeChange() {
		ProductOption option = new ProductOption(
			product(ProductManagementChannel.SUPPLIER_PORTAL), "portal", 0, ProductOptionStatus.ACTIVE
		);
		assertThatThrownBy(() -> option.updateInventory(
			SupplierAvailability.AVAILABLE, InventoryMode.UNTRACKED, 1L
		)).isInstanceOf(IllegalArgumentException.class);

		option.updateInventory(SupplierAvailability.AVAILABLE, InventoryMode.TRACKED, 2L);
		option.reserve(1);
		assertThatThrownBy(() -> option.updateInventory(
			SupplierAvailability.AVAILABLE, InventoryMode.UNTRACKED, null
		)).isInstanceOf(IllegalStateException.class);
	}

	private Product product(ProductManagementChannel channel) {
		return new Product(
			new Supplier("supplier", null, null, null, null),
			"product",
			"summary",
			1_000,
			1_300,
			ProductCategory.PPE_SAFETY_HELMET,
			ProductStatus.HIDDEN,
			channel
		);
	}

	private PricingPolicy policy() {
		return new PricingPolicy(
			"default",
			new BigDecimal("5.00"),
			new BigDecimal("10.00"),
			new BigDecimal("5.00"),
			new BigDecimal("5.00"),
			100
		);
	}
}
