package com.dropshipshop.api.fulfillment.domain;

public enum SupplierPurchaseStatus {
	READY,
	PROCESSING,
	RECONCILIATION_REQUIRED,
	ORDERED,
	FAILED,
	CANCEL_REQUESTED,
	CANCELLED
}
