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
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.refund.RefundService;

@Service
class AdminClaimService {

	private final ClaimRepository claimRepository;
	private final CustomerClaimService customerClaimService;
	private final RefundService refundService;
	private final NotificationService notificationService;

	AdminClaimService(
		ClaimRepository claimRepository,
		CustomerClaimService customerClaimService,
		RefundService refundService,
		NotificationService notificationService
	) {
		this.claimRepository = claimRepository;
		this.customerClaimService = customerClaimService;
		this.refundService = refundService;
		this.notificationService = notificationService;
	}

	@Transactional(readOnly = true)
	ClaimDtos.AdminClaimListResponse listClaims() {
		return new ClaimDtos.AdminClaimListResponse(
			claimRepository.findAllByOrderByCreatedAtAsc()
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
		Claim claim = findReviewableClaim(claimId);
		try {
			if (claim.getClaimType() == ClaimType.RETURN) {
				claim.approveReturn(adminUserId, request.reason(), Instant.now());
			} else {
				claim.approve(adminUserId, request.reason(), Instant.now());
			}
			if (claim.getClaimType() == ClaimType.CANCEL) {
				claim.getOrder().markRefundRequested();
				refundService.createCustomerCancelRefund(claim.getOrder());
			}
			notificationService.email(
				claim.getUser(),
				claim.getOrder(),
				claim.getOrder().getPaymentGroup(),
				claim,
				null,
				NotificationType.CLAIM_STATUS_CHANGED
			);
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
		Claim claim = findReviewableClaim(claimId);
		try {
			claim.reject(adminUserId, request.reason(), Instant.now());
			notificationService.email(
				claim.getUser(),
				claim.getOrder(),
				claim.getOrder().getPaymentGroup(),
				claim,
				null,
				NotificationType.CLAIM_STATUS_CHANGED
			);
			return customerClaimService.toResponse(claim);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	private Claim findReviewableClaim(UUID claimId) {
		Claim claim = claimRepository.findById(claimId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
		if (claim.getStatus() == ClaimStatus.APPROVED || claim.getStatus() == ClaimStatus.REJECTED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Claim has already been reviewed");
		}
		return claim;
	}
}
