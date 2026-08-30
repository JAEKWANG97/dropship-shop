package com.dropshipshop.api.supplierclaim;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.supplierclaim.domain.SupplierClaimInstructionCode;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimRequestedType;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTaskCloseReasonCode;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTaskStatus;
import com.dropshipshop.api.supplierclaim.domain.SupplierShortageReasonCode;
import com.dropshipshop.api.supplierclaim.domain.SupplierShortageReviewReasonCode;
import com.dropshipshop.api.supplierclaim.domain.SupplierShortageStatus;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public final class SupplierClaimDtos {

	private SupplierClaimDtos() {
	}

	public record ShortageSubmitRequest(@NotNull SupplierShortageReasonCode reasonCode) {
	}

	public record ShortageReviewRequest(
		@NotNull SupplierShortageStatus expectedStatus,
		@NotNull SupplierShortageReviewReasonCode reviewReasonCode
	) {
	}

	public record SupplierShortageListResponse(List<SupplierShortageResponse> reports) {
	}

	public record SupplierShortageResponse(
		UUID reportId,
		String orderNumber,
		SupplierShortageReasonCode reasonCode,
		SupplierShortageStatus status,
		Instant reportedAt,
		Instant reviewedAt,
		SupplierShortageReviewReasonCode reviewReasonCode,
		String nextAction
	) {
	}

	public record AdminShortageListResponse(List<AdminShortageResponse> reports) {
	}

	public record AdminShortageResponse(
		UUID reportId,
		UUID orderId,
		String orderNumber,
		UUID supplierId,
		String supplierName,
		SupplierShortageReasonCode reasonCode,
		SupplierShortageStatus status,
		Instant reportedAt,
		Instant reviewedAt,
		SupplierShortageReviewReasonCode reviewReasonCode,
		UUID reviewedByAdminId,
		String nextAction
	) {
	}

	public record TaskCreateRequest(
		@NotNull SupplierClaimRequestedType requestedType,
		@NotNull SupplierClaimInstructionCode instructionCode,
		@NotNull Instant dueAt
	) {
	}

	public record TaskCloseRequest(
		@NotNull SupplierClaimTaskStatus expectedStatus,
		@NotNull SupplierClaimTaskCloseReasonCode closeReasonCode
	) {
	}

	public record FactCreateRequest(
		@NotNull SupplierClaimRequestedType type,
		@NotNull JsonNode payload,
		UUID correctsFactId
	) {
	}

	public record SupplierTaskListResponse(List<SupplierTaskSummaryResponse> tasks) {
	}

	public record AdminTaskListResponse(List<AdminTaskSummaryResponse> tasks) {
	}

	public record TaskItemResponse(String productName, String optionName, int quantity) {
	}

	public record FactResponse(
		UUID factId,
		SupplierClaimRequestedType type,
		JsonNode payload,
		UUID correctsFactId,
		Instant createdAt
	) {
	}

	public record SupplierTaskSummaryResponse(
		UUID taskId,
		String orderNumber,
		boolean orderDetailAvailable,
		List<TaskItemResponse> items,
		SupplierClaimRequestedType requestedType,
		SupplierClaimTaskStatus status,
		SupplierClaimInstructionCode instructionCode,
		String instructions,
		Instant dueAt,
		Instant requestedAt,
		Instant answeredAt,
		Instant closedAt,
		SupplierClaimTaskCloseReasonCode closeReasonCode
	) {
	}

	public record SupplierTaskResponse(
		UUID taskId,
		String orderNumber,
		boolean orderDetailAvailable,
		List<TaskItemResponse> items,
		SupplierClaimRequestedType requestedType,
		SupplierClaimTaskStatus status,
		SupplierClaimInstructionCode instructionCode,
		String instructions,
		Instant dueAt,
		Instant requestedAt,
		Instant answeredAt,
		Instant closedAt,
		SupplierClaimTaskCloseReasonCode closeReasonCode,
		List<FactResponse> facts
	) {
	}

	public record AdminTaskResponse(
		UUID taskId,
		UUID claimId,
		UUID orderId,
		String orderNumber,
		UUID supplierId,
		String supplierName,
		List<TaskItemResponse> items,
		SupplierClaimRequestedType requestedType,
		SupplierClaimTaskStatus status,
		SupplierClaimInstructionCode instructionCode,
		String instructions,
		Instant dueAt,
		Instant requestedAt,
		Instant answeredAt,
		Instant closedAt,
		SupplierClaimTaskCloseReasonCode closeReasonCode,
		UUID requestedByAdminId,
		UUID closedByAdminId,
		List<FactResponse> facts
	) {
	}

	public record AdminTaskSummaryResponse(
		UUID taskId,
		UUID claimId,
		UUID orderId,
		String orderNumber,
		UUID supplierId,
		String supplierName,
		List<TaskItemResponse> items,
		SupplierClaimRequestedType requestedType,
		SupplierClaimTaskStatus status,
		SupplierClaimInstructionCode instructionCode,
		String instructions,
		Instant dueAt,
		Instant requestedAt,
		Instant answeredAt,
		Instant closedAt,
		SupplierClaimTaskCloseReasonCode closeReasonCode,
		UUID requestedByAdminId,
		UUID closedByAdminId
	) {
	}
}
