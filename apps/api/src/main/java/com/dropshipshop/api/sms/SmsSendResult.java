package com.dropshipshop.api.sms;

public record SmsSendResult(
	boolean successful,
	String skippedReason
) {

	public static SmsSendResult sent() {
		return new SmsSendResult(true, null);
	}

	public static SmsSendResult skipped(String reason) {
		return new SmsSendResult(false, reason);
	}
}
