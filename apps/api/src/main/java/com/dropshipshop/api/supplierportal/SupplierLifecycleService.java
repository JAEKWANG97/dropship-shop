package com.dropshipshop.api.supplierportal;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.domain.SupplierSalesAction;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentHandoverReasonCode;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.notification.NotificationLogRepository;
import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.supplierportal.SupplierInvitationService.IssuedInvite;
import com.dropshipshop.api.supplierportal.domain.FulfillmentHandoverHistory;
import com.dropshipshop.api.supplierportal.domain.SupplierInvite;
import com.dropshipshop.api.supplierportal.domain.SupplierInviteRevocationReasonCode;
import com.dropshipshop.api.supplierportal.domain.SupplierPortalAction;
import com.dropshipshop.api.supplierportal.domain.SupplierPortalActionHistory;
import com.dropshipshop.api.supplierportal.repository.FulfillmentHandoverHistoryRepository;
import com.dropshipshop.api.supplierportal.repository.SupplierInviteRepository;
import com.dropshipshop.api.supplierportal.repository.SupplierPortalActionHistoryRepository;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class SupplierLifecycleService {

	private final SupplierRepository supplierRepository;
	private final SupplierInviteRepository inviteRepository;
	private final SupplierPortalActionHistoryRepository actionHistoryRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final FulfillmentHandoverHistoryRepository handoverHistoryRepository;
	private final NotificationLogRepository notificationLogRepository;
	private final UserAccountRepository userAccountRepository;
	private final SupplierInvitationService invitationService;
	private final SupplierPortalFeatureGate featureGate;
	private final SupplierPortalHasher hasher;
	private final SupplierPortalInputPolicy inputPolicy;
	private final SupplierContractTerminalService contractTerminalService;
	private final ObjectMapper objectMapper;

	SupplierLifecycleService(
		SupplierRepository supplierRepository,
		SupplierInviteRepository inviteRepository,
		SupplierPortalActionHistoryRepository actionHistoryRepository,
		FulfillmentRepository fulfillmentRepository,
		FulfillmentHandoverHistoryRepository handoverHistoryRepository,
		NotificationLogRepository notificationLogRepository,
		UserAccountRepository userAccountRepository,
		SupplierInvitationService invitationService,
		SupplierPortalFeatureGate featureGate,
		SupplierPortalHasher hasher,
		SupplierPortalInputPolicy inputPolicy,
		SupplierContractTerminalService contractTerminalService,
		ObjectMapper objectMapper
	) {
		this.supplierRepository = supplierRepository;
		this.inviteRepository = inviteRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.handoverHistoryRepository = handoverHistoryRepository;
		this.notificationLogRepository = notificationLogRepository;
		this.userAccountRepository = userAccountRepository;
		this.invitationService = invitationService;
		this.featureGate = featureGate;
		this.hasher = hasher;
		this.inputPolicy = inputPolicy;
		this.contractTerminalService = contractTerminalService;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public SupplierPortalDtos.SupplierLifecycleResponse get(UUID supplierId) {
		return lifecycle(supplierRepository.findById(supplierId)
			.orElseThrow(() -> notFound("Supplier not found")));
	}

	@Transactional
	public CommandOutcome updatePortalStatus(
		UUID supplierId,
		UUID adminId,
		String idempotencyKey,
		SupplierPortalDtos.PortalStatusRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = inputPolicy.requirePiiFreeReason(request.reason(), 200);
		if (request.portalStatus() == SupplierPortalStatus.PENDING_ACTIVATION) {
			throw validation("PENDING_ACTIVATION is not a direct portal-status target");
		}
		String requestHash = hasher.hmac(
			"supplier-portal-lifecycle",
			SupplierPortalAction.class.getSimpleName(),
			"PORTAL_STATUS",
			request.portalStatus().name(),
			request.salesAction().name(),
			reason
		);
		SupplierPortalDtos.SupplierLifecycleResponse replay = lifecycleReplay(supplierId, key, requestHash);
		if (replay != null) {
			return CommandOutcome.completed(replay);
		}

		Supplier supplier = lockSupplier(supplierId);
		replay = lifecycleReplay(supplierId, key, requestHash);
		if (replay != null) {
			return CommandOutcome.completed(replay);
		}
		if (request.portalStatus() == SupplierPortalStatus.ACTIVE) {
			featureGate.requireInvitationMutationReleased();
			if (contractTerminalService.expireIfOverdue(supplier, adminId, reason, Instant.now())) {
				return CommandOutcome.contractMissing();
			}
			if (supplier.getManagerUserId() == null || userAccountRepository
				.findByIdAndStatus(supplier.getManagerUserId(), UserStatus.ACTIVE).isEmpty()) {
				throw lifecycleConflict("An active supplier manager is required");
			}
		}

		SupplierPortalStatus beforePortal = supplier.getPortalStatus();
		SupplierStatus beforeSales = supplier.getStatus();
		SupplierPortalAction action;
		FulfillmentHandoverReasonCode handoverReason = null;
		try {
			switch (request.portalStatus()) {
				case SUSPENDED -> {
					supplier.suspendPortal(request.salesAction());
					action = SupplierPortalAction.PORTAL_SUSPENDED;
					handoverReason = FulfillmentHandoverReasonCode.PORTAL_SUSPENDED;
				}
				case ACTIVE -> {
					supplier.reactivatePortal(Instant.now(), request.salesAction());
					action = SupplierPortalAction.PORTAL_REACTIVATED;
				}
				case DISABLED -> {
					supplier.disablePortal(request.salesAction());
					action = SupplierPortalAction.PORTAL_DISABLED;
					handoverReason = FulfillmentHandoverReasonCode.PORTAL_DISABLED;
					revokeOpenInvite(supplier, adminId, null, Instant.now());
				}
				default -> throw validation("Unsupported portal status");
			}
		} catch (IllegalStateException exception) {
			if (request.portalStatus() == SupplierPortalStatus.ACTIVE && !supplier.hasTimeValidContract(Instant.now())) {
				return CommandOutcome.contractMissing();
			}
			throw lifecycleConflict(exception.getMessage());
		}
		if (handoverReason != null) {
			handOverOpenWork(supplier, adminId, handoverReason, reason, Instant.now());
		}
		SupplierPortalDtos.SupplierLifecycleResponse response = lifecycle(supplier);
		recordHistory(
			supplier,
			adminId,
			action,
			beforePortal,
			beforeSales,
			request.salesAction(),
			reason,
			requestHash,
			key,
			response,
			Instant.now()
		);
		return CommandOutcome.completed(response);
	}

	@Transactional
	public CommandOutcome changeSalesStatus(
		UUID supplierId,
		UUID adminId,
		String idempotencyKey,
		SupplierPortalDtos.SalesStatusRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = inputPolicy.requirePiiFreeReason(request.reason(), 200);
		String requestHash = hasher.hmac(
			"supplier-portal-lifecycle",
			SupplierPortalAction.SALES_STATUS_CHANGED.name(),
			request.status().name(),
			reason
		);
		SupplierPortalDtos.SupplierLifecycleResponse replay = lifecycleReplay(supplierId, key, requestHash);
		if (replay != null) {
			return CommandOutcome.completed(replay);
		}
		Supplier supplier = lockSupplier(supplierId);
		replay = lifecycleReplay(supplierId, key, requestHash);
		if (replay != null) {
			return CommandOutcome.completed(replay);
		}
		Instant now = Instant.now();
		if (request.status() == SupplierStatus.ACTIVE
			&& contractTerminalService.expireIfOverdue(supplier, adminId, reason, now)) {
			return CommandOutcome.contractMissing();
		}
		SupplierPortalStatus beforePortal = supplier.getPortalStatus();
		SupplierStatus beforeSales = supplier.getStatus();
		try {
			supplier.changeSalesStatus(request.status(), now);
		} catch (IllegalStateException exception) {
			if (request.status() == SupplierStatus.ACTIVE) {
				return CommandOutcome.contractMissing();
			}
			throw lifecycleConflict(exception.getMessage());
		}
		SupplierPortalDtos.SupplierLifecycleResponse response = lifecycle(supplier);
		recordHistory(
			supplier,
			adminId,
			SupplierPortalAction.SALES_STATUS_CHANGED,
			beforePortal,
			beforeSales,
			null,
			reason,
			requestHash,
			key,
			response,
			now
		);
		return CommandOutcome.completed(response);
	}

	@Transactional
	public SupplierPortalDtos.SupplierLifecycleResponse disconnectManager(
		UUID supplierId,
		UUID adminId,
		String idempotencyKey,
		SupplierPortalDtos.ManagerDisconnectRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = inputPolicy.requirePiiFreeReason(request.reason(), 200);
		String requestHash = hasher.hmac(
			"supplier-portal-lifecycle",
			SupplierPortalAction.MANAGER_DISCONNECTED.name(),
			request.salesAction().name(),
			reason
		);
		SupplierPortalDtos.SupplierLifecycleResponse replay = lifecycleReplay(supplierId, key, requestHash);
		if (replay != null) {
			return replay;
		}
		Supplier supplier = lockSupplier(supplierId);
		replay = lifecycleReplay(supplierId, key, requestHash);
		if (replay != null) {
			return replay;
		}
		if (supplier.getManagerUserId() == null) {
			throw lifecycleConflict("Supplier manager is not linked");
		}
		SupplierPortalStatus beforePortal = supplier.getPortalStatus();
		SupplierStatus beforeSales = supplier.getStatus();
		Instant now = Instant.now();
		supplier.disconnectManager(request.salesAction());
		revokeOpenInvite(supplier, adminId, null, now);
		handOverOpenWork(supplier, adminId, FulfillmentHandoverReasonCode.MANAGER_DISCONNECTED, reason, now);
		SupplierPortalDtos.SupplierLifecycleResponse response = lifecycle(supplier);
		recordHistory(
			supplier,
			adminId,
			SupplierPortalAction.MANAGER_DISCONNECTED,
			beforePortal,
			beforeSales,
			request.salesAction(),
			reason,
			requestHash,
			key,
			response,
			now
		);
		return response;
	}

	@Transactional
	public SupplierPortalDtos.SupplierLifecycleResponse changeContactEmail(
		UUID supplierId,
		UUID adminId,
		String idempotencyKey,
		SupplierPortalDtos.ContactEmailRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = inputPolicy.requirePiiFreeReason(request.reason(), 200);
		String normalizedEmail = hasher.normalizeEmail(request.contactEmail());
		String requestHash = hasher.hmac(
			"supplier-portal-lifecycle",
			SupplierPortalAction.CONTACT_EMAIL_CHANGED.name(),
			normalizedEmail,
			request.salesAction().name(),
			reason
		);
		SupplierPortalDtos.SupplierLifecycleResponse replay = lifecycleReplay(supplierId, key, requestHash);
		if (replay != null) {
			return replay;
		}
		Supplier supplier = lockSupplier(supplierId);
		replay = lifecycleReplay(supplierId, key, requestHash);
		if (replay != null) {
			return replay;
		}
		featureGate.requireInvitationMutationReleased();
		if (supplierRepository.existsByCanonicalEmailAndIdNot(normalizedEmail, supplierId)) {
			throw lifecycleConflict("Supplier contact email is already in use");
		}
		SupplierPortalStatus beforePortal = supplier.getPortalStatus();
		SupplierStatus beforeSales = supplier.getStatus();
		Instant now = Instant.now();
		revokeOpenInvite(supplier, adminId, SupplierInviteRevocationReasonCode.RECIPIENT_CHANGED, now);
		supplier.changeContactEmail(normalizedEmail, request.salesAction());
		handOverOpenWork(supplier, adminId, FulfillmentHandoverReasonCode.CONTACT_EMAIL_CHANGED, reason, now);
		String inviteKey = "contact:" + hasher.hmac("supplier-contact-invite-key", supplierId.toString(), key);
		invitationService.issue(supplier, inviteKey, adminId, now);
		SupplierPortalDtos.SupplierLifecycleResponse response = lifecycle(supplier);
		recordHistory(
			supplier,
			adminId,
			SupplierPortalAction.CONTACT_EMAIL_CHANGED,
			beforePortal,
			beforeSales,
			request.salesAction(),
			reason,
			requestHash,
			key,
			response,
			now
		);
		return response;
	}

	@Transactional
	public SupplierPortalDtos.InviteResponse reissueInvite(
		UUID supplierId,
		UUID adminId,
		String idempotencyKey,
		SupplierPortalDtos.InviteReissueRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String requestHash = hasher.hmac(
			"supplier-invite-reissue",
			request.reasonCode().name(),
			key
		);
		SupplierPortalDtos.InviteResponse replay = historyReplay(supplierId, key, requestHash, SupplierPortalDtos.InviteResponse.class);
		if (replay != null) {
			return replay;
		}
		Supplier supplier = lockSupplier(supplierId);
		replay = historyReplay(supplierId, key, requestHash, SupplierPortalDtos.InviteResponse.class);
		if (replay != null) {
			return replay;
		}
		if (supplier.getPortalStatus() != SupplierPortalStatus.PENDING_ACTIVATION
			|| supplier.getManagerUserId() != null
			|| supplier.getEmail() == null
			|| supplier.getContactEmailVerifiedAt() != null) {
			throw new ApiErrorException(
				HttpStatus.CONFLICT,
				ApiErrorCode.INVITE_REISSUE_NOT_ALLOWED,
				"Supplier invitation cannot be reissued"
			);
		}
		featureGate.requireInvitationMutationReleased();
		Instant now = Instant.now();
		revokeOpenInvite(supplier, adminId, request.reasonCode(), now);
		String issuanceKey = "reissue:" + hasher.hmac("supplier-invite-reissue-key", supplierId.toString(), key);
		IssuedInvite invite = invitationService.issue(supplier, issuanceKey, adminId, now);
		SupplierPortalDtos.InviteResponse response = new SupplierPortalDtos.InviteResponse(
			invite.inviteId(),
			invite.supplierId(),
			invite.expiresAt(),
			"PENDING"
		);
		actionHistoryRepository.save(new SupplierPortalActionHistory(
			supplier,
			adminId,
			SupplierPortalAction.INVITE_REISSUED,
			supplier.getPortalStatus(),
			supplier.getPortalStatus(),
			supplier.getStatus(),
			supplier.getStatus(),
			null,
			request.reasonCode().name(),
			requestHash,
			key,
			json(response),
			now
		));
		return response;
	}

	private void handOverOpenWork(
		Supplier supplier,
		UUID adminId,
		FulfillmentHandoverReasonCode reasonCode,
		String reason,
		Instant now
	) {
		for (Fulfillment fulfillment : fulfillmentRepository.findOpenPortalSupplierOwnedForUpdate(supplier.getId())) {
			if (fulfillment.handOverToCoreable(now, reasonCode, adminId)) {
				handoverHistoryRepository.save(FulfillmentHandoverHistory.admin(
					fulfillment,
					adminId,
					reasonCode,
					reason,
					null,
					null,
					null,
					now
				));
			}
		}
	}

	private void revokeOpenInvite(
		Supplier supplier,
		UUID adminId,
		SupplierInviteRevocationReasonCode reasonCode,
		Instant now
	) {
		SupplierInvite invite = inviteRepository.findOpenBySupplierIdForUpdate(supplier.getId()).orElse(null);
		if (invite == null) {
			return;
		}
		if (reasonCode == null) {
			invite.revokeForLifecycle(adminId, now);
		} else {
			invite.revoke(adminId, reasonCode, now);
		}
		notificationLogRepository.findFirstBySupplierInviteIdOrderByCreatedAtDesc(invite.getId())
			.ifPresent(log -> log.scheduleRecipientCleanup(invite.getRecipientRetentionExpiresAt()));
	}

	private void recordHistory(
		Supplier supplier,
		UUID adminId,
		SupplierPortalAction action,
		SupplierPortalStatus beforePortal,
		SupplierStatus beforeSales,
		SupplierSalesAction salesAction,
		String reason,
		String requestHash,
		String key,
		SupplierPortalDtos.SupplierLifecycleResponse response,
		Instant now
	) {
		actionHistoryRepository.save(new SupplierPortalActionHistory(
			supplier,
			adminId,
			action,
			beforePortal,
			supplier.getPortalStatus(),
			beforeSales,
			supplier.getStatus(),
			salesAction,
			reason,
			requestHash,
			key,
			json(response),
			now
		));
	}

	private SupplierPortalDtos.SupplierLifecycleResponse lifecycleReplay(UUID supplierId, String key, String requestHash) {
		return historyReplay(supplierId, key, requestHash, SupplierPortalDtos.SupplierLifecycleResponse.class);
	}

	private <T> T historyReplay(UUID supplierId, String key, String requestHash, Class<T> type) {
		SupplierPortalActionHistory history = actionHistoryRepository
			.findBySupplier_IdAndIdempotencyKey(supplierId, key)
			.orElse(null);
		if (history == null) {
			return null;
		}
		if (!history.matchesReplay(key, requestHash) || history.getResultSnapshot() == null) {
			throw idempotencyConflict();
		}
		return fromJson(history.getResultSnapshot(), type);
	}

	private Supplier lockSupplier(UUID supplierId) {
		return supplierRepository.findByIdForUpdate(supplierId)
			.orElseThrow(() -> notFound("Supplier not found"));
	}

	private SupplierPortalDtos.SupplierLifecycleResponse lifecycle(Supplier supplier) {
		return new SupplierPortalDtos.SupplierLifecycleResponse(
			supplier.getId(),
			supplier.getName(),
			supplier.getContactName(),
			supplier.getEmail(),
			supplier.getManagerUserId(),
			supplier.getPortalStatus(),
			supplier.getStatus(),
			supplier.getPortalContractStatus(),
			supplier.getPortalContractVersion(),
			supplier.getPortalContractEffectiveAt(),
			supplier.getPortalContractExpiresAt(),
			supplier.getContactEmailVerifiedAt()
		);
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize supplier lifecycle result");
		}
	}

	private <T> T fromJson(String value, Class<T> type) {
		try {
			return objectMapper.readValue(value, type);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to read supplier lifecycle result");
		}
	}

	private ApiErrorException validation(String message) {
		return new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, message);
	}

	private ApiErrorException lifecycleConflict(String message) {
		return new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, message);
	}

	private ApiErrorException idempotencyConflict() {
		return new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency key conflict");
	}

	private ApiErrorException notFound(String message) {
		return new ApiErrorException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, message);
	}

	public record CommandOutcome(
		SupplierPortalDtos.SupplierLifecycleResponse response,
		boolean contractNotVerified
	) {
		static CommandOutcome completed(SupplierPortalDtos.SupplierLifecycleResponse response) {
			return new CommandOutcome(response, false);
		}

		static CommandOutcome contractMissing() {
			return new CommandOutcome(null, true);
		}
	}
}
