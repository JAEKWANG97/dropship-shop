package com.dropshipshop.api.auth;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieFactory {

	private final AuthProperties authProperties;

	AuthCookieFactory(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	public ResponseCookie accessToken(String value) {
		return cookie(authProperties.accessTokenCookieName(), value, authProperties.accessTokenTtl());
	}

	ResponseCookie cookie(String name, String value, Duration maxAge) {
		return ResponseCookie.from(name, value)
			.httpOnly(true)
			.secure(authProperties.cookieSecure())
			.sameSite("Lax")
			.path("/")
			.maxAge(maxAge)
			.build();
	}

	ResponseCookie delete(String name) {
		return cookie(name, "", Duration.ZERO);
	}
}
