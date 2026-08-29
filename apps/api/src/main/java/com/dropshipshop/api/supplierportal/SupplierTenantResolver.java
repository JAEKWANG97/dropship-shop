package com.dropshipshop.api.supplierportal;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;

@Component
public class SupplierTenantResolver {

	private final CurrentUser currentUser;
	private final SupplierRepository supplierRepository;

	SupplierTenantResolver(CurrentUser currentUser, SupplierRepository supplierRepository) {
		this.currentUser = currentUser;
		this.supplierRepository = supplierRepository;
	}

	public Supplier current(Authentication authentication) {
		UUID userId = currentUser.id(authentication);
		return supplierRepository.findByManagerUserId(userId)
			.filter(supplier -> supplier.isPortalAuthorityActive(Instant.now()))
			.orElseThrow(this::notFound);
	}

	public Supplier requireOwned(Authentication authentication, UUID resourceSupplierId) {
		Supplier supplier = current(authentication);
		if (!supplier.getId().equals(resourceSupplierId)) {
			throw notFound();
		}
		return supplier;
	}

	private ApiErrorException notFound() {
		return new ApiErrorException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
	}
}
