package com.dropshipshop.api.supplierclaim;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.supplierclaim.domain.SupplierShortageStatus;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/admin/supplier-shortage-reports")
@PreAuthorize("hasRole('ADMIN')")
class AdminSupplierShortageController {

	private final SupplierShortageService shortageService;
	private final StrictSupplierClaimRequestMapper requestMapper;
	private final CurrentUser currentUser;

	AdminSupplierShortageController(
		SupplierShortageService shortageService,
		StrictSupplierClaimRequestMapper requestMapper,
		CurrentUser currentUser
	) {
		this.shortageService = shortageService;
		this.requestMapper = requestMapper;
		this.currentUser = currentUser;
	}

	@GetMapping
	SupplierClaimDtos.AdminShortageListResponse list(
		@RequestParam(required = false) SupplierShortageStatus status,
		@RequestParam(required = false) UUID orderId
	) {
		return shortageService.listAdmin(status, orderId);
	}

	@GetMapping("/{reportId}")
	SupplierClaimDtos.AdminShortageResponse detail(@PathVariable UUID reportId) {
		return shortageService.detailAdmin(reportId);
	}

	@PostMapping("/{reportId}/approve")
	SupplierClaimDtos.AdminShortageResponse approve(
		@PathVariable UUID reportId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody JsonNode body,
		Authentication authentication
	) {
		return shortageService.approve(currentUser.id(authentication), reportId, idempotencyKey,
			requestMapper.shortageReview(body));
	}

	@PostMapping("/{reportId}/reject")
	SupplierClaimDtos.AdminShortageResponse reject(
		@PathVariable UUID reportId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody JsonNode body,
		Authentication authentication
	) {
		return shortageService.reject(currentUser.id(authentication), reportId, idempotencyKey,
			requestMapper.shortageReview(body));
	}
}
