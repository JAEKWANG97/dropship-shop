package com.dropshipshop.api.account.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.account.domain.PhoneVerificationCode;

public interface PhoneVerificationCodeRepository extends JpaRepository<PhoneVerificationCode, UUID> {

	Optional<PhoneVerificationCode> findFirstByUser_IdAndPhoneNumberOrderByCreatedAtDesc(UUID userId, String phoneNumber);
}
