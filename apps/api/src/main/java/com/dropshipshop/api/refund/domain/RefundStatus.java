package com.dropshipshop.api.refund.domain;

public enum RefundStatus {
	REQUESTED,
	APPROVED,
	PG_CANCEL_REQUESTED,
	PROCESSING,
	COMPLETED,
	FAILED,
	RETRY_REQUIRED,
	REJECTED,
	MANUAL_REVIEW_REQUIRED
}
