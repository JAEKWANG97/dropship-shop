package com.dropshipshop.api.catalog.domain;

import java.util.Objects;
import java.util.UUID;

public record ProductChangeActor(
	ProductChangeActorType type,
	UUID userId,
	UUID supplierId,
	String systemCode
) {
	public ProductChangeActor {
		Objects.requireNonNull(type, "type");
		switch (type) {
			case ADMIN -> {
				Objects.requireNonNull(userId, "userId");
				requireNull(supplierId, "supplierId");
				requireNull(systemCode, "systemCode");
			}
			case SUPPLIER -> {
				Objects.requireNonNull(userId, "userId");
				Objects.requireNonNull(supplierId, "supplierId");
				requireNull(systemCode, "systemCode");
			}
			case SYSTEM -> {
				requireNull(userId, "userId");
				requireNull(supplierId, "supplierId");
				if (systemCode == null || systemCode.isBlank()) {
					throw new IllegalArgumentException("systemCode is required");
				}
			}
		}
	}

	public static ProductChangeActor admin(UUID userId) {
		return new ProductChangeActor(ProductChangeActorType.ADMIN, userId, null, null);
	}

	public static ProductChangeActor supplier(UUID userId, UUID supplierId) {
		return new ProductChangeActor(ProductChangeActorType.SUPPLIER, userId, supplierId, null);
	}

	public static ProductChangeActor system(String systemCode) {
		return new ProductChangeActor(ProductChangeActorType.SYSTEM, null, null, systemCode);
	}

	private static void requireNull(Object value, String name) {
		if (value != null) {
			throw new IllegalArgumentException(name + " must be null");
		}
	}
}
