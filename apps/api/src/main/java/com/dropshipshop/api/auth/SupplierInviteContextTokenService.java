package com.dropshipshop.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.SupplierPortalProperties;

@Component
class SupplierInviteContextTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
	private final SupplierPortalHasher hasher;
	private final SupplierPortalProperties properties;

	SupplierInviteContextTokenService(SupplierPortalHasher hasher, SupplierPortalProperties properties) {
		this.hasher = hasher;
		this.properties = properties;
	}

	String issueInviteContext(UUID inviteId, String tokenDigest) {
		Instant expiresAt = Instant.now().plus(properties.inviteContextTtl());
		return signed("invite-context", inviteId, tokenDigest, newNonce(), expiresAt, null);
	}

	Optional<Binding> verifyInviteContext(String value) {
		return verify("invite-context", value, null);
	}

	String issueOAuthState(Binding context, String state) {
		Instant expiresAt = Instant.now().plus(properties.inviteContextTtl());
		return signed("invite-oauth-state", context.inviteId(), context.tokenDigest(), context.nonce(), expiresAt, state);
	}

	Optional<Binding> verifyOAuthState(String value, String state) {
		return verify("invite-oauth-state", value, state);
	}

	private String signed(
		String domain,
		UUID inviteId,
		String tokenDigest,
		String nonce,
		Instant expiresAt,
		String state
	) {
		String payload = String.join("|",
			"v1",
			inviteId.toString(),
			tokenDigest,
			nonce,
			Long.toString(expiresAt.getEpochSecond()),
			state == null ? "" : state
		);
		String encoded = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
		return encoded + "." + hasher.hmac(domain, encoded);
	}

	private Optional<Binding> verify(String domain, String value, String expectedState) {
		try {
			String[] signed = value == null ? new String[0] : value.split("\\.", -1);
			if (signed.length != 2 || !hasher.matches(hasher.hmac(domain, signed[0]), signed[1])) {
				return Optional.empty();
			}
			String payload = new String(DECODER.decode(signed[0]), StandardCharsets.UTF_8);
			String[] parts = payload.split("\\|", -1);
			if (parts.length != 6 || !"v1".equals(parts[0])) {
				return Optional.empty();
			}
			Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(parts[4]));
			if (!Instant.now().isBefore(expiresAt)) {
				return Optional.empty();
			}
			if (expectedState != null && !hasher.matches(parts[5], expectedState)) {
				return Optional.empty();
			}
			return Optional.of(new Binding(UUID.fromString(parts[1]), parts[2], parts[3], expiresAt));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private String newNonce() {
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		return ENCODER.encodeToString(bytes);
	}

	record Binding(UUID inviteId, String tokenDigest, String nonce, Instant expiresAt) {
	}
}
