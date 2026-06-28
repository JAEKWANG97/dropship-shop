package com.dropshipshop.api.account;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class AccountAddressDtos {

	private AccountAddressDtos() {
	}

	record AddressRequest(
		@NotBlank @Size(max = 100) String recipientName,
		@NotBlank @Size(max = 30) String recipientPhone,
		@NotBlank @Size(max = 20) String postalCode,
		@NotBlank @Size(max = 300) String address1,
		@Size(max = 300) String address2,
		boolean defaultAddress
	) {
	}

	record AddressListResponse(
		List<AddressResponse> addresses
	) {
	}

	record AddressResponse(
		UUID id,
		String recipientName,
		String recipientPhone,
		String postalCode,
		String address1,
		String address2,
		boolean defaultAddress,
		Instant createdAt,
		Instant updatedAt
	) {
	}
}
