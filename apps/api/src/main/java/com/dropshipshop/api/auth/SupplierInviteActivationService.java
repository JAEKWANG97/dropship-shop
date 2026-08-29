package com.dropshipshop.api.auth;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.supplierportal.SupplierInvitationService;
import com.dropshipshop.api.supplierportal.domain.SupplierInvite;
import com.dropshipshop.api.supplierportal.repository.SupplierInviteRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;
import com.dropshipshop.api.notification.NotificationLogRepository;

@Service
class SupplierInviteActivationService {

	private final SupplierInviteRepository inviteRepository;
	private final SupplierRepository supplierRepository;
	private final UserAccountRepository userAccountRepository;
	private final SupplierInvitationService invitationService;
	private final NotificationLogRepository notificationLogRepository;

	SupplierInviteActivationService(
		SupplierInviteRepository inviteRepository,
		SupplierRepository supplierRepository,
		UserAccountRepository userAccountRepository,
		SupplierInvitationService invitationService,
		NotificationLogRepository notificationLogRepository
	) {
		this.inviteRepository = inviteRepository;
		this.supplierRepository = supplierRepository;
		this.userAccountRepository = userAccountRepository;
		this.invitationService = invitationService;
		this.notificationLogRepository = notificationLogRepository;
	}

	@Transactional
	ActivationResult activate(SupplierInviteContextTokenService.Binding binding, OAuthProfile profile) {
		SupplierInvite resolved = inviteRepository.findByTokenDigest(binding.tokenDigest())
			.filter(invite -> invite.getId().equals(binding.inviteId()))
			.orElseThrow(() -> inviteError(ApiErrorCode.INVITE_INVALID));
		Supplier supplier = supplierRepository.findByIdForUpdate(resolved.getSupplier().getId())
			.orElseThrow(() -> inviteError(ApiErrorCode.INVITE_INVALID));
		SupplierInvite invite = inviteRepository.findByIdForUpdate(binding.inviteId())
			.orElseThrow(() -> inviteError(ApiErrorCode.INVITE_INVALID));
		Instant now = Instant.now();
		if (!invite.getSupplier().getId().equals(supplier.getId())
			|| !invite.getTokenDigest().equals(binding.tokenDigest())) {
			throw inviteError(ApiErrorCode.INVITE_INVALID);
		}
		invitationService.assertUsable(invite, now);
		if (supplier.getPortalStatus() != SupplierPortalStatus.PENDING_ACTIVATION
			|| supplier.getManagerUserId() != null
			|| supplier.getEmail() == null
			|| !supplier.getEmail().equalsIgnoreCase(invite.getRecipientEmail())) {
			throw inviteError(ApiErrorCode.MANAGER_ALREADY_LINKED);
		}

		UserAccount user = activeKakaoUser(profile);
		Supplier existingAssignment = supplierRepository.findByManagerUserId(user.getId()).orElse(null);
		if (existingAssignment != null && !existingAssignment.getId().equals(supplier.getId())) {
			throw inviteError(ApiErrorCode.ACCOUNT_ALREADY_LINKED);
		}
		try {
			supplier.bindManager(user.getId(), now);
			invite.consume(user.getId(), now);
			notificationLogRepository.findFirstBySupplierInviteIdOrderByCreatedAtDesc(invite.getId())
				.ifPresent(log -> log.scheduleRecipientCleanup(invite.getRecipientRetentionExpiresAt()));
			supplierRepository.saveAndFlush(supplier);
			inviteRepository.saveAndFlush(invite);
			return new ActivationResult(user, supplier, invite);
		} catch (DataIntegrityViolationException exception) {
			throw inviteError(ApiErrorCode.ACCOUNT_ALREADY_LINKED);
		} catch (IllegalStateException exception) {
			throw inviteError(ApiErrorCode.MANAGER_ALREADY_LINKED);
		}
	}

	private UserAccount activeKakaoUser(OAuthProfile profile) {
		UserAccount existing = userAccountRepository.findByProviderAndProviderUserIdAndStatus(
			SocialProvider.KAKAO,
			profile.providerUserId(),
			UserStatus.ACTIVE
		).orElse(null);
		if (existing == null) {
			return userAccountRepository.saveAndFlush(new UserAccount(
				SocialProvider.KAKAO,
				profile.providerUserId(),
				profile.email(),
				profile.displayName(),
				UserRole.CUSTOMER
			));
		}
		return userAccountRepository.findByIdForUpdate(existing.getId())
			.filter(user -> user.getStatus() == UserStatus.ACTIVE)
			.orElseThrow(() -> inviteError(ApiErrorCode.ACCOUNT_ALREADY_LINKED));
	}

	private ApiErrorException inviteError(ApiErrorCode code) {
		return new ApiErrorException(HttpStatus.CONFLICT, code, "Supplier invitation cannot be activated");
	}

	record ActivationResult(UserAccount user, Supplier supplier, SupplierInvite invite) {
	}
}
