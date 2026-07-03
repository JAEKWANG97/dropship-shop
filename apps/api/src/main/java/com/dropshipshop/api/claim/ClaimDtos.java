package com.dropshipshop.api.claim;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.claim.domain.ClaimReason;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.domain.ClaimType;
import com.dropshipshop.api.claim.domain.RequestedAction;
import com.dropshipshop.api.order.domain.OrderStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class ClaimDtos {

	private ClaimDtos() {
	}

	record CustomerCancelRequest(
		@NotBlank
		@Size(max = 1000)
		String reason
	) {
	}

	record CustomerClaimRequest(
		@NotNull
		ClaimType claimType,

		@NotNull
		ClaimReason claimReason,

		@NotBlank
		@Size(max = 1000)
		String customerMemo
	) {
	}

	record AdminClaimReviewRequest(
		@NotBlank
		@Size(max = 1000)
		String reason
	) {
	}

	record AdminReturnReceivedRequest(
		@NotBlank
		@Size(max = 1000)
		String memo
	) {
	}

	record ClaimResponse(
		UUID claimId,
		UUID orderId,
		String orderNumber,
		OrderStatus orderStatus,
		ClaimType claimType,
		ClaimReason claimReason,
		ClaimStatus status,
		String customerStatus,
		String customerStatusLabel,
		RequestedAction requestedAction,
		String customerMemo,
		UUID reviewedByAdminId,
		String adminReviewReason,
		Instant reviewedAt,
		UUID returnReceivedByAdminId,
		Instant returnReceivedAt,
		String returnReceivedMemo,
		UUID refundId,
		Instant completedAt,
		Instant createdAt,
		List<ClaimEvidenceResponse> evidenceFiles
	) {
	}

	record ClaimEvidenceResponse(
		UUID evidenceId,
		String fileUrl,
		String originalFilename,
		String contentType,
		long sizeBytes,
		Instant uploadedAt
	) {
	}

	record CustomerClaimListResponse(
		List<ClaimResponse> claims
	) {
	}

	record AdminClaimListResponse(
		List<ClaimResponse> claims
	) {
	}
}
