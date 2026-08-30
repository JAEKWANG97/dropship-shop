package com.dropshipshop.api.catalog.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.dropshipshop.api.catalog.domain.PricingPolicy;
import com.dropshipshop.api.common.money.MoneyMath;

@Component
public class CatalogPriceCalculator {
	private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
	public ProductPriceCalculation calculate(
		long sourcePrice,
		List<Long> sourceAdditionalPrices,
		long minimumResalePrice,
		PricingPolicy policy
	) {
		requireNonNegative(sourcePrice, "sourcePrice");
		requireNonNegative(minimumResalePrice, "minimumResalePrice");
		Objects.requireNonNull(sourceAdditionalPrices, "sourceAdditionalPrices");
		Objects.requireNonNull(policy, "policy");

		long basePrice = calculatePrice(sourcePrice, minimumResalePrice, policy);
		List<OptionPriceCalculation> options = new ArrayList<>(sourceAdditionalPrices.size());
		for (int index = 0; index < sourceAdditionalPrices.size(); index++) {
			Long sourceAdditionalPrice = sourceAdditionalPrices.get(index);
			if (sourceAdditionalPrice == null) {
				throw new IllegalArgumentException("sourceAdditionalPrices must not contain null");
			}
			requireNonNegative(sourceAdditionalPrice, "sourceAdditionalPrice");
			long totalSourcePrice = MoneyMath.addNonNegative(sourcePrice, sourceAdditionalPrice);
			long customerTotalPrice = calculatePrice(totalSourcePrice, minimumResalePrice, policy);
			long additionalPrice = MoneyMath.subtractNonNegative(customerTotalPrice, basePrice);
			options.add(new OptionPriceCalculation(
				index,
				sourceAdditionalPrice,
				totalSourcePrice,
				customerTotalPrice,
				additionalPrice
			));
		}

		return new ProductPriceCalculation(
			sourcePrice,
			minimumResalePrice,
			basePrice,
			options,
			new PricingCalculatorSnapshot(
				policy.getId(),
				policy.getVersion(),
				policy.getCommissionRate(),
				policy.getTaxBufferRate(),
				policy.getOverheadRate(),
				policy.getSafetyMarginRate(),
				policy.getRoundingUnit(),
				minimumResalePrice
			)
		);
	}

	public long calculatePrice(long sourcePrice, long minimumResalePrice, PricingPolicy policy) {
		requireNonNegative(sourcePrice, "sourcePrice");
		requireNonNegative(minimumResalePrice, "minimumResalePrice");
		Objects.requireNonNull(policy, "policy");
		BigDecimal totalRate = policy.getCommissionRate()
			.add(policy.getTaxBufferRate())
			.add(policy.getOverheadRate())
			.add(policy.getSafetyMarginRate());
		BigDecimal markedUp = BigDecimal.valueOf(sourcePrice)
			.multiply(ONE_HUNDRED.add(totalRate))
			.divide(ONE_HUNDRED);
		try {
			long roundedPrice = roundToUnit(markedUp, policy.getRoundingUnit(), RoundingMode.HALF_UP);
			long roundedMinimum = roundToUnit(
				BigDecimal.valueOf(minimumResalePrice),
				policy.getRoundingUnit(),
				RoundingMode.CEILING
			);
			long result = Math.max(roundedPrice, roundedMinimum);
			return MoneyMath.requireCustomerUnitPrice(result, "calculated unit price");
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException("Calculated unit price exceeds the supported range", exception);
		}
	}

	private static long roundToUnit(BigDecimal price, int unit, RoundingMode roundingMode) {
		if (unit <= 0) {
			throw new IllegalArgumentException("roundingUnit must be positive");
		}
		return price.divide(BigDecimal.valueOf(unit), 0, roundingMode)
			.multiply(BigDecimal.valueOf(unit))
			.longValueExact();
	}

	private static void requireNonNegative(long value, String name) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " must be non-negative");
		}
	}
}
