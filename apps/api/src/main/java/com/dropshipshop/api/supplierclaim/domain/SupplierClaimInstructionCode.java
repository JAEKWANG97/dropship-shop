package com.dropshipshop.api.supplierclaim.domain;

public enum SupplierClaimInstructionCode {
	CHECK_SHIPMENT_STOP(
		SupplierClaimRequestedType.SHIPMENT_STOP_RESULT,
		"상품 발송을 멈출 수 있는지 확인해 주세요."
	),
	PROVIDE_RETURN_METHOD(
		SupplierClaimRequestedType.RETURN_INSTRUCTIONS,
		"반품 수거 방법을 선택해 주세요."
	),
	CONFIRM_RETURN_RECEIPT(
		SupplierClaimRequestedType.RETURN_RECEIVED,
		"반품 상품 수령 여부를 확인해 주세요."
	),
	INSPECT_RETURNED_ITEM(
		SupplierClaimRequestedType.INSPECTION_RESULT,
		"반품 상품의 상태를 확인해 주세요."
	);

	private final SupplierClaimRequestedType requestedType;
	private final String instructions;

	SupplierClaimInstructionCode(SupplierClaimRequestedType requestedType, String instructions) {
		this.requestedType = requestedType;
		this.instructions = instructions;
	}

	public SupplierClaimRequestedType requestedType() {
		return requestedType;
	}

	public String instructions() {
		return instructions;
	}
}
