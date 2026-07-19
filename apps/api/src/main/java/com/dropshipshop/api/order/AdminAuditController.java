package com.dropshipshop.api.order;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminAuditController {

	private final AdminOrderQueryService adminOrderQueryService;

	AdminAuditController(AdminOrderQueryService adminOrderQueryService) {
		this.adminOrderQueryService = adminOrderQueryService;
	}

	@GetMapping("/actions")
	AdminOrderDtos.AdminActionHistoryListResponse listAdminActions(@RequestParam(required = false) UUID orderId) {
		return adminOrderQueryService.listAdminActions(orderId);
	}
}
