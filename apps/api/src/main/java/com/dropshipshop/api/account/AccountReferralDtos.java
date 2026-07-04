package com.dropshipshop.api.account;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class AccountReferralDtos {

	private AccountReferralDtos() {
	}

	record ReferralStateResponse(
		String myReferralCode,
		boolean referrerRegistered
	) {
	}

	record ReferralRegisterRequest(
		@NotBlank @Size(max = 20) String code
	) {
	}

	record AdminReferralListResponse(
		List<AdminReferralResponse> referrals
	) {
	}

	record AdminReferralResponse(
		UUID referrerUserId,
		String referrerDisplayName,
		String referralCode,
		UUID referredUserId,
		String referredDisplayName,
		Instant referredAt
	) {
	}
}
