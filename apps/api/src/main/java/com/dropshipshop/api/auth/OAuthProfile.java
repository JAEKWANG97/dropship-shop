package com.dropshipshop.api.auth;

record OAuthProfile(
	String providerUserId,
	String email,
	String displayName
) {

	OAuthProfile {
		displayName = isBlank(displayName) ? providerUserId : displayName;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
