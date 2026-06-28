package com.dropshipshop.api.policy;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LegalDisclosureController {

	private final PolicyPageService policyPageService;

	LegalDisclosureController(PolicyPageService policyPageService) {
		this.policyPageService = policyPageService;
	}

	@GetMapping("/api/business-profile")
	PolicyDtos.BusinessProfileResponse getBusinessProfile() {
		return policyPageService.getBusinessProfile();
	}

	@GetMapping("/api/privacy-processing-items")
	PolicyDtos.PrivacyProcessingItemListResponse listPrivacyProcessingItems() {
		return policyPageService.listPrivacyProcessingItems();
	}
}
