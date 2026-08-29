package com.dropshipshop.api.supplierportal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.catalog.domain.Supplier;

@RestController
@RequestMapping("/api/supplier")
@PreAuthorize("hasRole('SUPPLIER')")
class SupplierMeController {

	private final SupplierTenantResolver tenantResolver;
	private final SupplierPortalFeatureGate featureGate;
	private final CurrentUser currentUser;

	SupplierMeController(
		SupplierTenantResolver tenantResolver,
		SupplierPortalFeatureGate featureGate,
		CurrentUser currentUser
	) {
		this.tenantResolver = tenantResolver;
		this.featureGate = featureGate;
		this.currentUser = currentUser;
	}

	@GetMapping("/me")
	SupplierPortalDtos.SupplierMeResponse me(Authentication authentication) {
		featureGate.requirePublicReleased();
		Supplier supplier = tenantResolver.current(authentication);
		return new SupplierPortalDtos.SupplierMeResponse(
			currentUser.id(authentication),
			supplier.getId(),
			supplier.getName(),
			supplier.getPortalStatus(),
			supplier.getStatus(),
			supplier.getPortalContractStatus(),
			supplier.getPortalContractVersion(),
			supplier.getPortalContractEffectiveAt(),
			supplier.getPortalContractExpiresAt()
		);
	}
}
