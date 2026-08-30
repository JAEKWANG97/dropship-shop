package com.dropshipshop.api.supplierfulfillment;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
import com.dropshipshop.api.fulfillment.domain.FulfillmentHandoverReasonCode;
import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.SupplierPortalInputPolicy;
import com.dropshipshop.api.supplierportal.repository.FulfillmentHandoverHistoryRepository;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
class AdminSupplierPiiGrantService {

	private static final Duration MAX_WINDOW = Duration.ofDays(30);
	private static final Set<ClaimStatus> ALLOWED_STATUSES = EnumSet.of(
		ClaimStatus.APPROVED,
		ClaimStatus.RETURN_WAITING,
		ClaimStatus.RETURN_RECEIVED,
		ClaimStatus.REFUND_PROCESSING,
		ClaimStatus.EXCHANGE_SHIPPING
	);

	private final ClaimRepository claimRepository;
	private final CustomerOrderRepository orderRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final FulfillmentHandoverHistoryRepository handoverHistoryRepository;
	private final SupplierPiiAccessGrantRepository grantRepository;
	private final UserAccountRepository userAccountRepository;
	private final SupplierPortalInputPolicy inputPolicy;
	private final SupplierPortalHasher hasher;
	private final ObjectMapper objectMapper;

	AdminSupplierPiiGrantService(
		ClaimRepository claimRepository,
		CustomerOrderRepository orderRepository,
		FulfillmentRepository fulfillmentRepository,
		FulfillmentHandoverHistoryRepository handoverHistoryRepository,
		SupplierPiiAccessGrantRepository grantRepository,
		UserAccountRepository userAccountRepository,
		SupplierPortalInputPolicy inputPolicy,
		SupplierPortalHasher hasher,
		ObjectMapper objectMapper
	) {
		this.claimRepository = claimRepository;
		this.orderRepository = orderRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.handoverHistoryRepository = handoverHistoryRepository;
		this.grantRepository = grantRepository;
		this.userAccountRepository = userAccountRepository;
		this.inputPolicy = inputPolicy;
		this.hasher = hasher;
		this.objectMapper = objectMapper;
	}

	@Transactional
	AdminFulfillmentPrivacyDtos.GrantResponse grant(
		UUID claimId,
		UUID adminUserId,
		String idempotencyKey,
		AdminFulfillmentPrivacyDtos.GrantRequest request
	) {
		if (request.action() != SupplierPiiGrantAction.GRANTED
			&& request.action() != SupplierPiiGrantAction.EXTENDED) {
			throw validation("action must be GRANTED or EXTENDED");
		}
		return append(claimId, adminUserId, idempotencyKey, request.action(), request.expectedLatestGrantId(),
			request.accessUntil(), request.reason());
	}

	@Transactional
	AdminFulfillmentPrivacyDtos.GrantResponse revoke(
		UUID claimId,
		UUID adminUserId,
		String idempotencyKey,
		AdminFulfillmentPrivacyDtos.RevokeRequest request
	) {
		return append(claimId, adminUserId, idempotencyKey, SupplierPiiGrantAction.REVOKED,
			request.expectedLatestGrantId(), null, request.reason());
	}

	private AdminFulfillmentPrivacyDtos.GrantResponse append(
		UUID claimId,
		UUID adminUserId,
		String idempotencyKey,
		SupplierPiiGrantAction action,
		UUID expectedLatestGrantId,
		Instant accessUntil,
		String rawReason
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = action == SupplierPiiGrantAction.REVOKED
			? inputPolicy.requirePiiRevokeReasonCode(rawReason)
			: inputPolicy.requirePiiGrantReasonCode(rawReason);
		String hash = hasher.hmac("supplier-pii-grant", adminUserId.toString(), action.name(),
			expectedLatestGrantId == null ? null : expectedLatestGrantId.toString(),
			accessUntil == null ? null : accessUntil.toString(), reason);

		UUID orderId = claimRepository.findOrderIdById(claimId).orElseThrow(this::notFound);
		AdminFulfillmentPrivacyDtos.GrantResponse replay = replay(claimId, key, hash);
		if (replay != null) return replay;

		CustomerOrder order = orderRepository.findByIdForUpdate(orderId).orElseThrow(this::notFound);
		Claim claim = claimRepository.findByIdForUpdate(claimId).orElseThrow(this::notFound);
		if (!claim.getOrder().getId().equals(order.getId())) throw notFound();
		replay = replay(claimId, key, hash);
		if (replay != null) return replay;
		SupplierPiiAccessGrant latest = grantRepository.findFirstByClaim_IdOrderBySequenceDesc(claimId).orElse(null);
		if (!java.util.Objects.equals(expectedLatestGrantId, latest == null ? null : latest.getId())) {
			throw conflict("Latest grant changed");
		}
		if (!ALLOWED_STATUSES.contains(claim.getStatus())) {
			throw conflict("Claim status does not allow supplier PII access");
		}
		Supplier supplier = order.getSupplier();
		Instant now = Instant.now();
		if (!supplier.hasTimeValidContract(now)) {
			throw new ApiErrorException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN,
				"Supplier contract is not current");
		}
		if (fulfillmentRepository.findChannelByOrderId(orderId)
			.filter(channel -> channel == FulfillmentChannel.SUPPLIER_PORTAL)
			.isEmpty()) {
			throw notFound();
		}
		Fulfillment fulfillment = fulfillmentRepository.findByOrderIdForUpdate(orderId).orElseThrow(this::notFound);
		if (fulfillment.getChannel() != FulfillmentChannel.SUPPLIER_PORTAL
			|| !fulfillment.getSupplier().getId().equals(supplier.getId())) throw notFound();
		if (fulfillment.getOperationalOwner() == FulfillmentOperationalOwner.COREABLE) {
			FulfillmentHandoverReasonCode reasonCode = handoverHistoryRepository
				.findFirstByFulfillment_IdOrderByCreatedAtDesc(fulfillment.getId())
				.map(history -> history.getReasonCode())
				.orElseThrow(this::notFound);
			if (reasonCode != FulfillmentHandoverReasonCode.PII_CUTOFF_REACHED
				&& reasonCode != FulfillmentHandoverReasonCode.TERMINAL_STATE) {
				throw notFound();
			}
		}
		if (action == SupplierPiiGrantAction.EXTENDED
			&& (latest == null || !latest.isActiveAt(now))) {
			throw conflict("Only an active grant can be extended");
		}
		if (action == SupplierPiiGrantAction.GRANTED && latest != null
			&& latest.getAction() != SupplierPiiGrantAction.REVOKED
			&& latest.isActiveAt(now)) {
			throw conflict("An active grant must be extended instead");
		}
		if (action != SupplierPiiGrantAction.REVOKED
			&& (accessUntil == null || !now.isBefore(accessUntil) || accessUntil.isAfter(now.plus(MAX_WINDOW)))) {
			throw validation("accessUntil must be in the future and within 30 days");
		}
		if (action == SupplierPiiGrantAction.REVOKED && latest == null) {
			throw conflict("There is no grant to revoke");
		}
		if (action == SupplierPiiGrantAction.REVOKED
			&& latest.getAction() == SupplierPiiGrantAction.REVOKED) {
			throw conflict("Grant is already revoked");
		}
		UserAccount admin = userAccountRepository.findByIdAndStatus(adminUserId, UserStatus.ACTIVE)
			.orElseThrow(this::notFound);
		int sequence = latest == null ? 1 : latest.getSequence() + 1;
		Instant createdAt = now;
		SupplierPiiAccessGrant row = new SupplierPiiAccessGrant(
			claim, supplier, sequence, action, accessUntil, latest, admin, reason, hash, key,
			"{}", createdAt
		);
		AdminFulfillmentPrivacyDtos.GrantResponse response = new AdminFulfillmentPrivacyDtos.GrantResponse(
			row.getId(), claimId, supplier.getId(), sequence, action, accessUntil,
			latest == null ? null : latest.getId(), createdAt
		);
		row.initializeResultSnapshot(json(response));
		grantRepository.save(row);
		return response;
	}

	private AdminFulfillmentPrivacyDtos.GrantResponse replay(UUID claimId, String key, String hash) {
		SupplierPiiAccessGrant row = grantRepository.findByClaim_IdAndIdempotencyKey(claimId, key).orElse(null);
		if (row == null) return null;
		if (!row.matchesReplay(hash)) throw conflict("Idempotency key conflict");
		try {
			return objectMapper.readValue(row.getResultSnapshot(), AdminFulfillmentPrivacyDtos.GrantResponse.class);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to read supplier PII grant result");
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize supplier PII grant result");
		}
	}

	private ApiErrorException notFound() {
		return new ApiErrorException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
	}
	private ApiErrorException conflict(String message) {
		return new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_CONFLICT, message);
	}
	private ApiErrorException validation(String message) {
		return new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, message);
	}
}
