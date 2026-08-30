package com.dropshipshop.api.catalog.pricing;

import java.util.List;

public record ProductPriceCalculation(
	long sourcePrice,
	long minimumResalePrice,
	long basePrice,
	List<OptionPriceCalculation> options,
	PricingCalculatorSnapshot snapshot
) {
	public ProductPriceCalculation {
		options = List.copyOf(options);
	}
}
