package com.dropshipshop.api.procurement;

class DomeggookApiException extends RuntimeException {

	private final String code;
	private final boolean outcomeUnknown;

	DomeggookApiException(String code, String message, boolean outcomeUnknown) {
		super(message);
		this.code = code;
		this.outcomeUnknown = outcomeUnknown;
	}

	String code() {
		return code;
	}

	boolean outcomeUnknown() {
		return outcomeUnknown;
	}
}
