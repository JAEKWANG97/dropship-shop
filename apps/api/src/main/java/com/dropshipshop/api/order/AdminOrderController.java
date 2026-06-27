package com.dropshipshop.api.order;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
class AdminOrderController {

	private final AdminOrderQueryService adminOrderQueryService;
	private final AdminOrderFulfillmentService adminOrderFulfillmentService;
	private final CurrentUser currentUser;

	AdminOrderController(
		AdminOrderQueryService adminOrderQueryService,
		AdminOrderFulfillmentService adminOrderFulfillmentService,
		CurrentUser currentUser
	) {
		this.adminOrderQueryService = adminOrderQueryService;
		this.adminOrderFulfillmentService = adminOrderFulfillmentService;
		this.currentUser = currentUser;
	}

	@GetMapping
	AdminOrderDtos.AdminOrderListResponse listSupplierOrderPendingOrders() {
		return adminOrderQueryService.listSupplierOrderPendingOrders();
	}

	@GetMapping("/{orderId}")
	AdminOrderDtos.AdminOrderDetailResponse getOrder(@PathVariable UUID orderId) {
		return adminOrderQueryService.getOrder(orderId);
	}

	@PostMapping("/{orderId}/supplier-work-start")
	@ResponseStatus(HttpStatus.OK)
	AdminOrderDtos.AdminOrderActionResponse startSupplierWork(
		@PathVariable UUID orderId,
		@Valid @RequestBody AdminOrderDtos.SupplierWorkStartRequest request,
		Authentication authentication
	) {
		return adminOrderFulfillmentService.startSupplierWork(orderId, currentUser.id(authentication), request);
	}

	@PostMapping("/{orderId}/supplier-order-completed")
	@ResponseStatus(HttpStatus.OK)
	AdminOrderDtos.AdminOrderActionResponse markSupplierOrderCompleted(
		@PathVariable UUID orderId,
		@Valid @RequestBody AdminOrderDtos.SupplierOrderCompletedRequest request,
		Authentication authentication
	) {
		return adminOrderFulfillmentService.markSupplierOrderCompleted(orderId, currentUser.id(authentication), request);
	}

	@PostMapping("/{orderId}/out-of-stock")
	@ResponseStatus(HttpStatus.OK)
	AdminOrderDtos.AdminOrderActionResponse markOutOfStock(
		@PathVariable UUID orderId,
		@Valid @RequestBody AdminOrderDtos.OutOfStockRequest request,
		Authentication authentication
	) {
		return adminOrderFulfillmentService.markOutOfStock(orderId, currentUser.id(authentication), request);
	}
}
