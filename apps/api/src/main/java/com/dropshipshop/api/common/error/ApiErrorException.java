package com.dropshipshop.api.common.error;

import org.springframework.http.HttpStatus;

public class ApiErrorException extends RuntimeException {

	private final HttpStatus status;
	private final ApiErrorCode code;

	public ApiErrorException(HttpStatus status, ApiErrorCode code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public ApiErrorCode getCode() {
		return code;
	}
}
