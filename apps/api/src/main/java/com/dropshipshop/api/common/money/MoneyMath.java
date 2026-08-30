package com.dropshipshop.api.common.money;

public final class MoneyMath {

	public static final long MAX_SUPPLIER_UNIT_COST = 100_000_000L;
	public static final long MAX_CUSTOMER_UNIT_PRICE = 1_000_000_000L;

	private static final String OVERFLOW_MESSAGE = "Monetary amount exceeds the supported range";

	private MoneyMath() {
	}

	public static long addNonNegative(long left, long right) {
		requireNonNegative(left, "left amount");
		requireNonNegative(right, "right amount");
		try {
			return Math.addExact(left, right);
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException(OVERFLOW_MESSAGE, exception);
		}
	}

	public static long addPositive(long accumulated, long amount) {
		requireNonNegative(accumulated, "accumulated amount");
		requirePositive(amount, "amount");
		return addNonNegative(accumulated, amount);
	}

	public static long multiplyPositive(long unitPrice, int quantity) {
		requirePositive(unitPrice, "unitPrice");
		return multiplyNonNegative(unitPrice, quantity);
	}

	public static long multiplyNonNegative(long unitPrice, int quantity) {
		requireNonNegative(unitPrice, "unitPrice");
		if (quantity <= 0) {
			throw new IllegalArgumentException("quantity must be positive");
		}
		try {
			return Math.multiplyExact(unitPrice, quantity);
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException(OVERFLOW_MESSAGE, exception);
		}
	}

	public static long subtractNonNegative(long minuend, long subtrahend) {
		requireNonNegative(minuend, "minuend");
		requireNonNegative(subtrahend, "subtrahend");
		try {
			long result = Math.subtractExact(minuend, subtrahend);
			return requireNonNegative(result, "result");
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException(OVERFLOW_MESSAGE, exception);
		}
	}

	public static long requirePositive(long amount, String name) {
		if (amount <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}
		return amount;
	}

	public static long requireNonNegative(long amount, String name) {
		if (amount < 0) {
			throw new IllegalArgumentException(name + " must be non-negative");
		}
		return amount;
	}

	public static long requireSupplierUnitCost(long amount, String name) {
		return requireAtMost(requireNonNegative(amount, name), MAX_SUPPLIER_UNIT_COST, name);
	}

	public static long requireCustomerUnitPrice(long amount, String name) {
		return requireAtMost(requireNonNegative(amount, name), MAX_CUSTOMER_UNIT_PRICE, name);
	}

	private static long requireAtMost(long amount, long maximum, String name) {
		if (amount > maximum) {
			throw new IllegalArgumentException(name + " exceeds the supported maximum");
		}
		return amount;
	}
}
