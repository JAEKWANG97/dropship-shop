package com.dropshipshop.api.catalog.domain;

public enum ProductComplianceStatus {
	PENDING,
	NOT_REQUIRED,
	VERIFIED,
	REJECTED;

	public boolean allowsSale() {
		return this == NOT_REQUIRED || this == VERIFIED;
	}
}
