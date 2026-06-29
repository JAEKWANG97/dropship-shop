package com.dropshipshop.api.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "sms.sens.enabled", havingValue = "false", matchIfMissing = true)
class LoggingSmsSender implements SmsSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

	@Override
	public void sendVerificationCode(String phoneNumber, String code) {
		log.warn("Development SMS verification code for {} is {}. Configure SENS before production.", phoneNumber, code);
	}
}
