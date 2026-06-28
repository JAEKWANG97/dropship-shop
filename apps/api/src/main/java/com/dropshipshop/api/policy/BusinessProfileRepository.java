package com.dropshipshop.api.policy;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.policy.domain.BusinessProfile;

interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {

	Optional<BusinessProfile> findFirstByActiveTrueOrderByEffectiveFromDesc();
}
