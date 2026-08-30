package com.dropshipshop.api.checkout;

import org.springframework.http.HttpStatus;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;

final class SupplierContractExpiredCheckoutException extends ApiErrorException {

	SupplierContractExpiredCheckoutException() {
		super(
			HttpStatus.BAD_REQUEST,
			ApiErrorCode.CONTRACT_NOT_VERIFIED,
			"Supplier contract expired; checkout was not created"
		);
	}
}
