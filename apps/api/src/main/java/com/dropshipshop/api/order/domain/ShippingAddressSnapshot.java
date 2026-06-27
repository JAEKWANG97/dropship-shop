package com.dropshipshop.api.order.domain;

public record ShippingAddressSnapshot(
	String recipientName,
	String recipientPhone,
	String postalCode,
	String address1,
	String address2
) {
}
