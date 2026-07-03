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

	List<Claim> findAllByOrderByCreatedAtAsc();

	List<Claim> findTop5ByUser_IdAndStatusInOrderByCreatedAtDesc(UUID userId, Collection<ClaimStatus> statuses);

	List<Claim> findAllByOrder_IdOrderByCreatedAtAsc(UUID orderId);

	Optional<Claim> findByIdAndUser_Id(UUID id, UUID userId);

	Optional<Claim> findFirstByOrder_IdOrderByCreatedAtDesc(UUID orderId);

	Optional<Claim> findByRefund_Id(UUID refundId);

	boolean existsByOrder_IdAndClaimTypeAndStatusIn(
		UUID orderId,
		ClaimType claimType,
		Collection<ClaimStatus> statuses
	);
}
