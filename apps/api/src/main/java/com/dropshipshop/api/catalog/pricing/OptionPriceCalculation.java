package com.dropshipshop.api.catalog.pricing;

public record OptionPriceCalculation(
	int index,
	long sourceAdditionalPrice,
	long totalSourcePrice,
	long customerTotalPrice,
	long additionalPrice
) {
}
