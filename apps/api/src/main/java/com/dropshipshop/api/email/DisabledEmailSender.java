package com.dropshipshop.api.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.email.ses.enabled", havingValue = "false", matchIfMissing = true)
class DisabledEmailSender implements EmailSender {

	@Override
	public EmailSendResult sendTransactional(String recipient, String subject, String body) {
		return EmailSendResult.skipped("AWS SES is disabled");
	}
}
