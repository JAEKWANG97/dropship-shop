package com.dropshipshop.api.policy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.policy.domain.PolicyDocumentStatus;
import com.dropshipshop.api.policy.domain.PolicyDocumentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class PolicyDocumentDtos {

	private PolicyDocumentDtos() {
	}

	record PolicyDocumentRequest(
		@NotNull
		PolicyDocumentType type,

		@NotBlank
		@Size(max = 50)
		String version,

		@NotBlank
		@Size(max = 200)
		String title,

		@NotBlank
		String content,

		@NotNull
		Instant effectiveFrom
	) {
	}

	record PolicyDocumentResponse(
		UUID policyId,
		PolicyDocumentType type,
		String version,
		String title,
		String content,
		Instant effectiveFrom,
		PolicyDocumentStatus status,
		Instant createdAt
	) {
	}

	record PolicyDocumentListResponse(List<PolicyDocumentResponse> policies) {
	}
}
