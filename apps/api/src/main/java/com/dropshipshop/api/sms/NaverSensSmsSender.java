package com.dropshipshop.api.sms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(name = "sms.sens.enabled", havingValue = "true")
class NaverSensSmsSender implements SmsSender {

	private final SensSmsClient sensSmsClient;

	NaverSensSmsSender(
		@Value("${sms.sens.access-key}") String accessKey,
		@Value("${sms.sens.secret-key}") String secretKey,
		@Value("${sms.sens.service-id}") String serviceId,
		@Value("${sms.sens.from-number}") String fromNumber,
		@Value("${sms.sens.base-url:https://sens.apigw.ntruss.com}") String baseUrl
	) {
		this.sensSmsClient = new SensSmsClient(accessKey, secretKey, serviceId, fromNumber, baseUrl);
	}

	@Override
	public void sendVerificationCode(String phoneNumber, String code) {
		try {
			sensSmsClient.send(phoneNumber, "[코어블SAF] 인증번호 %s를 입력해 주세요.".formatted(code));
		} catch (RuntimeException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SMS verification send failed");
		}
	}

	@Override
	public SmsSendResult sendTransactional(String phoneNumber, String message) {
		sensSmsClient.send(phoneNumber, message);
		return SmsSendResult.sent();
	}
}
