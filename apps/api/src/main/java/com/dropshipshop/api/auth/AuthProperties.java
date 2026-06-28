package com.dropshipshop.api.auth;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthProperties {

	private final String jwtSecret;
	private final String jwtIssuer;
	private final String accessTokenCookieName;
	private final Duration accessTokenTtl;
	private final boolean cookieSecure;
	private final String stateCookieName;
	private final Duration stateTtl;
	private final String successRedirectUri;

	AuthProperties(
		@Value("${app.auth.jwt-secret}") String jwtSecret,
		@Value("${app.auth.jwt-issuer:dropship-shop-api}") String jwtIssuer,
		@Value("${app.auth.access-token-cookie-name:ACCESS_TOKEN}") String accessTokenCookieName,
		@Value("${app.auth.access-token-ttl-seconds:7200}") long accessTokenTtlSeconds,
		@Value("${app.auth.cookie-secure:false}") boolean cookieSecure,
		@Value("${app.auth.oauth-state-cookie-name:OAUTH2_STATE}") String stateCookieName,
		@Value("${app.auth.oauth-state-ttl-seconds:300}") long stateTtlSeconds,
		@Value("${app.auth.success-redirect-uri:http://localhost:3000/auth/callback/success}") String successRedirectUri
	) {
		this.jwtSecret = jwtSecret;
		this.jwtIssuer = jwtIssuer;
		this.accessTokenCookieName = accessTokenCookieName;
		this.accessTokenTtl = Duration.ofSeconds(accessTokenTtlSeconds);
		this.cookieSecure = cookieSecure;
		this.stateCookieName = stateCookieName;
		this.stateTtl = Duration.ofSeconds(stateTtlSeconds);
		this.successRedirectUri = successRedirectUri;
	}

	public String jwtSecret() {
		return jwtSecret;
	}

	public String jwtIssuer() {
		return jwtIssuer;
	}

	public String accessTokenCookieName() {
		return accessTokenCookieName;
	}

	public Duration accessTokenTtl() {
		return accessTokenTtl;
	}

	public boolean cookieSecure() {
		return cookieSecure;
	}

	public String stateCookieName() {
		return stateCookieName;
	}

	public Duration stateTtl() {
		return stateTtl;
	}

	public String successRedirectUri() {
		return successRedirectUri;
	}
}
