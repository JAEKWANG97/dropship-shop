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
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTaskStatus;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/supplier/claim-tasks")
@PreAuthorize("hasRole('SUPPLIER')")
class SupplierClaimTaskController {

	private final SupplierClaimTaskService taskService;
	private final StrictSupplierClaimRequestMapper requestMapper;
	private final CurrentUser currentUser;

	SupplierClaimTaskController(
		SupplierClaimTaskService taskService,
		StrictSupplierClaimRequestMapper requestMapper,
		CurrentUser currentUser
	) {
		this.taskService = taskService;
		this.requestMapper = requestMapper;
		this.currentUser = currentUser;
	}

	@GetMapping
	SupplierClaimDtos.SupplierTaskListResponse list(
		@RequestParam(required = false) SupplierClaimTaskStatus status,
		Authentication authentication
	) {
		return taskService.listSupplier(currentUser.id(authentication), status);
	}

	@GetMapping("/{taskId}")
	SupplierClaimDtos.SupplierTaskResponse detail(
		@PathVariable UUID taskId,
		Authentication authentication
	) {
		return taskService.detailSupplier(currentUser.id(authentication), taskId);
	}

	@PostMapping("/{taskId}/facts")
	SupplierClaimDtos.SupplierTaskResponse addFact(
		@PathVariable UUID taskId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody JsonNode body,
		Authentication authentication
	) {
		return taskService.addFact(currentUser.id(authentication), taskId, idempotencyKey,
			requestMapper.factCreate(body));
	}
}
