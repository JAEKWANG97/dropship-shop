package com.dropshipshop.api.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.account.domain.PhoneVerificationCode;
import com.dropshipshop.api.account.repository.PhoneVerificationCodeRepository;
import com.dropshipshop.api.sms.SmsSender;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@Service
public class AccountProfileService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int CODE_TTL_MINUTES = 5;
	private static final int RESEND_COOLDOWN_SECONDS = 60;

	private final UserAccountRepository userAccountRepository;
	private final PhoneVerificationCodeRepository phoneVerificationCodeRepository;
	private final SmsSender smsSender;
	private final Clock clock;

	AccountProfileService(
		UserAccountRepository userAccountRepository,
		PhoneVerificationCodeRepository phoneVerificationCodeRepository,
		SmsSender smsSender
	) {
		this.userAccountRepository = userAccountRepository;
		this.phoneVerificationCodeRepository = phoneVerificationCodeRepository;
		this.smsSender = smsSender;
		this.clock = Clock.systemUTC();
	}

	@Transactional(readOnly = true)
	public AccountProfileDtos.ProfileCompletionResponse getProfileCompletion(UUID userId) {
		return toCompletion(findUser(userId));
	}

	@Transactional
	public AccountProfileDtos.ProfileCompletionResponse updateProfile(
		UUID userId,
		AccountProfileDtos.ProfileUpdateRequest request
	) {
		UserAccount user = findUser(userId);
		user.updateProfile(request.displayName().trim(), request.email().trim());
		return toCompletion(user);
	}

	@Transactional
	public AccountProfileDtos.PhoneVerificationResponse requestPhoneVerification(
		UUID userId,
		AccountProfileDtos.PhoneVerificationRequest request
	) {
		UserAccount user = findUser(userId);
		String phoneNumber = normalizePhone(request.phoneNumber());
		Instant now = Instant.now(clock);
		phoneVerificationCodeRepository.findFirstByUser_IdAndPhoneNumberOrderByCreatedAtDesc(userId, phoneNumber)
			.filter(latest -> latest.getCreatedAt().isAfter(now.minus(RESEND_COOLDOWN_SECONDS, ChronoUnit.SECONDS)))
			.ifPresent(latest -> {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Phone verification code was sent recently");
			});

		String code = nextCode();
		Instant expiresAt = now.plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES);
		phoneVerificationCodeRepository.save(new PhoneVerificationCode(user, phoneNumber, hash(phoneNumber, code), expiresAt));
		smsSender.sendVerificationCode(phoneNumber, code);
		return new AccountProfileDtos.PhoneVerificationResponse(phoneNumber, expiresAt);
	}

	@Transactional
	public AccountProfileDtos.ProfileCompletionResponse confirmPhoneVerification(
		UUID userId,
		AccountProfileDtos.PhoneVerificationConfirmRequest request
	) {
		UserAccount user = findUser(userId);
		String phoneNumber = normalizePhone(request.phoneNumber());
		PhoneVerificationCode verification = phoneVerificationCodeRepository
			.findFirstByUser_IdAndPhoneNumberOrderByCreatedAtDesc(userId, phoneNumber)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone verification code is not found"));
		Instant now = Instant.now(clock);
		if (verification.isVerified()) {
			user.verifyPhone(phoneNumber, verification.getVerifiedAt());
			return toCompletion(user);
		}
		if (verification.isExpired(now)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone verification code is expired");
		}
		if (!verification.hasAttemptsLeft()) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Phone verification attempts exceeded");
		}
		if (!hash(phoneNumber, request.code()).equals(verification.getCodeHash())) {
			verification.recordFailedAttempt();
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone verification code does not match");
		}
		verification.verify(now);
		user.verifyPhone(phoneNumber, now);
		return toCompletion(user);
	}

	@Transactional(readOnly = true)
	public void requireRequiredInfo(UUID userId) {
		if (!toCompletion(findUser(userId)).requiredInfoComplete()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required customer information is missing");
		}
	}

	private UserAccount findUser(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
	}

	private AccountProfileDtos.ProfileCompletionResponse toCompletion(UserAccount user) {
		boolean displayNameComplete = user.getDisplayName() != null && !user.getDisplayName().isBlank();
		boolean emailRequired = isPlaceholderEmail(user.getEmail());
		boolean emailComplete = !emailRequired && user.getEmail() != null && !user.getEmail().isBlank();
		boolean phoneVerified = user.getPhoneVerifiedAt() != null;
		return new AccountProfileDtos.ProfileCompletionResponse(
			user.getDisplayName(),
			displayNameComplete,
			user.getEmail(),
			emailRequired,
			emailComplete,
			user.getPhoneNumber(),
			phoneVerified,
			user.getPhoneVerifiedAt(),
			displayNameComplete && emailComplete && phoneVerified
		);
	}

	private boolean isPlaceholderEmail(String email) {
		return email == null || email.isBlank() || email.endsWith("@oauth.local");
	}

	private String normalizePhone(String value) {
		String phoneNumber = value.replaceAll("[^0-9]", "");
		if (!phoneNumber.matches("01[016789][0-9]{7,8}")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number format is invalid");
		}
		return phoneNumber;
	}

	private String nextCode() {
		return "%06d".formatted(RANDOM.nextInt(1_000_000));
	}

	private String hash(String phoneNumber, String code) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest((phoneNumber + ":" + code).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hashed);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to hash phone verification code");
		}
	}
}
