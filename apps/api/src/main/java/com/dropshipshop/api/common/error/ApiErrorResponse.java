package com.dropshipshop.api.common.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiErrorResponse(
	Instant timestamp,
	int status,
	String code,
	String message,
	String path,
	List<FieldErrorResponse> fields,
	@JsonInclude(JsonInclude.Include.NON_NULL) Object details
) {

	public static ApiErrorResponse of(
		int status,
		ApiErrorCode code,
		String message,
		String path
	) {
		return of(status, code, message, path, null);
	}

	public static ApiErrorResponse of(
		int status,
		ApiErrorCode code,
		String message,
		String path,
		Object details
	) {
		return new ApiErrorResponse(Instant.now(), status, code.name(), message, path, List.of(), details);
	}

	public static ApiErrorResponse validation(
		int status,
		String message,
		String path,
		List<FieldErrorResponse> fields
	) {
		return new ApiErrorResponse(
			Instant.now(), status, ApiErrorCode.VALIDATION_FAILED.name(), message, path, fields, null
		);
	}

	public record FieldErrorResponse(
		String field,
		String message
	) {
	}
}
