package com.dropshipshop.api.policy;

import java.util.List;

final class PolicyDtos {

	private PolicyDtos() {
	}

	record PolicyIndexResponse(
		List<PolicyLinkResponse> policies
	) {
	}

	record PolicyLinkResponse(
		String policyType,
		String title,
		String href
	) {
	}

	record PolicyPageResponse(
		String policyType,
		String title,
		String version,
		String summary,
		List<PolicySectionResponse> sections,
		List<PolicyLinkResponse> relatedPolicies
	) {
	}

	record PolicySectionResponse(
		String heading,
		List<String> paragraphs
	) {
	}

	record BusinessProfileResponse(
		String companyName,
		String representativeName,
		String businessRegistrationNumber,
		String mailOrderSalesRegistrationNumber,
		String mailOrderSalesRegistrationAuthority,
		String businessAddress,
		String customerCenterPhone,
		String customerCenterEmail,
		String customerCenterHours,
		String privacyOfficerName,
		String privacyOfficerEmail,
		String privacyOfficerPhone,
		String hostingProvider,
		String effectiveFrom
	) {
	}

	record PrivacyProcessingItemListResponse(
		List<PrivacyProcessingItemResponse> items
	) {
	}

	record PrivacyProcessingItemResponse(
		String category,
		String collectedItems,
		String purpose,
		String retentionPeriod,
		String processorName,
		String processorPurpose,
		String thirdPartyRecipient,
		String thirdPartyPurpose
	) {
	}
}
