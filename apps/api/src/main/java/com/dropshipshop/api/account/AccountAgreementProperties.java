package com.dropshipshop.api.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class AccountAgreementProperties {

	private final String requiredTermsVersion;
	private final String requiredPrivacyVersion;

	AccountAgreementProperties(
		@Value("${app.policies.required-terms-version:prelaunch-2026-06-30}") String requiredTermsVersion,
		@Value("${app.policies.required-privacy-version:prelaunch-2026-06-30}") String requiredPrivacyVersion
	) {
		this.requiredTermsVersion = requiredTermsVersion;
		this.requiredPrivacyVersion = requiredPrivacyVersion;
	}

	String requiredTermsVersion() {
		return requiredTermsVersion;
	}

	String requiredPrivacyVersion() {
		return requiredPrivacyVersion;
	}
}
