package com.dropshipshop.api.policy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/policies")
class PolicyPageController {

	private final PolicyPageService policyPageService;

	PolicyPageController(PolicyPageService policyPageService) {
		this.policyPageService = policyPageService;
	}

	@GetMapping
	PolicyDtos.PolicyIndexResponse listPolicies() {
		return policyPageService.listPolicies();
	}

	@GetMapping("/{slug}")
	PolicyDtos.PolicyPageResponse getPolicy(@PathVariable String slug) {
		return policyPageService.getPolicy(slug);
	}
}
