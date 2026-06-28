package com.dropshipshop.api.account;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/me/addresses")
@PreAuthorize("hasRole('CUSTOMER')")
class AccountAddressController {

	private final AccountAddressService accountAddressService;
	private final CurrentUser currentUser;

	AccountAddressController(AccountAddressService accountAddressService, CurrentUser currentUser) {
		this.accountAddressService = accountAddressService;
		this.currentUser = currentUser;
	}

	@GetMapping
	AccountAddressDtos.AddressListResponse listAddresses(Authentication authentication) {
		return accountAddressService.listAddresses(currentUser.id(authentication));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AccountAddressDtos.AddressResponse createAddress(
		@Valid @RequestBody AccountAddressDtos.AddressRequest request,
		Authentication authentication
	) {
		return accountAddressService.createAddress(currentUser.id(authentication), request);
	}

	@PatchMapping("/{addressId}")
	AccountAddressDtos.AddressResponse updateAddress(
		@PathVariable UUID addressId,
		@Valid @RequestBody AccountAddressDtos.AddressRequest request,
		Authentication authentication
	) {
		return accountAddressService.updateAddress(currentUser.id(authentication), addressId, request);
	}

	@DeleteMapping("/{addressId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteAddress(@PathVariable UUID addressId, Authentication authentication) {
		accountAddressService.deleteAddress(currentUser.id(authentication), addressId);
	}
}
