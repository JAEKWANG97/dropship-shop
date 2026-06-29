package com.dropshipshop.api.account;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(name = "sms.sens.enabled", havingValue = "true")
class NaverSensSmsSender implements SmsSender {

	private final String accessKey;
	private final String secretKey;
	private final String serviceId;
	private final String fromNumber;
	private final RestClient restClient;

	NaverSensSmsSender(
		@Value("${sms.sens.access-key}") String accessKey,
		@Value("${sms.sens.secret-key}") String secretKey,
		@Value("${sms.sens.service-id}") String serviceId,
		@Value("${sms.sens.from-number}") String fromNumber,
		@Value("${sms.sens.base-url:https://sens.apigw.ntruss.com}") String baseUrl
	) {
		this.accessKey = accessKey;
		this.secretKey = secretKey;
		this.serviceId = serviceId;
		this.fromNumber = fromNumber;
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
	}

	@Override
	public void sendVerificationCode(String phoneNumber, String code) {
		String uri = "/sms/v2/services/%s/messages".formatted(serviceId);
		String timestamp = String.valueOf(Instant.now().toEpochMilli());
		try {
			restClient.post()
				.uri(uri)
				.header("x-ncp-apigw-timestamp", timestamp)
				.header("x-ncp-iam-access-key", accessKey)
				.header("x-ncp-apigw-signature-v2", signature(timestamp, uri))
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.body(Map.of(
					"type", "SMS",
					"from", fromNumber,
					"content", "[코어블SAF] 인증번호 %s를 입력해 주세요.".formatted(code),
					"messages", List.of(Map.of("to", phoneNumber))
				))
				.retrieve()
				.toBodilessEntity();
		} catch (RuntimeException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SMS verification send failed");
		}
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
