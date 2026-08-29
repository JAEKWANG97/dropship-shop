package com.dropshipshop.api.supplierportal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public class SupplierPortalHasher {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private final SecretKeySpec secretKey;

	SupplierPortalHasher(SupplierPortalProperties properties) {
		this.secretKey = new SecretKeySpec(properties.hmacSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
	}

	public String hmac(String domain, String... values) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(secretKey);
			mac.update(field(domain));
			for (String value : values) {
				mac.update(field(value));
			}
			return ENCODER.encodeToString(mac.doFinal());
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to create supplier portal request HMAC");
		}
	}

	public String tokenDigest(String token) {
		try {
			return ENCODER.encodeToString(MessageDigest.getInstance("SHA-256")
				.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to create invitation token digest");
		}
	}

	public boolean matches(String first, String second) {
		if (first == null || second == null) {
			return false;
		}
		return MessageDigest.isEqual(
			first.getBytes(StandardCharsets.UTF_8),
			second.getBytes(StandardCharsets.UTF_8)
		);
	}

	public String normalizeText(String value) {
		if (value == null) {
			return null;
		}
		return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
	}

	public String normalizeEmail(String value) {
		String normalized = normalizeText(value);
		return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
	}

	private byte[] field(String value) {
		if (value == null) {
			return "-1:|".getBytes(StandardCharsets.UTF_8);
		}
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		return (bytes.length + ":" + new String(bytes, StandardCharsets.UTF_8) + "|")
			.getBytes(StandardCharsets.UTF_8);
	}
}
