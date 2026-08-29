package com.dropshipshop.api.supplierportal;

import java.time.Duration;
import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SupplierPortalProperties {

	private final boolean enabled;
	private final String hmacSecret;
	private final Duration inviteTtl;
	private final Duration inviteContextTtl;
	private final Duration applicationRetention;
	private final Duration inviteRecipientRetention;
	private final String kakaoRedirectUri;
	private final String successRedirectUri;
	private final int applicationRateLimit;
	private final Duration applicationRateWindow;

	SupplierPortalProperties(
		@Value("${app.supplier-portal.enabled:false}") boolean enabled,
		@Value("${app.supplier-portal.hmac-secret}") String hmacSecret,
		@Value("${app.supplier-portal.invite-ttl-seconds:604800}") long inviteTtlSeconds,
		@Value("${app.supplier-portal.invite-context-ttl-seconds:300}") long inviteContextTtlSeconds,
		@Value("${app.supplier-portal.application-retention-days:90}") long applicationRetentionDays,
		@Value("${app.supplier-portal.invite-recipient-retention-days:30}") long inviteRecipientRetentionDays,
		@Value("${app.supplier-portal.kakao-redirect-uri:http://localhost:8080/api/supplier/auth/kakao/callback}") String kakaoRedirectUri,
		@Value("${app.supplier-portal.success-redirect-uri:http://localhost:3000/supplier}") String successRedirectUri,
		@Value("${app.supplier-portal.application-rate-limit:10}") int applicationRateLimit,
		@Value("${app.supplier-portal.application-rate-window-seconds:600}") long applicationRateWindowSeconds
	) {
		if (hmacSecret == null || hmacSecret.length() < 32) {
			throw new IllegalStateException("Supplier portal HMAC secret must be at least 32 characters");
		}
		if (inviteTtlSeconds <= 0 || inviteContextTtlSeconds <= 0
			|| applicationRetentionDays <= 0 || inviteRecipientRetentionDays <= 0
			|| applicationRateLimit <= 0 || applicationRateWindowSeconds <= 0) {
			throw new IllegalStateException("Supplier portal durations and limits must be positive");
		}
		requireHttpUri(kakaoRedirectUri, "Kakao supplier redirect URI");
		requireHttpUri(successRedirectUri, "Supplier portal success redirect URI");
		this.enabled = enabled;
		this.hmacSecret = hmacSecret;
		this.inviteTtl = Duration.ofSeconds(inviteTtlSeconds);
		this.inviteContextTtl = Duration.ofSeconds(inviteContextTtlSeconds);
		this.applicationRetention = Duration.ofDays(applicationRetentionDays);
		this.inviteRecipientRetention = Duration.ofDays(inviteRecipientRetentionDays);
		this.kakaoRedirectUri = kakaoRedirectUri;
		this.successRedirectUri = successRedirectUri;
		this.applicationRateLimit = applicationRateLimit;
		this.applicationRateWindow = Duration.ofSeconds(applicationRateWindowSeconds);
	}

	public boolean enabled() {
		return enabled;
	}

	public String hmacSecret() {
		return hmacSecret;
	}

	public Duration inviteTtl() {
		return inviteTtl;
	}

	public Duration inviteContextTtl() {
		return inviteContextTtl;
	}

	public Duration applicationRetention() {
		return applicationRetention;
	}

	public Duration inviteRecipientRetention() {
		return inviteRecipientRetention;
	}

	public String kakaoRedirectUri() {
		return kakaoRedirectUri;
	}

	public String successRedirectUri() {
		return successRedirectUri;
	}

	public int applicationRateLimit() {
		return applicationRateLimit;
	}

	public Duration applicationRateWindow() {
		return applicationRateWindow;
	}

	private void requireHttpUri(String value, String label) {
		try {
			URI uri = URI.create(value);
			if (uri.getHost() == null || uri.getUserInfo() != null
				|| !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
				throw new IllegalArgumentException();
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException(label + " must be an absolute HTTP(S) URI");
		}
	}
}
