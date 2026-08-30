package com.dropshipshop.api.order.domain;

public record ShippingAddressSnapshot(
	String recipientName,
	String recipientPhone,
	String postalCode,
	String address1,
	String address2,
	String deliveryMemo
) {
	public ShippingAddressSnapshot(
		String recipientName,
		String recipientPhone,
		String postalCode,
		String address1,
		String address2
	) {
		this(recipientName, recipientPhone, postalCode, address1, address2, null);
	}
}
