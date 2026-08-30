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
