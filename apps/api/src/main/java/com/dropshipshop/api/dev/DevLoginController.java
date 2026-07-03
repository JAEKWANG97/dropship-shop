package com.dropshipshop.api.dev;

import java.util.Map;
import java.util.Locale;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.auth.AuthCookieFactory;
import com.dropshipshop.api.auth.JwtAccessTokenService;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@RestController
@RequestMapping("/api/dev/login")
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "app.dev-login", name = "enabled", havingValue = "true")
class DevLoginController {

	private static final SocialProvider DEFAULT_PROVIDER = SocialProvider.GOOGLE;
	private static final Map<UserRole, String> SEED_PROVIDER_USER_IDS = Map.of(
		UserRole.CUSTOMER, "local-b003-customer",
		UserRole.ADMIN, "local-b003-admin"
	);

	private final UserAccountRepository userAccountRepository;
	private final JwtAccessTokenService jwtAccessTokenService;
	private final AuthCookieFactory authCookieFactory;

	DevLoginController(
		UserAccountRepository userAccountRepository,
		JwtAccessTokenService jwtAccessTokenService,
		AuthCookieFactory authCookieFactory
	) {
		this.userAccountRepository = userAccountRepository;
		this.jwtAccessTokenService = jwtAccessTokenService;
		this.authCookieFactory = authCookieFactory;
	}

	@GetMapping
	ResponseEntity<DevLoginResponse> login(
		@RequestParam(required = false) String providerUserId,
		@RequestParam(required = false) String provider,
		@RequestParam(required = false) String role
	) {
		return login(new DevLoginRequest(provider, providerUserId, role));
	}

	@PostMapping
	ResponseEntity<DevLoginResponse> login(@RequestBody(required = false) DevLoginRequest request) {
		DevLoginRequest normalizedRequest = request == null ? new DevLoginRequest(null, null, null) : request;
		SocialProvider provider = provider(normalizedRequest.provider());
		String providerUserId = providerUserId(normalizedRequest.providerUserId(), normalizedRequest.role());
		UserAccount user = userAccountRepository.findByProviderAndProviderUserIdAndStatus(
				provider,
				providerUserId,
				UserStatus.ACTIVE
			)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seed user is not found"));
		String token = jwtAccessTokenService.issue(user);
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, authCookieFactory.accessToken(token).toString())
			.body(new DevLoginResponse(
				user.getId(),
				user.getProvider(),
				user.getProviderUserId(),
				user.getRole(),
				user.getDisplayName(),
				user.getEmail()
			));
	}

	private SocialProvider provider(String value) {
		if (isBlank(value)) {
			return DEFAULT_PROVIDER;
		}
		try {
			return SocialProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported provider");
		}
	}

	private String providerUserId(String value, String roleValue) {
		if (!isBlank(value)) {
			return value.trim();
		}
		UserRole role = role(roleValue);
		if (role == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "providerUserId or role is required");
		}
		return SEED_PROVIDER_USER_IDS.get(role);
	}

	private UserRole role(String value) {
		if (isBlank(value)) {
			return null;
		}
		try {
			UserRole role = UserRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
			if (!SEED_PROVIDER_USER_IDS.containsKey(role)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported seed role");
			}
			return role;
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported seed role");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	record DevLoginRequest(String provider, String providerUserId, String role) {
	}

	record DevLoginResponse(
		UUID userId,
		SocialProvider provider,
		String providerUserId,
		UserRole role,
		String displayName,
		String email
	) {
	}
}
