package com.dropshipshop.api.auth;

import com.dropshipshop.api.user.domain.SocialProvider;

interface OAuthProviderClient {

	OAuthProfile fetchProfile(SocialProvider provider, String code);
}
