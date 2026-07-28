package com.dropshipshop.api.account;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class AccountProfileDtos {

	private AccountProfileDtos() {
	}

	record ProfileCompletionResponse(
		String displayName,
		boolean displayNameComplete,
		String email,
		boolean emailRequired,
		boolean emailComplete,
		String phoneNumber,
		boolean phoneVerified,
		Instant phoneVerifiedAt,
		boolean requiredInfoComplete
	) {
	}

	record ProfileUpdateRequest(
		@NotBlank @Size(max = 100) String displayName,
		@NotBlank @Email @Size(max = 320) String email,
		@NotBlank @Size(max = 30) String phoneNumber
	) {
	}

	record PhoneVerificationRequest(
		@NotBlank @Size(max = 30) String phoneNumber
	) {
	}

	record PhoneVerificationConfirmRequest(
		@NotBlank @Size(max = 30) String phoneNumber,
		@NotBlank @Size(min = 6, max = 6) String code
	) {
	}

	record PhoneVerificationResponse(
		String phoneNumber,
		Instant expiresAt
	) {
	}
}
