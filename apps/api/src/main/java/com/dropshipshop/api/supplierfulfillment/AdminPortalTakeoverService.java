package com.dropshipshop.api.supplierfulfillment;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentHandoverReasonCode;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.SupplierPortalInputPolicy;
import com.dropshipshop.api.supplierportal.domain.FulfillmentHandoverHistory;
import com.dropshipshop.api.supplierportal.repository.FulfillmentHandoverHistoryRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
class AdminPortalTakeoverService {

	private final FulfillmentRepository fulfillmentRepository;
	private final FulfillmentHandoverHistoryRepository historyRepository;
	private final SupplierPortalInputPolicy inputPolicy;
	private final SupplierPortalHasher hasher;
	private final ObjectMapper objectMapper;

	AdminPortalTakeoverService(
		FulfillmentRepository fulfillmentRepository,
		FulfillmentHandoverHistoryRepository historyRepository,
		SupplierPortalInputPolicy inputPolicy,
		SupplierPortalHasher hasher,
		ObjectMapper objectMapper
	) {
		this.fulfillmentRepository = fulfillmentRepository;
		this.historyRepository = historyRepository;
		this.inputPolicy = inputPolicy;
		this.hasher = hasher;
		this.objectMapper = objectMapper;
	}

	@Transactional
	AdminFulfillmentPrivacyDtos.PortalTakeoverResponse takeOver(
		UUID orderId,
		UUID adminUserId,
		String idempotencyKey,
		AdminFulfillmentPrivacyDtos.PortalTakeoverRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = inputPolicy.requirePortalTakeoverReasonCode(request.reason());
		String hash = hasher.hmac("portal-takeover", adminUserId.toString(), reason);
		UUID fulfillmentId = fulfillmentRepository.findIdByOrderId(orderId).orElseThrow(this::notFound);
		AdminFulfillmentPrivacyDtos.PortalTakeoverResponse replay = replay(fulfillmentId, key, hash);
		if (replay != null) return replay;
		Fulfillment fulfillment = fulfillmentRepository.findByIdForUpdate(fulfillmentId).orElseThrow(this::notFound);
		replay = replay(fulfillment.getId(), key, hash);
		if (replay != null) return replay;
		Instant now = Instant.now();
		if (!fulfillment.handOverToCoreable(
			now, FulfillmentHandoverReasonCode.ADMIN_TAKEOVER, reason, adminUserId
		)) {
			throw conflict("Portal fulfillment is not open for supplier takeover");
		}
		AdminFulfillmentPrivacyDtos.PortalTakeoverResponse response =
			new AdminFulfillmentPrivacyDtos.PortalTakeoverResponse(
				orderId, fulfillment.getId(), fulfillment.getOperationalOwner(), now, reason
			);
		historyRepository.save(FulfillmentHandoverHistory.admin(
			fulfillment, adminUserId, FulfillmentHandoverReasonCode.ADMIN_TAKEOVER,
			reason, hash, key, json(response), now
		));
		return response;
	}

	private AdminFulfillmentPrivacyDtos.PortalTakeoverResponse replay(UUID fulfillmentId, String key, String hash) {
		FulfillmentHandoverHistory history = historyRepository
			.findByFulfillment_IdAndIdempotencyKey(fulfillmentId, key).orElse(null);
		if (history == null) return null;
		if (!history.matchesReplay(key, hash) || history.getResultSnapshot() == null) {
			throw conflict("Idempotency key conflict");
		}
		try {
			return objectMapper.readValue(history.getResultSnapshot(),
				AdminFulfillmentPrivacyDtos.PortalTakeoverResponse.class);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to read portal takeover result");
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize portal takeover result");
		}
	}

	private ApiErrorException notFound() {
		return new ApiErrorException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
	}

	private ApiErrorException conflict(String message) {
		return new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_CONFLICT, message);
	}
}
