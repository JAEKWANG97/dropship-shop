package com.dropshipshop.api.account;

interface SmsSender {

	void sendVerificationCode(String phoneNumber, String code);
}
