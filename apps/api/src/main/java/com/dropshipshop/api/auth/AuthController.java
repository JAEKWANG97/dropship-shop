package com.dropshipshop.api.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final OAuthLoginService oauthLoginService;

	AuthController(OAuthLoginService oauthLoginService) {
		this.oauthLoginService = oauthLoginService;
	}

	@GetMapping("/oauth2/{provider}/authorize")
	ResponseEntity<Void> authorize(@PathVariable String provider) {
		return oauthLoginService.authorize(provider);
	}

	@GetMapping("/oauth2/{provider}/callback")
	ResponseEntity<Void> callback(
		@PathVariable String provider,
		@RequestParam(required = false) String code,
		@RequestParam(required = false) String state,
		@RequestParam(required = false) String error,
		HttpServletRequest request
	) {
		return oauthLoginService.callback(provider, code, state, error, request);
	}

	@PostMapping("/logout")
	@PreAuthorize("isAuthenticated()")
	ResponseEntity<Void> logout() {
		return oauthLoginService.logout();
	}
}
