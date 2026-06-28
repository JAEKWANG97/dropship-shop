package com.dropshipshop.api.common.error;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
	Instant timestamp,
	int status,
	String code,
	String message,
	String path,
	List<FieldErrorResponse> fields
) {

	public static ApiErrorResponse of(
		int status,
		ApiErrorCode code,
		String message,
		String path
	) {
		return new ApiErrorResponse(Instant.now(), status, code.name(), message, path, List.of());
	}

	public static ApiErrorResponse validation(
		int status,
		String message,
		String path,
		List<FieldErrorResponse> fields
	) {
		return new ApiErrorResponse(Instant.now(), status, ApiErrorCode.VALIDATION_FAILED.name(), message, path, fields);
	}

	public record FieldErrorResponse(
		String field,
		String message
	) {
	}
}
