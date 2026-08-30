package com.dropshipshop.api.payment.domain;

public enum PaymentCommandType {
	CONFIRM_BANK_TRANSFER_DEPOSIT,
	RECORD_AMOUNT_MISMATCH,
	RECORD_LATE_DEPOSIT,
	COMPLETE_RECEIVED_EXCEPTION_REFUND
}
