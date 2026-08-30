package com.dropshipshop.api.common.error;

import java.util.Comparator;
import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(ApiErrorException.class)
	ResponseEntity<ApiErrorResponse> handleApiErrorException(
		ApiErrorException exception,
		HttpServletRequest request
	) {
		return error(
			exception.getStatus(), exception.getCode(), message(exception.getMessage()), request, exception.getDetails()
		);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
		MethodArgumentNotValidException exception,
		HttpServletRequest request
	) {
		List<ApiErrorResponse.FieldErrorResponse> fields = exception.getBindingResult().getFieldErrors().stream()
			.map(error -> new ApiErrorResponse.FieldErrorResponse(error.getField(), message(error.getDefaultMessage())))
			.sorted(Comparator.comparing(ApiErrorResponse.FieldErrorResponse::field))
			.toList();

		ApiErrorResponse response = ApiErrorResponse.validation(
			HttpStatus.BAD_REQUEST.value(),
			"Request validation failed",
			request.getRequestURI(),
			fields
		);
		return ResponseEntity.badRequest().body(response);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiErrorResponse> handleConstraintViolation(
		ConstraintViolationException exception,
		HttpServletRequest request
	) {
		List<ApiErrorResponse.FieldErrorResponse> fields = exception.getConstraintViolations().stream()
			.map(violation -> new ApiErrorResponse.FieldErrorResponse(
				violation.getPropertyPath().toString(),
				message(violation.getMessage())
			))
			.sorted(Comparator.comparing(ApiErrorResponse.FieldErrorResponse::field))
			.toList();

		ApiErrorResponse response = ApiErrorResponse.validation(
			HttpStatus.BAD_REQUEST.value(),
			"Request validation failed",
			request.getRequestURI(),
			fields
		);
		return ResponseEntity.badRequest().body(response);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
		HttpMessageNotReadableException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, ApiErrorCode.MALFORMED_REQUEST, "Malformed request body", request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
		MethodArgumentTypeMismatchException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, ApiErrorCode.MALFORMED_REQUEST, "Malformed request parameter", request);
	}

	@ExceptionHandler(ResponseStatusException.class)
	ResponseEntity<ApiErrorResponse> handleResponseStatusException(
		ResponseStatusException exception,
		HttpServletRequest request
	) {
		HttpStatus status = httpStatus(exception.getStatusCode());
		return error(status, codeFor(status), message(exception.getReason(), status.getReasonPhrase()), request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiErrorResponse> handleNoResourceFound(
		NoResourceFoundException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Resource not found", request);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiErrorResponse> handleAccessDenied(
		AccessDeniedException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, "Access is denied", request);
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailure(
		OptimisticLockingFailureException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, "Order state was just changed. Please refresh and try again.", request);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
		DataIntegrityViolationException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, "Request conflicts with current data", request);
	}

	@ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
	ResponseEntity<ApiErrorResponse> handleBusinessRuleViolation(
		RuntimeException exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.BAD_REQUEST, ApiErrorCode.BUSINESS_RULE_VIOLATION, message(exception.getMessage()), request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpected(
		Exception exception,
		HttpServletRequest request
	) {
		return error(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_SERVER_ERROR, "Internal server error", request);
	}

	private ResponseEntity<ApiErrorResponse> error(
		HttpStatus status,
		ApiErrorCode code,
		String message,
		HttpServletRequest request
	) {
		return error(status, code, message, request, null);
	}

	private ResponseEntity<ApiErrorResponse> error(
		HttpStatus status,
		ApiErrorCode code,
		String message,
		HttpServletRequest request,
		Object details
	) {
		return ResponseEntity
			.status(status)
			.body(ApiErrorResponse.of(status.value(), code, message, request.getRequestURI(), details));
	}

	private ApiErrorCode codeFor(HttpStatus status) {
		return switch (status) {
			case BAD_REQUEST -> ApiErrorCode.BUSINESS_RULE_VIOLATION;
			case NOT_FOUND -> ApiErrorCode.RESOURCE_NOT_FOUND;
			case CONFLICT -> ApiErrorCode.CONFLICT;
			case TOO_MANY_REQUESTS -> ApiErrorCode.RATE_LIMITED;
			case BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT -> ApiErrorCode.UPSTREAM_SERVICE_ERROR;
			case UNAUTHORIZED -> ApiErrorCode.UNAUTHORIZED;
			case FORBIDDEN -> ApiErrorCode.FORBIDDEN;
			default -> status.is5xxServerError() ? ApiErrorCode.INTERNAL_SERVER_ERROR : ApiErrorCode.BUSINESS_RULE_VIOLATION;
		};
	}

	private HttpStatus httpStatus(HttpStatusCode statusCode) {
		return HttpStatus.valueOf(statusCode.value());
	}

	private String message(String value) {
		return message(value, "Request failed");
	}

	private String message(String value, String fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		return value;
	}
}
