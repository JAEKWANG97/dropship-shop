package com.dropshipshop.api.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Service
class OAuthLoginService {

	private static final Logger log = LoggerFactory.getLogger(OAuthLoginService.class);
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String REDIRECT_TO_COOKIE_NAME = "OAUTH2_REDIRECT_TO";

	private final OAuthProviderProperties oauthProviderProperties;
	private final OAuthProviderClient oauthProviderClient;
	private final UserAccountRepository userAccountRepository;
	private final JwtAccessTokenService jwtAccessTokenService;
	private final AuthProperties authProperties;
	private final AuthCookieFactory authCookieFactory;

	OAuthLoginService(
		OAuthProviderProperties oauthProviderProperties,
		OAuthProviderClient oauthProviderClient,
		UserAccountRepository userAccountRepository,
		JwtAccessTokenService jwtAccessTokenService,
		AuthProperties authProperties,
		AuthCookieFactory authCookieFactory
	) {
		this.oauthProviderProperties = oauthProviderProperties;
		this.oauthProviderClient = oauthProviderClient;
		this.userAccountRepository = userAccountRepository;
		this.jwtAccessTokenService = jwtAccessTokenService;
		this.authProperties = authProperties;
		this.authCookieFactory = authCookieFactory;
	}

	ResponseEntity<Void> authorize(String providerValue, String redirectTo) {
		SocialProvider provider = provider(providerValue);
		OAuthProviderProperties.ProviderSettings settings = configuredSettings(provider);
		String state = newState();
		URI location = UriComponentsBuilder
			.fromUriString(settings.authorizationUri())
			.queryParam("response_type", "code")
			.queryParam("client_id", settings.clientId())
			.queryParam("redirect_uri", settings.redirectUri())
			.queryParam("state", state)
			.queryParamIfPresent("scope", optional(settings.scope()))
			.build()
			.encode()
			.toUri();

		return ResponseEntity.status(HttpStatus.FOUND)
			.location(location)
			.header(HttpHeaders.SET_COOKIE, authCookieFactory
				.cookie(authProperties.stateCookieName(), state, authProperties.stateTtl())
				.toString())
			.header(HttpHeaders.SET_COOKIE, redirectToCookie(redirectTo).toString())
			.build();
	}

	ResponseEntity<Void> callback(
		String providerValue,
		String code,
		String state,
		String error,
		HttpServletRequest request
	) {
		if (!isBlank(error)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth provider returned error");
		}
		if (isBlank(code)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth authorization code is required");
		}
		validateState(request, state);

		SocialProvider provider = provider(providerValue);
		configuredSettings(provider);
		OAuthProfile profile = fetchProfile(provider, code);
		UserAccount user = userAccountRepository.findByProviderAndProviderUserIdAndStatus(
				provider,
				profile.providerUserId(),
				UserStatus.ACTIVE
			)
			.orElseGet(() -> userAccountRepository.save(new UserAccount(
				provider,
				profile.providerUserId(),
				profile.email(),
				profile.displayName(),
				UserRole.CUSTOMER
			)));

		String token = jwtAccessTokenService.issue(user);
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(successRedirectUri(request))
			.header(HttpHeaders.SET_COOKIE, authCookieFactory.accessToken(token).toString())
			.header(HttpHeaders.SET_COOKIE, authCookieFactory.delete(authProperties.stateCookieName()).toString())
			.header(HttpHeaders.SET_COOKIE, authCookieFactory.delete(REDIRECT_TO_COOKIE_NAME).toString())
			.build();
	}

	ResponseEntity<Void> logout() {
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, authCookieFactory.delete(authProperties.accessTokenCookieName()).toString())
			.build();
	}

	private OAuthProfile fetchProfile(SocialProvider provider, String code) {
		try {
			return oauthProviderClient.fetchProfile(provider, code);
		} catch (RuntimeException exception) {
			log.warn("OAuth provider request failed: provider={}, message={}", provider, exception.getMessage());
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OAuth provider profile request failed");
		}
	}

	private void validateState(HttpServletRequest request, String state) {
		if (isBlank(state) || cookieValue(request, authProperties.stateCookieName())
			.filter(state::equals)
			.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth state is invalid");
		}
	}

	private OAuthProviderProperties.ProviderSettings configuredSettings(SocialProvider provider) {
		OAuthProviderProperties.ProviderSettings settings = oauthProviderProperties.get(provider);
		if (settings == null || !settings.configured()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth provider is not configured");
		}
		return settings;
	}

	private SocialProvider provider(String value) {
		try {
			return SocialProvider.valueOf(value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported OAuth provider");
		}
	}

	private Optional<String> cookieValue(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
			.filter(cookie -> name.equals(cookie.getName()))
			.map(Cookie::getValue)
			.findFirst();
	}

	private ResponseCookie redirectToCookie(String redirectTo) {
		return safeRedirectTo(redirectTo)
			.map(value -> authCookieFactory.cookie(REDIRECT_TO_COOKIE_NAME, encode(value), authProperties.stateTtl()))
			.orElseGet(() -> authCookieFactory.delete(REDIRECT_TO_COOKIE_NAME));
	}

	private URI successRedirectUri(HttpServletRequest request) {
		Optional<String> redirectTo = cookieValue(request, REDIRECT_TO_COOKIE_NAME)
			.flatMap(this::decode)
			.flatMap(this::safeRedirectTo);
		if (redirectTo.isEmpty()) {
			return URI.create(authProperties.successRedirectUri());
		}
		String separator = authProperties.successRedirectUri().contains("?") ? "&" : "?";
		String encodedRedirectTo = URLEncoder.encode(redirectTo.get(), StandardCharsets.UTF_8);
		return URI.create(authProperties.successRedirectUri() + separator + "redirectTo=" + encodedRedirectTo);
	}

	private Optional<String> safeRedirectTo(String value) {
		if (isBlank(value) || !value.startsWith("/") || value.startsWith("//")) {
			return Optional.empty();
		}
		String normalized = value.toLowerCase(Locale.ROOT);
		if (
			value.contains("\\")
				|| normalized.contains("%5c")
				|| value.contains("\r")
				|| value.contains("\n")
		) {
			return Optional.empty();
		}
		return Optional.of(value);
	}

	private String encode(String value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private Optional<String> decode(String value) {
		try {
			return Optional.of(new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private String newState() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private Optional<String> optional(String value) {
		return isBlank(value) ? Optional.empty() : Optional.of(value);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
