package com.dropshipshop.api.supplierportal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;

@Component
public class SupplierPortalFeatureGate {

	private final SupplierPortalProperties properties;

	SupplierPortalFeatureGate(SupplierPortalProperties properties) {
		this.properties = properties;
	}

	public boolean isEnabled() {
		return properties.enabled();
	}

	public void requirePublicReleased() {
		if (!isEnabled()) {
			throw new ApiErrorException(
				HttpStatus.NOT_FOUND,
				ApiErrorCode.SUPPLIER_PORTAL_NOT_RELEASED,
				"Resource not found"
			);
		}
	}

	public void requireInvitationMutationReleased() {
		requireOperationalMutationReleased();
	}

	public void requireOperationalMutationReleased() {
		if (!isEnabled()) {
			throw new ApiErrorException(
				HttpStatus.CONFLICT,
				ApiErrorCode.SUPPLIER_PORTAL_NOT_RELEASED,
				"Supplier portal is not released"
			);
		}
	}
}
