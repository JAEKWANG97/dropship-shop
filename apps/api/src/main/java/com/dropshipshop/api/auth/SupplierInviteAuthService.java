package com.dropshipshop.api.auth;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.supplierportal.SupplierInvitationService;
import com.dropshipshop.api.supplierportal.SupplierPortalDtos;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;
import com.dropshipshop.api.supplierportal.SupplierPortalProperties;
import com.dropshipshop.api.user.domain.SocialProvider;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@Service
class SupplierInviteAuthService {

	static final String INVITE_CONTEXT_COOKIE = "SUPPLIER_INVITE_CONTEXT";
	static final String INVITE_STATE_COOKIE = "SUPPLIER_INVITE_STATE";
	private static final SecureRandom RANDOM = new SecureRandom();
	private final SupplierInvitationService invitationService;
	private final SupplierInviteContextTokenService contextTokenService;
	private final SupplierInviteActivationService activationService;
	private final SupplierPortalFeatureGate featureGate;
	private final SupplierPortalProperties properties;
	private final OAuthProviderProperties oauthProviderProperties;
	private final OAuthProviderClient oauthProviderClient;
	private final JwtAccessTokenService jwtAccessTokenService;
	private final AuthCookieFactory cookieFactory;

	SupplierInviteAuthService(
		SupplierInvitationService invitationService,
		SupplierInviteContextTokenService contextTokenService,
		SupplierInviteActivationService activationService,
		SupplierPortalFeatureGate featureGate,
		SupplierPortalProperties properties,
		OAuthProviderProperties oauthProviderProperties,
		OAuthProviderClient oauthProviderClient,
		JwtAccessTokenService jwtAccessTokenService,
		AuthCookieFactory cookieFactory
	) {
		this.invitationService = invitationService;
		this.contextTokenService = contextTokenService;
		this.activationService = activationService;
		this.featureGate = featureGate;
		this.properties = properties;
		this.oauthProviderProperties = oauthProviderProperties;
		this.oauthProviderClient = oauthProviderClient;
		this.jwtAccessTokenService = jwtAccessTokenService;
		this.cookieFactory = cookieFactory;
	}

	ResponseEntity<SupplierPortalDtos.InviteSessionResponse> exchange(String rawToken) {
		featureGate.requirePublicReleased();
		SupplierInvitationService.InviteBinding binding = invitationService.exchange(rawToken, Instant.now());
		String context = contextTokenService.issueInviteContext(binding.inviteId(), binding.tokenDigest());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookieFactory
				.cookie(INVITE_CONTEXT_COOKIE, context, properties.inviteContextTtl()).toString())
			.header(HttpHeaders.SET_COOKIE, cookieFactory.delete(INVITE_STATE_COOKIE).toString())
			.body(new SupplierPortalDtos.InviteSessionResponse("/api/supplier/auth/kakao/authorize"));
	}

	ResponseEntity<Void> authorize(HttpServletRequest request) {
		featureGate.requirePublicReleased();
		SupplierInviteContextTokenService.Binding context = cookieValue(request, INVITE_CONTEXT_COOKIE)
			.flatMap(contextTokenService::verifyInviteContext)
			.orElseThrow(() -> inviteError(ApiErrorCode.INVITE_INVALID));
		OAuthProviderProperties.ProviderSettings settings = kakaoSettings();
		String state = newState();
		String signedState = contextTokenService.issueOAuthState(context, state);
		URI location = UriComponentsBuilder
			.fromUriString(settings.authorizationUri())
			.queryParam("response_type", "code")
			.queryParam("client_id", settings.clientId())
			.queryParam("redirect_uri", properties.kakaoRedirectUri())
			.queryParam("state", state)
			.queryParamIfPresent("scope", optional(settings.scope()))
			.build()
			.encode()
			.toUri();
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(location)
			.header(HttpHeaders.SET_COOKIE, cookieFactory
				.cookie(INVITE_STATE_COOKIE, signedState, properties.inviteContextTtl()).toString())
			.build();
	}

	ResponseEntity<Void> callback(
		String code,
		String state,
		String error,
		HttpServletRequest request
	) {
		featureGate.requirePublicReleased();
		if (!isBlank(error) || isBlank(code) || isBlank(state)) {
			return errorRedirect(ApiErrorCode.OAUTH_TEMPORARY_FAILURE, false);
		}
		SupplierInviteContextTokenService.Binding context = cookieValue(request, INVITE_CONTEXT_COOKIE)
			.flatMap(contextTokenService::verifyInviteContext)
			.orElse(null);
		SupplierInviteContextTokenService.Binding stateBinding = cookieValue(request, INVITE_STATE_COOKIE)
			.flatMap(value -> contextTokenService.verifyOAuthState(value, state))
			.orElse(null);
		if (context == null || stateBinding == null || !sameBinding(context, stateBinding)) {
			return errorRedirect(ApiErrorCode.INVITE_INVALID, true);
		}

		OAuthProfile profile;
		try {
			profile = oauthProviderClient.fetchProfile(SocialProvider.KAKAO, code, properties.kakaoRedirectUri());
		} catch (RuntimeException exception) {
			return errorRedirect(ApiErrorCode.OAUTH_TEMPORARY_FAILURE, false);
		}
		try {
			SupplierInviteActivationService.ActivationResult result = activationService.activate(context, profile);
			String accessToken = jwtAccessTokenService.issue(result.user());
			return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(properties.successRedirectUri()))
				.header(HttpHeaders.SET_COOKIE, cookieFactory.accessToken(accessToken).toString())
				.header(HttpHeaders.SET_COOKIE, cookieFactory.delete(INVITE_CONTEXT_COOKIE).toString())
				.header(HttpHeaders.SET_COOKIE, cookieFactory.delete(INVITE_STATE_COOKIE).toString())
				.build();
		} catch (ApiErrorException exception) {
			return errorRedirect(safeInviteError(exception.getCode()), true);
		} catch (DataIntegrityViolationException exception) {
			return errorRedirect(ApiErrorCode.ACCOUNT_ALREADY_LINKED, true);
		}
	}

	private OAuthProviderProperties.ProviderSettings kakaoSettings() {
		OAuthProviderProperties.ProviderSettings settings = oauthProviderProperties.get(SocialProvider.KAKAO);
		if (settings == null || !settings.configured()) {
			throw new ApiErrorException(
				HttpStatus.SERVICE_UNAVAILABLE,
				ApiErrorCode.OAUTH_TEMPORARY_FAILURE,
				"Kakao OAuth is unavailable"
			);
		}
		return settings;
	}

	private ResponseEntity<Void> errorRedirect(ApiErrorCode code, boolean clearContext) {
		URI location = UriComponentsBuilder.fromUriString(properties.successRedirectUri())
			.replacePath("/supplier/activate")
			.replaceQuery(null)
			.queryParam("error", code.name())
			.build()
			.encode()
			.toUri();
		ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.FOUND)
			.location(location)
			.header(HttpHeaders.SET_COOKIE, cookieFactory.delete(INVITE_STATE_COOKIE).toString());
		if (clearContext) {
			response.header(HttpHeaders.SET_COOKIE, cookieFactory.delete(INVITE_CONTEXT_COOKIE).toString());
		}
		return response.build();
	}

	private ApiErrorCode safeInviteError(ApiErrorCode code) {
		return switch (code) {
			case INVITE_INVALID, INVITE_EXPIRED, INVITE_ALREADY_USED, INVITE_REVOKED,
				MANAGER_ALREADY_LINKED, ACCOUNT_ALREADY_LINKED -> code;
			default -> ApiErrorCode.INVITE_INVALID;
		};
	}

	private boolean sameBinding(
		SupplierInviteContextTokenService.Binding first,
		SupplierInviteContextTokenService.Binding second
	) {
		return first.inviteId().equals(second.inviteId())
			&& first.tokenDigest().equals(second.tokenDigest())
			&& first.nonce().equals(second.nonce());
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

	private String newState() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private Optional<String> optional(String value) {
		return isBlank(value) ? Optional.empty() : Optional.of(value);
	}

	private ApiErrorException inviteError(ApiErrorCode code) {
		return new ApiErrorException(HttpStatus.CONFLICT, code, "Supplier invitation cannot be used");
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
