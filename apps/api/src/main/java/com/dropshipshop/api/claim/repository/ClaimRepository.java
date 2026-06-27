package com.dropshipshop.api.claim.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.domain.ClaimType;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

	List<Claim> findAllByClaimTypeOrderByCreatedAtAsc(ClaimType claimType);

	Optional<Claim> findByIdAndUser_Id(UUID id, UUID userId);

	boolean existsByOrder_IdAndClaimTypeAndStatusIn(
		UUID orderId,
		ClaimType claimType,
		Collection<ClaimStatus> statuses
	);
}
