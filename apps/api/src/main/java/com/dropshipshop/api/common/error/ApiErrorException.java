package com.dropshipshop.api.common.error;

import org.springframework.http.HttpStatus;

public class ApiErrorException extends RuntimeException {

	private final HttpStatus status;
	private final ApiErrorCode code;
	private final Object details;

	public ApiErrorException(HttpStatus status, ApiErrorCode code, String message) {
		this(status, code, message, null);
	}

	public ApiErrorException(HttpStatus status, ApiErrorCode code, String message, Object details) {
		super(message);
		this.status = status;
		this.code = code;
		this.details = details;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public ApiErrorCode getCode() {
		return code;
	}

	public Object getDetails() {
		return details;
	}
}
