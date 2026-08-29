package com.dropshipshop.api.supplierportal;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;

@Component
public class SupplierPortalInputPolicy {

	private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,200}");
	private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
	private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\d[ -]?){8,13}(?!\\d)");
	private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.)");
	private static final Pattern CUSTOMER_IDENTIFIER = Pattern.compile(
		"(?i)(?:customer|recipient|order[-_ ]?number|고객|수령인|주문번호|주소|배송지)"
	);

	public String requireIdempotencyKey(String value) {
		if (value == null || !IDEMPOTENCY_KEY.matcher(value).matches()) {
			throw new ApiErrorException(
				HttpStatus.BAD_REQUEST,
				ApiErrorCode.VALIDATION_FAILED,
				"Idempotency-Key must contain 8 to 200 safe characters"
			);
		}
		return value;
	}

	public String requirePiiFreeReason(String value, int maxLength) {
		if (value == null || value.isBlank() || value.length() > maxLength
			|| value.contains("\n") || value.contains("\r")
			|| EMAIL.matcher(value).find() || PHONE.matcher(value).find()
			|| URL.matcher(value).find() || CUSTOMER_IDENTIFIER.matcher(value).find()) {
			throw new ApiErrorException(
				HttpStatus.BAD_REQUEST,
				ApiErrorCode.VALIDATION_FAILED,
				"Reason must be a single-line PII-free operational description"
			);
		}
		return value.trim();
	}
}
