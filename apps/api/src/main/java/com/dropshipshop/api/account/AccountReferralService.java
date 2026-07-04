package com.dropshipshop.api.account;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@Service
public class AccountReferralService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
	private static final int CODE_LENGTH = 8;
	private static final int CODE_GENERATION_ATTEMPTS = 20;

	private final UserAccountRepository userAccountRepository;
	private final Clock clock;

	AccountReferralService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
		this.clock = Clock.systemUTC();
	}

	@Transactional
	public AccountReferralDtos.ReferralStateResponse getReferralState(UUID userId) {
		UserAccount user = findUserForUpdate(userId);
		ensureReferralCode(user);
		return toState(user);
	}

	@Transactional
	public AccountReferralDtos.ReferralStateResponse registerReferrer(
		UUID userId,
		AccountReferralDtos.ReferralRegisterRequest request
	) {
		UserAccount user = findUserForUpdate(userId);
		if (user.getReferredBy() != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Referrer is already registered");
		}

		String code = normalizeCode(request.code());
		UserAccount referrer = userAccountRepository.findByReferralCode(code)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referral code is not found"));
		if (referrer.getStatus() != UserStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referral code is not available");
		}
		if (referrer.getId().equals(user.getId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Self referral is not allowed");
		}

		user.registerReferrer(referrer, Instant.now(clock));
		ensureReferralCode(user);
		return toState(user);
	}

	@Transactional(readOnly = true)
	public AccountReferralDtos.AdminReferralListResponse listAdminReferrals() {
		return new AccountReferralDtos.AdminReferralListResponse(
			userAccountRepository.findAllByReferredByIsNotNullOrderByReferredAtDesc()
				.stream()
				.map(this::toAdminReferral)
				.toList()
		);
	}

	private UserAccount findUserForUpdate(UUID userId) {
		return userAccountRepository.findByIdForUpdate(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
	}

	private void ensureReferralCode(UserAccount user) {
		if (user.getReferralCode() != null) {
			return;
		}

		for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
			String code = nextCode();
			if (!userAccountRepository.existsByReferralCode(code)) {
				user.assignReferralCode(code);
				return;
			}
		}
		throw new IllegalStateException("Failed to generate referral code");
	}

	private AccountReferralDtos.ReferralStateResponse toState(UserAccount user) {
		return new AccountReferralDtos.ReferralStateResponse(
			user.getReferralCode(),
			user.getReferredBy() != null
		);
	}

	private AccountReferralDtos.AdminReferralResponse toAdminReferral(UserAccount referred) {
		UserAccount referrer = referred.getReferredBy();
		return new AccountReferralDtos.AdminReferralResponse(
			referrer.getId(),
			referrer.getDisplayName(),
			referrer.getReferralCode(),
			referred.getId(),
			referred.getDisplayName(),
			referred.getReferredAt()
		);
	}

	private String normalizeCode(String value) {
		return value.trim().toUpperCase(Locale.ROOT);
	}

	private String nextCode() {
		StringBuilder builder = new StringBuilder(CODE_LENGTH);
		for (int index = 0; index < CODE_LENGTH; index++) {
			builder.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
		}
		return builder.toString();
	}
}
