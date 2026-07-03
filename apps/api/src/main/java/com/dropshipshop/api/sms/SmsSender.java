package com.dropshipshop.api.sms;

public interface SmsSender {

	void sendVerificationCode(String phoneNumber, String code);

	SmsSendResult sendTransactional(String phoneNumber, String message);
}
