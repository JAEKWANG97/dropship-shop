package com.dropshipshop.api.order;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
class AdminOrderController {

	private final AdminOrderQueryService adminOrderQueryService;

	AdminOrderController(AdminOrderQueryService adminOrderQueryService) {
		this.adminOrderQueryService = adminOrderQueryService;
	}

	@GetMapping
	AdminOrderDtos.AdminOrderListResponse listSupplierOrderPendingOrders() {
		return adminOrderQueryService.listSupplierOrderPendingOrders();
	}

	@GetMapping("/{orderId}")
	AdminOrderDtos.AdminOrderDetailResponse getOrder(@PathVariable UUID orderId) {
		return adminOrderQueryService.getOrder(orderId);
	}
}
