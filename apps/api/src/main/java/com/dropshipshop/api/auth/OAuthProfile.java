package com.dropshipshop.api.auth;

record OAuthProfile(
	String providerUserId,
	String email,
	String displayName
) {
}
