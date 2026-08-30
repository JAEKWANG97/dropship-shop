package com.dropshipshop.api.catalog.pricing;

import java.math.BigDecimal;
import java.util.UUID;

public record PricingCalculatorSnapshot(
	UUID pricingPolicyId,
	long pricingPolicyVersion,
	BigDecimal commissionRate,
	BigDecimal taxBufferRate,
	BigDecimal overheadRate,
	BigDecimal safetyMarginRate,
	int roundingUnit,
	long minimumResalePrice
) {
}
