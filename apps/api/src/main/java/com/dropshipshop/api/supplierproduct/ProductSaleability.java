package com.dropshipshop.api.supplierproduct;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductReviewStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.domain.SupplierAvailability;
import com.dropshipshop.api.common.money.MoneyMath;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;

@Component
public class ProductSaleability {

	private final SupplierPortalFeatureGate supplierPortalFeatureGate;
	private final Clock clock;

	@Autowired
	public ProductSaleability(SupplierPortalFeatureGate supplierPortalFeatureGate) {
		this(supplierPortalFeatureGate, Clock.systemUTC());
	}

	ProductSaleability(SupplierPortalFeatureGate supplierPortalFeatureGate, Clock clock) {
		this.supplierPortalFeatureGate = supplierPortalFeatureGate;
		this.clock = clock;
	}

	public boolean isProductSellable(Product product) {
		if (product.getStatus() != ProductStatus.ACTIVE
			|| product.getSupplier().getStatus() != SupplierStatus.ACTIVE
			|| !product.getComplianceStatus().allowsSale()
			|| product.getBasePrice() <= 0
			|| product.getBasePrice() > MoneyMath.MAX_CUSTOMER_UNIT_PRICE) {
			return false;
		}
		if (product.getManagementChannel() == ProductManagementChannel.COREABLE) {
			return true;
		}
		ProductReviewStatus reviewStatus = product.getReviewStatus();
		return supplierPortalFeatureGate.isEnabled()
			&& (reviewStatus == ProductReviewStatus.AUTO_APPROVED || reviewStatus == ProductReviewStatus.APPROVED)
			&& product.getSupplier().hasTimeValidContract(Instant.now(clock));
	}

	public boolean isSellable(Product product, ProductOption option) {
		return isSellable(product, option, 1);
	}

	public boolean isSellable(Product product, ProductOption option, int quantity) {
		return isProductSellable(product)
			&& option.getStatus() == ProductOptionStatus.ACTIVE
			&& hasValidCustomerUnitPrice(product, option)
			&& option.getSupplierAvailability() == SupplierAvailability.AVAILABLE
			&& quantity > 0
			&& (!option.isTracked() || option.getAvailableQuantity() >= quantity);
	}

	public boolean hasValidCustomerUnitPrice(Product product, ProductOption option) {
		try {
			long unitPrice = MoneyMath.addNonNegative(product.getBasePrice(), option.getAdditionalPrice());
			return unitPrice > 0 && unitPrice <= MoneyMath.MAX_CUSTOMER_UNIT_PRICE;
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}
}
