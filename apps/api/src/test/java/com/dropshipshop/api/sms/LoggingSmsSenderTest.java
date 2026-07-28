package com.dropshipshop.api.sms;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

class LoggingSmsSenderTest {

	@Test
	void productionDoesNotExposeVerificationCodeWhenSensIsDisabled() {
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles("prod");
		LoggingSmsSender sender = new LoggingSmsSender(environment);

		assertThatThrownBy(() -> sender.sendVerificationCode("01012345678", "123456"))
			.isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("SMS verification is not configured");
	}
}
