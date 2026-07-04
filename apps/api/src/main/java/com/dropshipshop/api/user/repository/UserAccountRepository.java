package com.dropshipshop.api.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserStatus;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	Optional<UserAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	Optional<UserAccount> findByProviderAndProviderUserIdAndStatus(
		SocialProvider provider,
		String providerUserId,
		UserStatus status
	);

	Optional<UserAccount> findByIdAndStatus(UUID id, UserStatus status);

	boolean existsByReferralCode(String referralCode);

	Optional<UserAccount> findByReferralCode(String referralCode);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from UserAccount user where user.id = :id")
	Optional<UserAccount> findByIdForUpdate(@Param("id") UUID id);

	List<UserAccount> findAllByReferredByIsNotNullOrderByReferredAtDesc();
}
