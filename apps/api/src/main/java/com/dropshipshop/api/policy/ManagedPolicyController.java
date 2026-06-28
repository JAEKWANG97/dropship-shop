package com.dropshipshop.api.policy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.policy.domain.PolicyDocumentType;

@RestController
class ManagedPolicyController {

	private final PolicyDocumentService policyDocumentService;

	ManagedPolicyController(PolicyDocumentService policyDocumentService) {
		this.policyDocumentService = policyDocumentService;
	}

	@GetMapping("/api/policies/{type}/current")
	PolicyDocumentDtos.PolicyDocumentResponse getCurrent(@PathVariable PolicyDocumentType type) {
		return policyDocumentService.getCurrent(type);
	}

	@GetMapping("/api/policies/{type}/versions/{version}")
	PolicyDocumentDtos.PolicyDocumentResponse getVersion(@PathVariable PolicyDocumentType type, @PathVariable String version) {
		return policyDocumentService.getVersion(type, version);
	}
}
