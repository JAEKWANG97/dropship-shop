package com.dropshipshop.api.claim;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.domain.ClaimType;
import com.dropshipshop.api.claim.repository.ClaimRepository;

@Service
class AdminClaimService {

	private final ClaimRepository claimRepository;
	private final CustomerClaimService customerClaimService;

	AdminClaimService(ClaimRepository claimRepository, CustomerClaimService customerClaimService) {
		this.claimRepository = claimRepository;
		this.customerClaimService = customerClaimService;
	}

	@Transactional(readOnly = true)
	ClaimDtos.AdminClaimListResponse listCancellationClaims() {
		return new ClaimDtos.AdminClaimListResponse(
			claimRepository.findAllByClaimTypeOrderByCreatedAtAsc(ClaimType.CANCEL)
				.stream()
				.map(customerClaimService::toResponse)
				.toList()
		);
	}

	@Transactional
	ClaimDtos.ClaimResponse approveCancellationClaim(
		UUID claimId,
		UUID adminUserId,
		ClaimDtos.AdminClaimReviewRequest request
	) {
		Claim claim = findCancellationClaim(claimId);
		try {
			claim.approve(adminUserId, request.reason(), Instant.now());
			claim.getOrder().markRefundRequested();
			return customerClaimService.toResponse(claim);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@Transactional
	ClaimDtos.ClaimResponse rejectCancellationClaim(
		UUID claimId,
		UUID adminUserId,
		ClaimDtos.AdminClaimReviewRequest request
	) {
		Claim claim = findCancellationClaim(claimId);
		try {
			claim.reject(adminUserId, request.reason(), Instant.now());
			return customerClaimService.toResponse(claim);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	private Claim findCancellationClaim(UUID claimId) {
		Claim claim = claimRepository.findById(claimId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
		if (claim.getClaimType() != ClaimType.CANCEL) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only cancellation claims are supported");
		}
		if (claim.getStatus() == ClaimStatus.APPROVED || claim.getStatus() == ClaimStatus.REJECTED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Claim has already been reviewed");
		}
		return claim;
	}
}
