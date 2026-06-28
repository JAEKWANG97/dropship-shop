package com.dropshipshop.api.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserStatus;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	Optional<UserAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	Optional<UserAccount> findByIdAndStatus(UUID id, UserStatus status);
}
