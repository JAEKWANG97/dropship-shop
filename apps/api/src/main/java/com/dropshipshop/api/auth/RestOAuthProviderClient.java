package com.dropshipshop.api.auth;

import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.dropshipshop.api.user.domain.SocialProvider;

@Component
class RestOAuthProviderClient implements OAuthProviderClient {

	private final OAuthProviderProperties oauthProviderProperties;
	private final RestClient restClient;
	private final ParameterizedTypeReference<Map<String, Object>> mapBodyType = new ParameterizedTypeReference<>() {
	};

	RestOAuthProviderClient(OAuthProviderProperties oauthProviderProperties) {
		this.oauthProviderProperties = oauthProviderProperties;
		this.restClient = RestClient.create();
	}

	@Override
	public OAuthProfile fetchProfile(SocialProvider provider, String code) {
		OAuthProviderProperties.ProviderSettings settings = oauthProviderProperties.get(provider);
		Map<String, Object> tokenResponse = token(settings, code);
		String accessToken = string(tokenResponse.get("access_token"));
		if (isBlank(accessToken)) {
			throw new OAuthProviderException("OAuth provider did not return access token");
		}
		Map<String, Object> profileResponse = profile(settings, accessToken);
		return switch (provider) {
			case GOOGLE -> googleProfile(profileResponse);
			case KAKAO -> kakaoProfile(profileResponse);
			case NAVER -> naverProfile(profileResponse);
		};
	}

	private Map<String, Object> token(OAuthProviderProperties.ProviderSettings settings, String code) {
		MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
		body.add("grant_type", "authorization_code");
		body.add("client_id", settings.clientId());
		if (!isBlank(settings.clientSecret())) {
			body.add("client_secret", settings.clientSecret());
		}
		body.add("redirect_uri", settings.redirectUri());
		body.add("code", code);

		try {
			return restClient.post()
				.uri(settings.tokenUri())
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(body)
				.retrieve()
				.body(mapBodyType);
		} catch (RestClientResponseException exception) {
			throw new OAuthProviderException("OAuth token request failed: " + exception.getStatusCode());
		}
	}

	private Map<String, Object> profile(OAuthProviderProperties.ProviderSettings settings, String accessToken) {
		try {
			return restClient.get()
				.uri(settings.userInfoUri())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.retrieve()
				.body(mapBodyType);
		} catch (RestClientResponseException exception) {
			throw new OAuthProviderException("OAuth profile request failed: " + exception.getStatusCode());
		}
	}

	private OAuthProfile googleProfile(Map<String, Object> response) {
		return profile(string(response.get("sub")), string(response.get("email")), string(response.get("name")));
	}

	@SuppressWarnings("unchecked")
	private OAuthProfile kakaoProfile(Map<String, Object> response) {
		Map<String, Object> account = map(response.get("kakao_account"));
		Map<String, Object> profile = map(account.get("profile"));
		String providerUserId = string(response.get("id"));
		return profile(
			providerUserId,
			firstNonBlank(string(account.get("email")), "kakao-" + providerUserId + "@oauth.local"),
			string(profile.get("nickname"))
		);
	}

	private OAuthProfile naverProfile(Map<String, Object> response) {
		Map<String, Object> profile = map(response.get("response"));
		return profile(
			string(profile.get("id")),
			string(profile.get("email")),
			firstNonBlank(string(profile.get("name")), string(profile.get("nickname")))
		);
	}

	private OAuthProfile profile(String providerUserId, String email, String displayName) {
		if (isBlank(providerUserId) || isBlank(email)) {
			throw new OAuthProviderException("OAuth provider profile is missing required fields");
		}
		return new OAuthProfile(providerUserId, email, displayName);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> map(Object value) {
		if (!(value instanceof Map<?, ?> map)) {
			return Map.of();
		}
		return (Map<String, Object>) map;
	}

	private String firstNonBlank(String first, String second) {
		return isBlank(first) ? second : first;
	}

	private String string(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
