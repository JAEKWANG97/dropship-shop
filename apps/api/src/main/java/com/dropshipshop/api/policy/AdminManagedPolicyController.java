package com.dropshipshop.api.policy;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/policies")
@PreAuthorize("hasRole('ADMIN')")
class AdminManagedPolicyController {

	private final PolicyDocumentService policyDocumentService;

	AdminManagedPolicyController(PolicyDocumentService policyDocumentService) {
		this.policyDocumentService = policyDocumentService;
	}

	@GetMapping
	PolicyDocumentDtos.PolicyDocumentListResponse listPolicies() {
		return policyDocumentService.listPolicies();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PolicyDocumentDtos.PolicyDocumentResponse createPolicy(@Valid @RequestBody PolicyDocumentDtos.PolicyDocumentRequest request) {
		return policyDocumentService.create(request);
	}

	@PatchMapping("/{policyId}")
	PolicyDocumentDtos.PolicyDocumentResponse updatePolicy(
		@PathVariable UUID policyId,
		@Valid @RequestBody PolicyDocumentDtos.PolicyDocumentRequest request
	) {
		return policyDocumentService.update(policyId, request);
	}

	@PostMapping("/{policyId}/activate")
	PolicyDocumentDtos.PolicyDocumentResponse activatePolicy(@PathVariable UUID policyId) {
		return policyDocumentService.activate(policyId);
	}
}
