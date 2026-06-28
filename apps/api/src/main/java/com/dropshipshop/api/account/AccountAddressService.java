package com.dropshipshop.api.account;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.account.domain.UserAddress;
import com.dropshipshop.api.account.repository.UserAddressRepository;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@Service
public class AccountAddressService {

	private final UserAddressRepository userAddressRepository;
	private final UserAccountRepository userAccountRepository;

	AccountAddressService(
		UserAddressRepository userAddressRepository,
		UserAccountRepository userAccountRepository
	) {
		this.userAddressRepository = userAddressRepository;
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional(readOnly = true)
	public AccountAddressDtos.AddressListResponse listAddresses(UUID userId) {
		return new AccountAddressDtos.AddressListResponse(
			userAddressRepository.findAllByUser_IdOrderByDefaultAddressDescCreatedAtDesc(userId)
				.stream()
				.map(this::toResponse)
				.toList()
		);
	}

	@Transactional
	public AccountAddressDtos.AddressResponse createAddress(UUID userId, AccountAddressDtos.AddressRequest request) {
		UserAccount user = userAccountRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		boolean defaultAddress = request.defaultAddress() || !userAddressRepository.existsByUser_Id(userId);
		UserAddress address = userAddressRepository.save(new UserAddress(
			user,
			request.recipientName(),
			request.recipientPhone(),
			request.postalCode(),
			request.address1(),
			request.address2(),
			defaultAddress
		));
		if (defaultAddress) {
			clearOtherDefaults(userId, address.getId());
		}
		return toResponse(address);
	}

	@Transactional
	public AccountAddressDtos.AddressResponse updateAddress(
		UUID userId,
		UUID addressId,
		AccountAddressDtos.AddressRequest request
	) {
		UserAddress address = findUserAddress(userId, addressId);
		address.update(
			request.recipientName(),
			request.recipientPhone(),
			request.postalCode(),
			request.address1(),
			request.address2(),
			request.defaultAddress()
		);
		if (request.defaultAddress()) {
			clearOtherDefaults(userId, address.getId());
		} else if (!userAddressRepository.existsByUser_IdAndDefaultAddressTrue(userId)) {
			address.setDefaultAddress(true);
		}
		return toResponse(address);
	}

	@Transactional
	public void deleteAddress(UUID userId, UUID addressId) {
		UserAddress address = findUserAddress(userId, addressId);
		boolean wasDefault = address.isDefaultAddress();
		userAddressRepository.delete(address);
		userAddressRepository.flush();
		if (wasDefault) {
			userAddressRepository.findFirstByUser_IdOrderByCreatedAtDesc(userId)
				.ifPresent(nextDefault -> nextDefault.setDefaultAddress(true));
		}
	}

	private UserAddress findUserAddress(UUID userId, UUID addressId) {
		return userAddressRepository.findByIdAndUser_Id(addressId, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
	}

	private void clearOtherDefaults(UUID userId, UUID defaultAddressId) {
		List<UserAddress> addresses = userAddressRepository.findAllByUser_IdOrderByDefaultAddressDescCreatedAtDesc(userId);
		for (UserAddress address : addresses) {
			if (!address.getId().equals(defaultAddressId) && address.isDefaultAddress()) {
				address.setDefaultAddress(false);
			}
		}
	}

	private AccountAddressDtos.AddressResponse toResponse(UserAddress address) {
		return new AccountAddressDtos.AddressResponse(
			address.getId(),
			address.getRecipientName(),
			address.getRecipientPhone(),
			address.getPostalCode(),
			address.getAddress1(),
			address.getAddress2(),
			address.isDefaultAddress(),
			address.getCreatedAt(),
			address.getUpdatedAt()
		);
	}
}
