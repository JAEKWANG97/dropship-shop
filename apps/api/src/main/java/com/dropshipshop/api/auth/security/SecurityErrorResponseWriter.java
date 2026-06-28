package com.dropshipshop.api.auth.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.dropshipshop.api.common.error.ApiErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class SecurityErrorResponseWriter {

	private SecurityErrorResponseWriter() {
	}

	static void write(
		HttpServletRequest request,
		HttpServletResponse response,
		HttpStatus status,
		ApiErrorCode code,
		String message
	) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("""
			{"timestamp":"%s","status":%d,"code":"%s","message":"%s","path":"%s","fields":[]}
			""".formatted(
			Instant.now(),
			status.value(),
			code.name(),
			escape(message),
			escape(request.getRequestURI())
		));
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		return value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r");
	}
}
