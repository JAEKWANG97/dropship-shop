package com.dropshipshop.api.support;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InquiryLookupTokenService {

	private final SecretKeySpec secretKey;

	InquiryLookupTokenService(@Value("${app.inquiry.lookup-secret}") String secret) {
		if (secret == null || secret.length() < 32) {
			throw new IllegalStateException("APP_INQUIRY_LOOKUP_SECRET must be at least 32 characters");
		}
		this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	public String token(UUID inquiryId) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(secretKey);
			return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(mac.doFinal(inquiryId.toString().getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException | InvalidKeyException exception) {
			throw new IllegalStateException("Inquiry lookup token cannot be generated", exception);
		}
	}

	public boolean matches(UUID inquiryId, String candidate) {
		if (candidate == null) {
			return false;
		}
		return MessageDigest.isEqual(
			token(inquiryId).getBytes(StandardCharsets.UTF_8),
			candidate.getBytes(StandardCharsets.UTF_8)
		);
	}
}
