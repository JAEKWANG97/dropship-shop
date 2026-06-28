package com.dropshipshop.api.account;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class AccountAgreementDtos {

	private AccountAgreementDtos() {
	}

	record AgreementStateResponse(
		boolean requiredAgreed,
		String requiredTermsVersion,
		String requiredPrivacyVersion,
		String agreedTermsVersion,
		String agreedPrivacyVersion,
		Instant agreedAt
	) {
	}

	record AgreeRequest(
		@AssertTrue boolean termsAgreed,
		@AssertTrue boolean privacyAgreed,
		@NotBlank @Size(max = 50) String termsVersion,
		@NotBlank @Size(max = 50) String privacyVersion
	) {
	}

	record AgreeResponse(
		UUID agreementId,
		boolean requiredAgreed,
		String termsVersion,
		String privacyVersion,
		Instant agreedAt
	) {
	}
}
