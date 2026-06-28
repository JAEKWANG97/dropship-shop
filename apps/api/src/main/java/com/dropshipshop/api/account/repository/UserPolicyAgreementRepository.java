package com.dropshipshop.api.account.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.account.domain.UserPolicyAgreement;

public interface UserPolicyAgreementRepository extends JpaRepository<UserPolicyAgreement, UUID> {

	Optional<UserPolicyAgreement> findFirstByUser_IdOrderByAgreedAtDesc(UUID userId);

	Optional<UserPolicyAgreement> findByUser_IdAndTermsVersionAndPrivacyVersion(
		UUID userId,
		String termsVersion,
		String privacyVersion
	);

	boolean existsByUser_IdAndTermsVersionAndPrivacyVersion(
		UUID userId,
		String termsVersion,
		String privacyVersion
	);
}
