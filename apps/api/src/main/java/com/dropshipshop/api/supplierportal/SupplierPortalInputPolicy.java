package com.dropshipshop.api.supplierportal;

import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;

@Component
public class SupplierPortalInputPolicy {

	private static final Set<String> PORTAL_TAKEOVER_REASON_CODES = Set.of(
		"COREABLE_FULFILLMENT_TAKEOVER",
		"SUPPLIER_SUPPORT_REQUIRED",
		"OPERATIONAL_RISK"
	);
	private static final Set<String> PII_GRANT_REASON_CODES = Set.of(
		"RETURN_COORDINATION_REQUIRED",
		"EXCHANGE_COORDINATION_REQUIRED",
		"REFUND_COORDINATION_REQUIRED"
	);
	private static final Set<String> PII_REVOKE_REASON_CODES = Set.of(
		"CLAIM_ACCESS_NO_LONGER_REQUIRED"
	);
	private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,200}");
	private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
	private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\d[ -]?){8,13}(?!\\d)");
	private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.)");
	private static final Pattern POSTAL_ADDRESS = Pattern.compile(
		"(?iu)(?:\\b\\d{1,6}\\s+(?:[a-z0-9.'-]+\\s+){0,6}"
			+ "(?:street|st\\.?|road|rd\\.?|avenue|ave\\.?|boulevard|blvd\\.?|lane|ln\\.?|drive|dr\\.?|way|court|ct\\.?)\\b"
			+ "|(?:[가-힣]+(?:특별시|광역시|특별자치시|도)\\s+)?[가-힣]+(?:시|군|구)\\s+"
			+ "[가-힣0-9·.-]+(?:로|길|동)\\s*\\d+(?:-\\d+)?)"
	);
	private static final Pattern CUSTOMER_IDENTIFIER = Pattern.compile(
		"(?i)(?:customer|recipient|order[-_ ]?number|shipping[-_ ]?address|address|delivery[-_ ]?memo|"
			+ "고객|수령인|주문번호|주소|배송지|배송[-_ ]?메모)"
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
			|| URL.matcher(value).find() || POSTAL_ADDRESS.matcher(value).find()
			|| CUSTOMER_IDENTIFIER.matcher(value).find()) {
			throw new ApiErrorException(
				HttpStatus.BAD_REQUEST,
				ApiErrorCode.VALIDATION_FAILED,
				"Reason must be a single-line PII-free operational description"
			);
		}
		return value.trim();
	}

	public String requirePortalTakeoverReasonCode(String value) {
		return requireAllowedReasonCode(value, PORTAL_TAKEOVER_REASON_CODES);
	}

	public String requirePiiGrantReasonCode(String value) {
		return requireAllowedReasonCode(value, PII_GRANT_REASON_CODES);
	}

	public String requirePiiRevokeReasonCode(String value) {
		return requireAllowedReasonCode(value, PII_REVOKE_REASON_CODES);
	}

	private String requireAllowedReasonCode(String value, Set<String> allowedCodes) {
		String code = value == null ? null : value.trim();
		if (code == null || !allowedCodes.contains(code)) {
			throw new ApiErrorException(
				HttpStatus.BAD_REQUEST,
				ApiErrorCode.VALIDATION_FAILED,
				"Reason must be an allowlisted operational code"
			);
		}
		return code;
	}
}
