package com.dropshipshop.api.claim;

import java.util.EnumSet;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.claim.domain.ClaimReason;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.domain.ClaimType;
import com.dropshipshop.api.claim.domain.RequestedAction;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.refund.RefundService;

@Service
class CustomerClaimService {

	private static final EnumSet<ClaimStatus> ACTIVE_CLAIM_STATUSES = EnumSet.of(
		ClaimStatus.REQUESTED,
		ClaimStatus.UNDER_REVIEW,
		ClaimStatus.APPROVED,
		ClaimStatus.REFUND_PROCESSING
	);

	private final CustomerOrderRepository orderRepository;
	private final ClaimRepository claimRepository;
	private final RefundService refundService;

	CustomerClaimService(
		CustomerOrderRepository orderRepository,
		ClaimRepository claimRepository,
		RefundService refundService
	) {
		this.orderRepository = orderRepository;
		this.claimRepository = claimRepository;
		this.refundService = refundService;
	}

	@Transactional
	ClaimDtos.ClaimResponse selfServiceCancel(
		UUID userId,
		UUID orderId,
		ClaimDtos.CustomerCancelRequest request
	) {
		CustomerOrder order = findCustomerOrder(userId, orderId);
		if (!order.isSelfServiceCancellable()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not eligible for self-service cancellation");
		}
		rejectDuplicateCancellation(order);
		order.markRefundRequested();
		Claim claim = claimRepository.save(new Claim(
			order,
			order.getUser(),
			ClaimType.CANCEL,
			ClaimReason.SIMPLE_CHANGE_OF_MIND,
			ClaimStatus.APPROVED,
			RequestedAction.REFUND,
			request.reason()
		));
		refundService.createCustomerCancelRefund(order);
		return toResponse(claim);
	}

	@Transactional
	ClaimDtos.ClaimResponse createClaim(UUID userId, UUID orderId, ClaimDtos.CustomerClaimRequest request) {
		CustomerOrder order = findCustomerOrder(userId, orderId);
		if (request.claimType() != ClaimType.CANCEL) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only cancellation claims are supported in MVP");
		}
		if (!order.canRequestCancellationClaim()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not eligible for cancellation claim");
		}
		rejectDuplicateCancellation(order);
		Claim claim = claimRepository.save(new Claim(
			order,
			order.getUser(),
			ClaimType.CANCEL,
			request.claimReason(),
			ClaimStatus.REQUESTED,
			RequestedAction.REFUND,
			request.customerMemo()
		));
		return toResponse(claim);
	}

	ClaimDtos.ClaimResponse toResponse(Claim claim) {
		return new ClaimDtos.ClaimResponse(
			claim.getId(),
			claim.getOrder().getId(),
			claim.getOrder().getOrderNumber(),
			claim.getOrder().getStatus(),
			claim.getClaimType(),
			claim.getClaimReason(),
			claim.getStatus(),
			claim.getRequestedAction(),
			claim.getCustomerMemo(),
			claim.getReviewedByAdminId(),
			claim.getAdminReviewReason(),
			claim.getReviewedAt(),
			claim.getCreatedAt()
		);
	}

	private CustomerOrder findCustomerOrder(UUID userId, UUID orderId) {
		return orderRepository.findByIdAndUser_Id(orderId, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
	}

	private void rejectDuplicateCancellation(CustomerOrder order) {
		if (claimRepository.existsByOrder_IdAndClaimTypeAndStatusIn(
			order.getId(),
			ClaimType.CANCEL,
			ACTIVE_CLAIM_STATUSES
		)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancellation request already exists");
		}
	}
}
