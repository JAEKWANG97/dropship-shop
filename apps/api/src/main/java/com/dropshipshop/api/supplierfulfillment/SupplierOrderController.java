package com.dropshipshop.api.supplierfulfillment;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

@RestController
@RequestMapping("/api/supplier/orders")
@PreAuthorize("hasRole('SUPPLIER')")
class SupplierOrderController {

	private final SupplierOrderService supplierOrderService;
	private final CurrentUser currentUser;

	SupplierOrderController(SupplierOrderService supplierOrderService, CurrentUser currentUser) {
		this.supplierOrderService = supplierOrderService;
		this.currentUser = currentUser;
	}

	@GetMapping
	SupplierOrderDtos.OrderListResponse list(Authentication authentication) {
		return supplierOrderService.list(currentUser.id(authentication));
	}

	@GetMapping("/{orderNumber}")
	ResponseEntity<SupplierOrderDtos.OrderDetailResponse> detail(
		Authentication authentication,
		@PathVariable String orderNumber
	) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(supplierOrderService.detail(currentUser.id(authentication), orderNumber));
	}
}
