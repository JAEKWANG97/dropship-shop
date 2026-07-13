package com.dropshipshop.api.email;

public interface EmailSender {

	EmailSendResult sendTransactional(String recipient, String subject, String body);
}
