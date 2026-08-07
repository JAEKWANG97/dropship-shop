package com.dropshipshop.api.user;

import java.time.Duration;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.AuthProperties;
import com.dropshipshop.api.auth.security.CurrentUser;

@RestController
@RequestMapping("/api/me")
class UserProfileController {

	private final CurrentUser currentUser;
	private final AccountDeletionService accountDeletionService;
	private final AuthProperties authProperties;

	UserProfileController(
		CurrentUser currentUser,
		AccountDeletionService accountDeletionService,
		AuthProperties authProperties
	) {
		this.currentUser = currentUser;
		this.accountDeletionService = accountDeletionService;
		this.authProperties = authProperties;
	}

	@GetMapping
	UserProfileResponse me(Authentication authentication) {
		return new UserProfileResponse(currentUser.id(authentication));
	}

	@PostMapping("/deletion-request")
	@PreAuthorize("hasRole('CUSTOMER') and !hasRole('ADMIN')")
	ResponseEntity<Void> requestDeletion(Authentication authentication) {
		accountDeletionService.deleteCustomerAccount(currentUser.id(authentication));
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, deleteAccessTokenCookie().toString())
			.build();
	}

	private ResponseCookie deleteAccessTokenCookie() {
		return ResponseCookie.from(authProperties.accessTokenCookieName(), "")
			.httpOnly(true)
			.secure(authProperties.cookieSecure())
			.sameSite("Lax")
			.path("/")
			.maxAge(Duration.ZERO)
			.build();
	}

	record UserProfileResponse(UUID userId) {
	}
}
