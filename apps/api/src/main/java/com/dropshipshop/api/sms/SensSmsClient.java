package com.dropshipshop.api.sms;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class SensSmsClient {

	private static final int SMS_MAX_BYTES = 90;

	private final String accessKey;
	private final String secretKey;
	private final String serviceId;
	private final String fromNumber;
	private final RestClient restClient;

	SensSmsClient(
		String accessKey,
		String secretKey,
		String serviceId,
		String fromNumber,
		String baseUrl
	) {
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.serviceId = serviceId;
		this.fromNumber = fromNumber;
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
	}

	void send(String phoneNumber, String content) {
		String uri = "/sms/v2/services/%s/messages".formatted(serviceId);
		String timestamp = String.valueOf(Instant.now().toEpochMilli());
		restClient.post()
			.uri(uri)
			.header("x-ncp-apigw-timestamp", timestamp)
			.header("x-ncp-iam-access-key", accessKey)
			.header("x-ncp-apigw-signature-v2", signature(timestamp, uri))
			.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.body(Map.of(
				"type", messageType(content),
				"from", fromNumber,
				"content", content,
				"messages", List.of(Map.of("to", phoneNumber))
			))
			.retrieve()
			.toBodilessEntity();
	}

	private String messageType(String content) {
		if (content.getBytes(StandardCharsets.UTF_8).length <= SMS_MAX_BYTES) {
			return "SMS";
		}
		return "LMS";
	}

	private String signature(String timestamp, String uri) {
		String message = "POST %s\n%s\n%s".formatted(uri, timestamp, accessKey);
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return Base64.getEncoder().encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to sign SENS SMS request");
		}
	}
}
