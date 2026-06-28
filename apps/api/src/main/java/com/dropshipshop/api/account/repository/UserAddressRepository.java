package com.dropshipshop.api.account.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.account.domain.UserAddress;

public interface UserAddressRepository extends JpaRepository<UserAddress, UUID> {

	List<UserAddress> findAllByUser_IdOrderByDefaultAddressDescCreatedAtDesc(UUID userId);

	Optional<UserAddress> findByIdAndUser_Id(UUID id, UUID userId);

	Optional<UserAddress> findFirstByUser_IdOrderByCreatedAtDesc(UUID userId);

	boolean existsByUser_Id(UUID userId);

	boolean existsByUser_IdAndDefaultAddressTrue(UUID userId);
}
