package com.dropshipshop.api.catalog.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dropshipshop.api.catalog.domain.PricingPolicy;
import com.dropshipshop.api.common.money.MoneyMath;

class CatalogPriceCalculatorTest {
	private final CatalogPriceCalculator calculator = new CatalogPriceCalculator();

	@Test
	void calculatesProductAndOptionFromTheSameTotalCostFormula() {
		PricingPolicy policy = policy();

		ProductPriceCalculation result = calculator.calculate(1_000, List.of(0L, 200L), 600, policy);

		assertThat(result.basePrice()).isEqualTo(1_300);
		assertThat(result.options()).containsExactly(
			new OptionPriceCalculation(0, 0, 1_000, 1_300, 0),
			new OptionPriceCalculation(1, 200, 1_200, 1_500, 200)
		);
		assertThat(result.snapshot().pricingPolicyVersion()).isEqualTo(1);
		assertThat(result.snapshot().minimumResalePrice()).isEqualTo(600);
	}

	@Test
	void roundsResaleMinimumUpBeforeApplyingTheFloor() {
		long result = calculator.calculatePrice(100, 651, policy());

		assertThat(result).isEqualTo(700);
	}

	@Test
	void rejectsNegativeSourceInputs() {
		assertThatThrownBy(() -> calculator.calculate(-1, List.of(), 0, policy()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> calculator.calculate(1, List.of(-1L), 0, policy()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsCustomerPriceAboveTheSupportedUnitCap() {
		assertThatThrownBy(() -> calculator.calculatePrice(
			100,
			MoneyMath.MAX_CUSTOMER_UNIT_PRICE + 1,
			policy()
		)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("maximum");
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
