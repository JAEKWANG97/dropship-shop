package com.dropshipshop.api.supplierclaim.domain;

public enum SupplierClaimTaskCloseReasonCode {
	RESPONSE_ACCEPTED,
	SUPERSEDED,
	NO_LONGER_NEEDED,
	DUE_AT_EXPIRED,
	CLAIM_TERMINAL;

	public boolean isAdminReason() {
		return this == RESPONSE_ACCEPTED || this == SUPERSEDED || this == NO_LONGER_NEEDED;
	}
}
