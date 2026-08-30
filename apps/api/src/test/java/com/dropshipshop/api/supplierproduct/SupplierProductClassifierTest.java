package com.dropshipshop.api.supplierproduct;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductComplianceStatus;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductReviewReasonCode;
import com.dropshipshop.api.catalog.domain.ProductReviewStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;

class SupplierProductClassifierTest {

	private final SupplierProductClassifier classifier = new SupplierProductClassifier();

	@Test
	void autoApprovesReadyAllowlistedProductAfterCertificationGuard() {
		Product product = product(ProductCategory.PPE_SAFETY_HELMET);
		product.updateComplianceStatus(ProductComplianceStatus.VERIFIED);

		SupplierProductClassifier.Classification result = classifier.classify(product, true, true, true, false);

		assertThat(result.status()).isEqualTo(ProductReviewStatus.AUTO_APPROVED);
		assertThat(result.reasonCode()).isNull();
	}

	@Test
	void requiresCertificationBeforeCategoryClassification() {
		Product product = product(ProductCategory.PPE_SAFETY_HELMET);

		SupplierProductClassifier.Classification result = classifier.classify(product, true, true, true, false);

		assertThat(result.reasonCode()).isEqualTo(ProductReviewReasonCode.CERTIFICATION_REVIEW);
	}

	@Test
	void routesManualAndIncompleteProductsToSafeReviewReasons() {
		Product reviewCategory = product(ProductCategory.SMART_CCTV_MOBILE);
		assertThat(classifier.classify(reviewCategory, true, true, true, false).reasonCode())
			.isEqualTo(ProductReviewReasonCode.CATEGORY_REVIEW);
		Product excludedSearchCategory = product(ProductCategory.DANGER_AREA_BARRIER);
		assertThat(classifier.classify(excludedSearchCategory, true, true, true, false).reasonCode())
			.isEqualTo(ProductReviewReasonCode.CATEGORY_REVIEW);
		assertThat(classifier.classify(reviewCategory, false, true, true, false).reasonCode())
			.isEqualTo(ProductReviewReasonCode.REQUIRED_INFO_MISSING);
	}

	@Test
	void neverAutoApprovesSupplementationResubmission() {
		Product product = product(ProductCategory.PPE_WORK_GLOVES);

		SupplierProductClassifier.Classification result = classifier.classify(product, true, true, true, true);

		assertThat(result.status()).isEqualTo(ProductReviewStatus.REVIEW_REQUIRED);
		assertThat(result.reasonCode()).isEqualTo(ProductReviewReasonCode.SAFETY_REVIEW);
	}

	@Test
	void requiresAPositiveCustomerPriceBeforeAutomaticApproval() {
		Product product = product(ProductCategory.PPE_WORK_GLOVES);
		product.updateSourcePricing(0, 0);

		SupplierProductClassifier.Classification result = classifier.classify(product, true, true, true, false);

		assertThat(result.status()).isEqualTo(ProductReviewStatus.REVIEW_REQUIRED);
		assertThat(result.reasonCode()).isEqualTo(ProductReviewReasonCode.REQUIRED_INFO_MISSING);
	}

	@Test
	void neverAutoApprovesAnExplicitlyRejectedComplianceDecision() {
		Product product = product(ProductCategory.PPE_WORK_GLOVES);
		product.updateComplianceStatus(ProductComplianceStatus.REJECTED);

		SupplierProductClassifier.Classification result = classifier.classify(product, true, true, true, false);

		assertThat(result.status()).isEqualTo(ProductReviewStatus.REVIEW_REQUIRED);
		assertThat(result.reasonCode()).isEqualTo(ProductReviewReasonCode.SAFETY_REVIEW);
	}

	private Product product(ProductCategory category) {
		return new Product(
			new Supplier("Supplier", null, null, null, null),
			"Product",
			"Summary",
			1000,
			1250,
			category,
			ProductStatus.HIDDEN,
			ProductManagementChannel.SUPPLIER_PORTAL
		);
	}
}
