package com.dropshipshop.api.supplierclaim;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.shipment.CarrierRegistry;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimRequestedType;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class SupplierClaimFactPayloadPolicy {

	private static final Set<String> SHIPMENT_STOP_RESULTS = Set.of(
		"STOPPED", "ALREADY_SHIPPED", "UNCONFIRMED"
	);
	private static final Set<String> RETURN_METHODS = Set.of(
		"COURIER_PICKUP", "CUSTOMER_PREPAID", "CUSTOMER_COD"
	);
	private static final Set<String> RETURN_RECEIVED_RESULTS = Set.of("RECEIVED", "NOT_RECEIVED");
	private static final Set<String> INSPECTION_RESULTS = Set.of(
		"DEFECT_CONFIRMED", "NO_DEFECT", "DAMAGED_IN_TRANSIT", "UNDETERMINED"
	);

	private final CarrierRegistry carrierRegistry;
	private final ObjectMapper objectMapper;

	SupplierClaimFactPayloadPolicy(CarrierRegistry carrierRegistry, ObjectMapper objectMapper) {
		this.carrierRegistry = carrierRegistry;
		this.objectMapper = objectMapper;
	}

	String normalize(
		SupplierClaimRequestedType type,
		JsonNode payload,
		Instant requestedAt,
		Instant now
	) {
		if (type == null || payload == null || !payload.isObject()) {
			throw validation("Fact payload must be an object");
		}
		Map<String, Object> canonical = switch (type) {
			case SHIPMENT_STOP_RESULT -> timedResult(
				payload, Set.of("resultCode", "checkedAt"), SHIPMENT_STOP_RESULTS,
				"checkedAt", requestedAt, now
			);
			case RETURN_INSTRUCTIONS -> returnInstructions(payload);
			case RETURN_RECEIVED -> timedResult(
				payload, Set.of("resultCode", "checkedAt"), RETURN_RECEIVED_RESULTS,
				"checkedAt", requestedAt, now
			);
			case INSPECTION_RESULT -> timedResult(
				payload, Set.of("resultCode", "inspectedAt"), INSPECTION_RESULTS,
				"inspectedAt", requestedAt, now
			);
		};
		try {
			return objectMapper.writeValueAsString(canonical);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize supplier fact payload");
		}
	}

	private Map<String, Object> timedResult(
		JsonNode payload,
		Set<String> keys,
		Set<String> resultCodes,
		String timeField,
		Instant requestedAt,
		Instant now
	) {
		requireExactKeys(payload, keys);
		String resultCode = requiredText(payload, "resultCode");
		if (!resultCodes.contains(resultCode)) throw validation("Fact resultCode is not allowed");
		Instant factTime = instant(payload, timeField);
		if (factTime.isBefore(requestedAt) || factTime.isAfter(now)) {
			throw validation(timeField + " must be between requestedAt and now");
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("resultCode", resultCode);
		result.put(timeField, factTime.toString());
		return result;
	}

	private Map<String, Object> returnInstructions(JsonNode payload) {
		requireAllowedKeys(payload, Set.of("methodCode", "carrierCode"));
		String methodCode = requiredText(payload, "methodCode");
		if (!RETURN_METHODS.contains(methodCode)) throw validation("Fact methodCode is not allowed");
		String carrierCode = null;
		JsonNode carrier = payload.get("carrierCode");
		if (carrier != null && !carrier.isNull()) {
			if (!carrier.isTextual()) throw validation("carrierCode must be a supported carrier code");
			carrierCode = carrierRegistry.find(carrier.asText())
				.map(CarrierRegistry.Carrier::carrierCode)
				.orElseThrow(() -> validation("carrierCode must be a supported carrier code"));
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("methodCode", methodCode);
		result.put("carrierCode", carrierCode);
		return result;
	}

	private Instant instant(JsonNode payload, String field) {
		String value = requiredText(payload, field);
		try {
			return Instant.parse(value);
		} catch (DateTimeParseException exception) {
			throw validation(field + " must be an ISO-8601 instant");
		}
	}

	private String requiredText(JsonNode payload, String field) {
		JsonNode value = payload.get(field);
		if (value == null || !value.isTextual() || value.asText().isBlank()) {
			throw validation(field + " is required");
		}
		return value.asText();
	}

	private void requireExactKeys(JsonNode payload, Set<String> allowed) {
		requireAllowedKeys(payload, allowed);
		for (String required : allowed) {
			if (!payload.has(required)) throw validation("Fact payload is missing " + required);
		}
	}

	private void requireAllowedKeys(JsonNode payload, Set<String> allowed) {
		for (Map.Entry<String, JsonNode> entry : payload.properties()) {
			if (!allowed.contains(entry.getKey())) {
				throw validation("Fact payload contains an unknown field");
			}
		}
	}

	private ApiErrorException validation(String message) {
		return new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, message);
	}
}
