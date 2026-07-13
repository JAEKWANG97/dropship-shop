package com.dropshipshop.api.email;

public record EmailSendResult(boolean successful, String skippedReason) {

	public static EmailSendResult sent() {
		return new EmailSendResult(true, null);
	}

	public static EmailSendResult skipped(String reason) {
		return new EmailSendResult(false, reason);
	}
}
