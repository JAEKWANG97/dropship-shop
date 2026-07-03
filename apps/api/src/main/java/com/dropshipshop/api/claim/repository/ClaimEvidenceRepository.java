package com.dropshipshop.api.claim.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.claim.domain.ClaimEvidence;

public interface ClaimEvidenceRepository extends JpaRepository<ClaimEvidence, UUID> {

	List<ClaimEvidence> findAllByClaim_IdOrderByUploadedAtAsc(UUID claimId);

	List<ClaimEvidence> findAllByClaim_IdInOrderByUploadedAtAsc(Collection<UUID> claimIds);
}
