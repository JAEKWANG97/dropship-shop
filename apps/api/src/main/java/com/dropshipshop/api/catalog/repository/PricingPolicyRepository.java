package com.dropshipshop.api.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.dropshipshop.api.catalog.domain.PricingPolicy;

public interface PricingPolicyRepository extends JpaRepository<PricingPolicy, UUID> {

	Optional<PricingPolicy> findFirstByActiveTrueOrderByCreatedAtAsc();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select policy from PricingPolicy policy where policy.active = true order by policy.createdAt")
	Optional<PricingPolicy> findActiveForUpdate();
}
