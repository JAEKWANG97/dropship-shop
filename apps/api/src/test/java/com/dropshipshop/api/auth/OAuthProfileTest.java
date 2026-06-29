package com.dropshipshop.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OAuthProfileTest {

	@Test
	void fallsBackToProviderUserIdWhenDisplayNameIsMissing() {
		OAuthProfile profile = new OAuthProfile("kakao-user-1", "kakao-user-1@oauth.local", null);

		assertThat(profile.displayName()).isEqualTo("kakao-user-1");
	}
}
