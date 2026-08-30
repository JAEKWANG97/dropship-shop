package com.dropshipshop.api.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyMathTest {

	@Test
	void calculatesNonNegativeAndPositiveAmountsExactly() {
		assertThat(MoneyMath.addNonNegative(0, 10)).isEqualTo(10);
		assertThat(MoneyMath.multiplyNonNegative(0, 99)).isZero();
		assertThat(MoneyMath.multiplyPositive(10, 99)).isEqualTo(990);
		assertThat(MoneyMath.subtractNonNegative(10, 4)).isEqualTo(6);
	}

	@Test
	void rejectsOverflowUnderflowAndInvalidPositiveAmounts() {
		assertThatThrownBy(() -> MoneyMath.addNonNegative(Long.MAX_VALUE, 1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MoneyMath.multiplyPositive(Long.MAX_VALUE, 2))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MoneyMath.subtractNonNegative(1, 2))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MoneyMath.multiplyPositive(0, 1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void enforcesUnitCostAndCustomerPriceCaps() {
		assertThat(MoneyMath.requireSupplierUnitCost(MoneyMath.MAX_SUPPLIER_UNIT_COST, "cost"))
			.isEqualTo(MoneyMath.MAX_SUPPLIER_UNIT_COST);
		assertThat(MoneyMath.requireCustomerUnitPrice(MoneyMath.MAX_CUSTOMER_UNIT_PRICE, "price"))
			.isEqualTo(MoneyMath.MAX_CUSTOMER_UNIT_PRICE);
		assertThatThrownBy(() -> MoneyMath.requireSupplierUnitCost(
			MoneyMath.MAX_SUPPLIER_UNIT_COST + 1, "cost"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MoneyMath.requireCustomerUnitPrice(
			MoneyMath.MAX_CUSTOMER_UNIT_PRICE + 1, "price"))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
