package com.dropshipshop.api.supplierportal;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.supplierportal.domain.SupplierInvite;
import com.dropshipshop.api.supplierportal.repository.SupplierInviteRepository;

@Service
public class SupplierInvitationService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder TOKEN_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private final SupplierInviteRepository inviteRepository;
	private final SupplierPortalHasher hasher;
	private final SupplierPortalProperties properties;
	private final SupplierPortalFeatureGate featureGate;
	private final NotificationService notificationService;

	SupplierInvitationService(
		SupplierInviteRepository inviteRepository,
		SupplierPortalHasher hasher,
		SupplierPortalProperties properties,
		SupplierPortalFeatureGate featureGate,
		NotificationService notificationService
	) {
		this.inviteRepository = inviteRepository;
		this.hasher = hasher;
		this.properties = properties;
		this.featureGate = featureGate;
		this.notificationService = notificationService;
	}

	public IssuedInvite issueForApproval(Supplier supplier, UUID applicationId, UUID adminId, Instant now) {
		String issuanceKey = "application:" + applicationId;
		return issue(supplier, issuanceKey, adminId, now);
	}

	public IssuedInvite issue(Supplier supplier, String issuanceKey, UUID adminId, Instant now) {
		String recipient = supplier.getEmail();
		if (recipient == null || recipient.isBlank()) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.INVITE_REISSUE_NOT_ALLOWED, "Supplier contact email is required");
		}
		String requestHash = hasher.hmac(
			"supplier-invite-issuance",
			supplier.getId().toString(),
			issuanceKey,
			recipient
		);
		SupplierInvite replay = inviteRepository
			.findBySupplier_IdAndIssuanceIdempotencyKey(supplier.getId(), issuanceKey)
			.orElse(null);
		if (replay != null) {
			if (!replay.matchesIssuanceReplay(issuanceKey, requestHash)) {
				throw idempotencyConflict();
			}
			return IssuedInvite.replay(replay);
		}
		featureGate.requireInvitationMutationReleased();

		String rawToken = newRawToken();
		Instant expiresAt = now.plus(properties.inviteTtl());
		SupplierInvite invite = inviteRepository.saveAndFlush(SupplierInvite.issue(
			supplier,
			recipient,
			hasher.tokenDigest(rawToken),
			issuanceKey,
			requestHash,
			expiresAt,
			adminId,
			now,
			properties.inviteRecipientRetention()
		));
		notificationService.supplierInvitation(
			supplier.getId(),
			invite.getId(),
			recipient,
			expiresAt,
			rawToken
		);
		return IssuedInvite.created(invite);
	}

	@Transactional(readOnly = true)
	public InviteBinding exchange(String rawToken, Instant now) {
		featureGate.requirePublicReleased();
		if (rawToken == null || rawToken.length() < 40 || rawToken.length() > 200
			|| !rawToken.matches("[A-Za-z0-9_-]+")) {
			throw inviteError(ApiErrorCode.INVITE_INVALID);
		}
		String digest = hasher.tokenDigest(rawToken);
		SupplierInvite invite = inviteRepository.findByTokenDigest(digest)
			.orElseThrow(() -> inviteError(ApiErrorCode.INVITE_INVALID));
		assertUsable(invite, now);
		return new InviteBinding(invite.getId(), invite.getSupplier().getId(), digest);
	}

	public void assertUsable(SupplierInvite invite, Instant now) {
		if (invite.getConsumedAt() != null) {
			throw inviteError(ApiErrorCode.INVITE_ALREADY_USED);
		}
		if (invite.getRevokedAt() != null) {
			throw inviteError(ApiErrorCode.INVITE_REVOKED);
		}
		if (!now.isBefore(invite.getExpiresAt())) {
			throw inviteError(ApiErrorCode.INVITE_EXPIRED);
		}
		if (invite.getRecipientEmail() == null) {
			throw inviteError(ApiErrorCode.INVITE_INVALID);
		}
	}

	private String newRawToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return TOKEN_ENCODER.encodeToString(bytes);
	}

	private ApiErrorException inviteError(ApiErrorCode code) {
		return new ApiErrorException(HttpStatus.CONFLICT, code, "Supplier invitation cannot be used");
	}

	private ApiErrorException idempotencyConflict() {
		return new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_CONFLICT, "Idempotency key conflict");
	}

	public record IssuedInvite(UUID inviteId, UUID supplierId, Instant expiresAt, boolean newlyCreated) {
		static IssuedInvite created(SupplierInvite invite) {
			return new IssuedInvite(invite.getId(), invite.getSupplier().getId(), invite.getExpiresAt(), true);
		}

		static IssuedInvite replay(SupplierInvite invite) {
			return new IssuedInvite(invite.getId(), invite.getSupplier().getId(), invite.getExpiresAt(), false);
		}
	}

	public record InviteBinding(UUID inviteId, UUID supplierId, String tokenDigest) {
	}
}
