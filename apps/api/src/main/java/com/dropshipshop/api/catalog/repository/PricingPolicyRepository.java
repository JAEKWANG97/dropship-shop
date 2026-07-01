package com.dropshipshop.api.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.catalog.domain.PricingPolicy;

public interface PricingPolicyRepository extends JpaRepository<PricingPolicy, UUID> {

	Optional<PricingPolicy> findFirstByActiveTrueOrderByCreatedAtAsc();
}
