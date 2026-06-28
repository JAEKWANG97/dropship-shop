package com.dropshipshop.api.auth;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.dropshipshop.api.user.domain.SocialProvider;

@Component
class OAuthProviderProperties {

	private final Map<SocialProvider, ProviderSettings> settings;

	OAuthProviderProperties(
		@Value("${app.oauth.google.client-id:}") String googleClientId,
		@Value("${app.oauth.google.client-secret:}") String googleClientSecret,
		@Value("${app.oauth.google.redirect-uri:}") String googleRedirectUri,
		@Value("${app.oauth.google.scope:openid email profile}") String googleScope,
		@Value("${app.oauth.kakao.client-id:}") String kakaoClientId,
		@Value("${app.oauth.kakao.client-secret:}") String kakaoClientSecret,
		@Value("${app.oauth.kakao.redirect-uri:}") String kakaoRedirectUri,
		@Value("${app.oauth.kakao.scope:profile_nickname}") String kakaoScope,
		@Value("${app.oauth.naver.client-id:}") String naverClientId,
		@Value("${app.oauth.naver.client-secret:}") String naverClientSecret,
		@Value("${app.oauth.naver.redirect-uri:}") String naverRedirectUri,
		@Value("${app.oauth.naver.scope:}") String naverScope
	) {
		settings = Map.of(
			SocialProvider.GOOGLE, new ProviderSettings(
				googleClientId,
				googleClientSecret,
				googleRedirectUri,
				googleScope,
				"https://accounts.google.com/o/oauth2/v2/auth",
				"https://oauth2.googleapis.com/token",
				"https://openidconnect.googleapis.com/v1/userinfo"
			),
			SocialProvider.KAKAO, new ProviderSettings(
				kakaoClientId,
				kakaoClientSecret,
				kakaoRedirectUri,
				kakaoScope,
				"https://kauth.kakao.com/oauth/authorize",
				"https://kauth.kakao.com/oauth/token",
				"https://kapi.kakao.com/v2/user/me"
			),
			SocialProvider.NAVER, new ProviderSettings(
				naverClientId,
				naverClientSecret,
				naverRedirectUri,
				naverScope,
				"https://nid.naver.com/oauth2.0/authorize",
				"https://nid.naver.com/oauth2.0/token",
				"https://openapi.naver.com/v1/nid/me"
			)
		);
	}

	ProviderSettings get(SocialProvider provider) {
		return settings.get(provider);
	}

	record ProviderSettings(
		String clientId,
		String clientSecret,
		String redirectUri,
		String scope,
		String authorizationUri,
		String tokenUri,
		String userInfoUri
	) {

		boolean configured() {
			return !isBlank(clientId) && !isBlank(redirectUri);
		}

		private boolean isBlank(String value) {
			return value == null || value.isBlank();
		}
	}
}
