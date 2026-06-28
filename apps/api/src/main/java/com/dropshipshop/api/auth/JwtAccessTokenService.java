package com.dropshipshop.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.dropshipshop.api.user.domain.UserAccount;

@Component
public class JwtAccessTokenService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

	private final AuthProperties authProperties;

	JwtAccessTokenService(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	public String issue(UserAccount user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(authProperties.accessTokenTtl());
		String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
		String payload = base64Url("""
			{"iss":"%s","sub":"%s","iat":%d,"exp":%d}
			""".formatted(
			escape(authProperties.jwtIssuer()),
			user.getId(),
			now.getEpochSecond(),
			expiresAt.getEpochSecond()
		));
		String signingInput = header + "." + payload;
		return signingInput + "." + sign(signingInput);
	}

	public Optional<UUID> verify(String token) {
		try {
			String[] parts = token == null ? new String[0] : token.split("\\.");
			if (parts.length != 3) {
				return Optional.empty();
			}

			String signingInput = parts[0] + "." + parts[1];
			if (!MessageDigest.isEqual(sign(signingInput).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
				return Optional.empty();
			}

			String payload = new String(BASE64_URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
			String issuer = stringClaim(payload, "iss");
			if (!authProperties.jwtIssuer().equals(issuer)) {
				return Optional.empty();
			}
			long expiresAt = longClaim(payload, "exp");
			if (expiresAt <= Instant.now().getEpochSecond()) {
				return Optional.empty();
			}
			return Optional.of(UUID.fromString(stringClaim(payload, "sub")));
		} catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}

	private String sign(String value) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to sign access token");
		}
	}

	private String base64Url(String value) {
		return BASE64_URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private String stringClaim(String json, String name) {
		String key = "\"" + name + "\":\"";
		int start = json.indexOf(key);
		if (start < 0) {
			throw new IllegalArgumentException("Missing JWT claim: " + name);
		}
		int valueStart = start + key.length();
		int valueEnd = json.indexOf('"', valueStart);
		return json.substring(valueStart, valueEnd);
	}

	private long longClaim(String json, String name) {
		String key = "\"" + name + "\":";
		int start = json.indexOf(key);
		if (start < 0) {
			throw new IllegalArgumentException("Missing JWT claim: " + name);
		}
		int valueStart = start + key.length();
		int valueEnd = json.indexOf(',', valueStart);
		if (valueEnd < 0) {
			valueEnd = json.indexOf('}', valueStart);
		}
		return Long.parseLong(json.substring(valueStart, valueEnd).trim());
	}

	private String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
