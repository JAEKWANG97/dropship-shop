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
}
