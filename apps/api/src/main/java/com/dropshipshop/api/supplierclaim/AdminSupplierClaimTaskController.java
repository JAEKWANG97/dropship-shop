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
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminSupplierClaimTaskController {

	private final SupplierClaimTaskService taskService;
	private final StrictSupplierClaimRequestMapper requestMapper;
	private final CurrentUser currentUser;

	AdminSupplierClaimTaskController(
		SupplierClaimTaskService taskService,
		StrictSupplierClaimRequestMapper requestMapper,
		CurrentUser currentUser
	) {
		this.taskService = taskService;
		this.requestMapper = requestMapper;
		this.currentUser = currentUser;
	}

	@GetMapping("/supplier-claim-tasks")
	SupplierClaimDtos.AdminTaskListResponse list(
		@RequestParam(required = false) SupplierClaimTaskStatus status,
		@RequestParam(required = false) UUID claimId,
		@RequestParam(required = false) UUID orderId
	) {
		return taskService.listAdmin(status, claimId, orderId);
	}

	@GetMapping("/supplier-claim-tasks/{taskId}")
	SupplierClaimDtos.AdminTaskResponse detail(@PathVariable UUID taskId) {
		return taskService.detailAdmin(taskId);
	}

	@PostMapping("/claims/{claimId}/supplier-tasks")
	SupplierClaimDtos.AdminTaskResponse create(
		@PathVariable UUID claimId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody JsonNode body,
		Authentication authentication
	) {
		return taskService.create(currentUser.id(authentication), claimId, idempotencyKey,
			requestMapper.taskCreate(body));
	}

	@PostMapping("/supplier-claim-tasks/{taskId}/close")
	SupplierClaimDtos.AdminTaskResponse close(
		@PathVariable UUID taskId,
		@RequestHeader("Idempotency-Key") String idempotencyKey,
		@RequestBody JsonNode body,
		Authentication authentication
	) {
		return taskService.close(currentUser.id(authentication), taskId, idempotencyKey,
			requestMapper.taskClose(body));
	}
}
