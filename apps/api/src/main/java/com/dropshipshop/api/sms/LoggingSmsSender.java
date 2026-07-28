package com.dropshipshop.api.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(name = "sms.sens.enabled", havingValue = "false", matchIfMissing = true)
class LoggingSmsSender implements SmsSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);
	private static final String SKIPPED_REASON = "SMS SENS is disabled";
	private final Environment environment;

	LoggingSmsSender(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void sendVerificationCode(String phoneNumber, String code) {
		if (environment.acceptsProfiles(Profiles.of("prod"))) {
			log.warn("SMS verification skipped because SENS is disabled.");
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS verification is not configured");
		}
		log.warn("Development SMS verification code for {} is {}. Configure SENS before production.", phoneNumber, code);
	}

	@Override
	public SmsSendResult sendTransactional(String phoneNumber, String message) {
		log.warn("Transactional SMS skipped because SENS is disabled.");
		return SmsSendResult.skipped(SKIPPED_REASON);
	}
}
