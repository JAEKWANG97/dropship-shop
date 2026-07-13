package com.dropshipshop.api.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Component
@ConditionalOnProperty(name = "app.email.ses.enabled", havingValue = "true")
class SesEmailSender implements EmailSender {

	private final SesV2Client client;
	private final String fromAddress;

	SesEmailSender(
		@Value("${app.email.ses.region:ap-northeast-2}") String region,
		@Value("${app.email.from-address}") String fromAddress
	) {
		this.client = SesV2Client.builder().region(Region.of(region)).build();
		this.fromAddress = fromAddress;
	}

	@Override
	public EmailSendResult sendTransactional(String recipient, String subject, String body) {
		client.sendEmail(SendEmailRequest.builder()
			.fromEmailAddress(fromAddress)
			.replyToAddresses(fromAddress)
			.destination(Destination.builder().toAddresses(recipient).build())
			.content(EmailContent.builder().simple(Message.builder()
				.subject(content(subject))
				.body(Body.builder().text(content(body)).build())
				.build()).build())
			.build());
		return EmailSendResult.sent();
	}

	@PreDestroy
	void close() {
		client.close();
	}

	private Content content(String value) {
		return Content.builder().charset("UTF-8").data(value).build();
	}
}
