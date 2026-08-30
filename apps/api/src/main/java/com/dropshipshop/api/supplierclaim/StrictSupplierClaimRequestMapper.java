package com.dropshipshop.api.supplierclaim;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimInstructionCode;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimRequestedType;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTaskCloseReasonCode;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTaskStatus;
import com.dropshipshop.api.supplierclaim.domain.SupplierShortageReasonCode;
import com.dropshipshop.api.supplierclaim.domain.SupplierShortageReviewReasonCode;
import com.dropshipshop.api.supplierclaim.domain.SupplierShortageStatus;

import tools.jackson.databind.JsonNode;

@Component
class StrictSupplierClaimRequestMapper {

	SupplierClaimDtos.ShortageSubmitRequest shortageSubmit(JsonNode body) {
		requireExactKeys(body, Set.of("reasonCode"));
		return new SupplierClaimDtos.ShortageSubmitRequest(
			enumValue(body, "reasonCode", SupplierShortageReasonCode.class)
		);
	}

	SupplierClaimDtos.ShortageReviewRequest shortageReview(JsonNode body) {
		requireExactKeys(body, Set.of("expectedStatus", "reviewReasonCode"));
		return new SupplierClaimDtos.ShortageReviewRequest(
			enumValue(body, "expectedStatus", SupplierShortageStatus.class),
			enumValue(body, "reviewReasonCode", SupplierShortageReviewReasonCode.class)
		);
	}

	SupplierClaimDtos.TaskCreateRequest taskCreate(JsonNode body) {
		requireExactKeys(body, Set.of("requestedType", "instructionCode", "dueAt"));
		return new SupplierClaimDtos.TaskCreateRequest(
			enumValue(body, "requestedType", SupplierClaimRequestedType.class),
			enumValue(body, "instructionCode", SupplierClaimInstructionCode.class),
			instant(body, "dueAt")
		);
	}

	SupplierClaimDtos.TaskCloseRequest taskClose(JsonNode body) {
		requireExactKeys(body, Set.of("expectedStatus", "closeReasonCode"));
		SupplierClaimDtos.TaskCloseRequest request = new SupplierClaimDtos.TaskCloseRequest(
			enumValue(body, "expectedStatus", SupplierClaimTaskStatus.class),
			enumValue(body, "closeReasonCode", SupplierClaimTaskCloseReasonCode.class)
		);
		if (!request.closeReasonCode().isAdminReason()) {
			throw validation("Only an allowlisted admin close reason is accepted");
		}
		return request;
	}

	SupplierClaimDtos.FactCreateRequest factCreate(JsonNode body) {
		requireAllowedKeys(body, Set.of("type", "payload", "correctsFactId"));
		requirePresent(body, "type");
		requirePresent(body, "payload");
		JsonNode payload = body.get("payload");
		if (payload == null || !payload.isObject()) throw validation("payload must be an object");
		UUID correctsFactId = null;
		JsonNode correction = body.get("correctsFactId");
		if (correction != null && !correction.isNull()) {
			if (!correction.isTextual()) throw validation("correctsFactId must be a UUID");
			try {
				correctsFactId = UUID.fromString(correction.asText());
			} catch (IllegalArgumentException exception) {
				throw validation("correctsFactId must be a UUID");
			}
		}
		return new SupplierClaimDtos.FactCreateRequest(
			enumValue(body, "type", SupplierClaimRequestedType.class), payload, correctsFactId
		);
	}

	private Instant instant(JsonNode body, String field) {
		String value = requiredText(body, field);
		try {
			return Instant.parse(value);
		} catch (DateTimeParseException exception) {
			throw validation(field + " must be an ISO-8601 instant");
		}
	}

	private <E extends Enum<E>> E enumValue(JsonNode body, String field, Class<E> type) {
		String value = requiredText(body, field);
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException exception) {
			throw validation(field + " is not allowed");
		}
	}

	private String requiredText(JsonNode body, String field) {
		JsonNode value = body.get(field);
		if (value == null || !value.isTextual() || value.asText().isBlank()) {
			throw validation(field + " is required");
		}
		return value.asText();
	}

	private void requireExactKeys(JsonNode body, Set<String> keys) {
		requireAllowedKeys(body, keys);
		for (String key : keys) requirePresent(body, key);
	}

	private void requirePresent(JsonNode body, String key) {
		if (!body.has(key) || body.get(key).isNull()) throw validation(key + " is required");
	}

	private void requireAllowedKeys(JsonNode body, Set<String> keys) {
		if (body == null || !body.isObject()) throw validation("Request body must be an object");
		for (Map.Entry<String, JsonNode> entry : body.properties()) {
			if (!keys.contains(entry.getKey())) throw validation("Request contains an unknown field");
		}
	}

	private ApiErrorException validation(String message) {
		return new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, message);
	}
}
