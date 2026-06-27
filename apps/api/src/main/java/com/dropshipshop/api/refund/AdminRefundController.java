package com.dropshipshop.api.refund;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
class AdminRefundController {

	private final RefundService refundService;

	AdminRefundController(RefundService refundService) {
		this.refundService = refundService;
	}

	@GetMapping
	RefundDtos.AdminRefundListResponse listRefunds() {
		return refundService.listRefunds();
	}

	@PostMapping("/{refundId}/request-pg-cancel")
	RefundDtos.AdminRefundResponse requestPgCancel(@PathVariable UUID refundId) {
		return refundService.requestPgCancel(refundId);
	}

	@PostMapping("/{refundId}/retry")
	RefundDtos.AdminRefundResponse retryPgCancel(@PathVariable UUID refundId) {
		return refundService.retryPgCancel(refundId);
	}
}
