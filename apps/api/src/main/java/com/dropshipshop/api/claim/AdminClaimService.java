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
import com.dropshipshop.api.order.domain.AdminOrderActionHistory;
import com.dropshipshop.api.order.domain.AdminOrderActionType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.OrderStatusHistory;
import com.dropshipshop.api.order.repository.AdminOrderActionHistoryRepository;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.refund.RefundService;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.fulfillment.SupplierFulfillmentHandoverService;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;

@Service
class AdminClaimService {

	private final ClaimRepository claimRepository;
	private final CustomerOrderRepository orderRepository;
	private final CustomerClaimService customerClaimService;
	private final RefundService refundService;
	private final NotificationService notificationService;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;
	private final OrderStatusHistoryRepository statusHistoryRepository;
	private final SupplierFulfillmentHandoverService handoverService;
	private final ShipmentRepository shipmentRepository;

	AdminClaimService(
		ClaimRepository claimRepository,
		CustomerOrderRepository orderRepository,
		CustomerClaimService customerClaimService,
		RefundService refundService,
		NotificationService notificationService,
		AdminOrderActionHistoryRepository actionHistoryRepository,
		OrderStatusHistoryRepository statusHistoryRepository,
		SupplierFulfillmentHandoverService handoverService,
		ShipmentRepository shipmentRepository
	) {
		this.claimRepository = claimRepository;
		this.orderRepository = orderRepository;
		this.customerClaimService = customerClaimService;
		this.refundService = refundService;
		this.notificationService = notificationService;
		this.actionHistoryRepository = actionHistoryRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.handoverService = handoverService;
		this.shipmentRepository = shipmentRepository;
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
			if (claim.getClaimType() == ClaimType.CANCEL
				&& shipmentRepository.countNonVoided(claim.getOrder().getId()) > 0) {
				throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Active tracking must be voided before approving cancellation");
			}
			if (claim.getClaimType() == ClaimType.RETURN) {
				claim.approveReturn(adminUserId, request.reason(), Instant.now());
			} else {
				claim.approve(adminUserId, request.reason(), Instant.now());
			}
			if (claim.getClaimType() == ClaimType.CANCEL) {
				handoverService.takeOverTerminal(claim.getOrder(), Instant.now());
				claim.getOrder().markRefundRequested();
				refundService.createCustomerCancelRefund(claim.getOrder());
			}
			notificationService.transactionalSms(
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
		Claim claim = findClaim(claimId);
		try {
			if (claim.getClaimType() == ClaimType.RETURN
				&& (claim.getStatus() == ClaimStatus.RETURN_WAITING || claim.getStatus() == ClaimStatus.RETURN_RECEIVED)) {
				OrderStatus beforeStatus = claim.getOrder().getStatus();
				claim.rejectReturnAfterApproval(adminUserId, request.reason(), Instant.now());
				recordAdminTransition(
					claim.getOrder(),
					adminUserId,
					AdminOrderActionType.RETURN_REJECTED,
					beforeStatus,
					claim.getOrder().getStatus(),
					"Return rejected after approval",
					request.reason()
				);
			} else {
				claim.reject(adminUserId, request.reason(), Instant.now());
			}
			notificationService.transactionalSms(
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
	ClaimDtos.ClaimResponse recordReturnReceived(
		UUID claimId,
		UUID adminUserId,
		ClaimDtos.AdminReturnReceivedRequest request
	) {
		Claim claim = findClaim(claimId);
		try {
			OrderStatus beforeStatus = claim.getOrder().getStatus();
			claim.markReturnReceived(adminUserId, request.memo(), Instant.now());
			recordAdminTransition(
				claim.getOrder(),
				adminUserId,
				AdminOrderActionType.RETURN_RECEIVED,
				beforeStatus,
				claim.getOrder().getStatus(),
				"Return product received and inspected",
				request.memo()
			);
			notifyClaimChanged(claim, null);
			return customerClaimService.toResponse(claim);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@Transactional
	ClaimDtos.ClaimResponse startReturnRefund(
		UUID claimId,
		UUID adminUserId,
		ClaimDtos.AdminClaimReviewRequest request
	) {
		Claim claim = findClaim(claimId);
		try {
			if (claim.getClaimType() != ClaimType.RETURN || claim.getStatus() != ClaimStatus.RETURN_RECEIVED) {
				throw new IllegalStateException("Return refund can start only after return received");
			}
			CustomerOrder order = claim.getOrder();
			OrderStatus beforeStatus = order.getStatus();
			handoverService.takeOverTerminal(order, Instant.now());
			order.markRefundRequested();
			Refund refund = refundService.createReturnRefund(order);
			claim.markRefundProcessing(refund, adminUserId, request.reason(), Instant.now());
			recordAdminTransition(
				order,
				adminUserId,
				AdminOrderActionType.RETURN_REFUND_REQUESTED,
				beforeStatus,
				order.getStatus(),
				"Return refund requested",
				request.reason()
			);
			notifyClaimChanged(claim, refund);
			return customerClaimService.toResponse(claim);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	private Claim findReviewableClaim(UUID claimId) {
		Claim claim = findClaim(claimId);
		if (claim.getStatus() == ClaimStatus.APPROVED || claim.getStatus() == ClaimStatus.REJECTED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Claim has already been reviewed");
		}
		return claim;
	}

	private Claim findClaim(UUID claimId) {
		UUID orderId = claimRepository.findOrderIdById(claimId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
		CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
		Claim claim = claimRepository.findByIdForUpdate(claimId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
		if (!claim.getOrder().getId().equals(order.getId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found");
		}
		return claim;
	}

	private void recordAdminTransition(
		CustomerOrder order,
		UUID adminUserId,
		AdminOrderActionType actionType,
		OrderStatus beforeStatus,
		OrderStatus afterStatus,
		String sideEffectSummary,
		String reason
	) {
		actionHistoryRepository.save(new AdminOrderActionHistory(
			order,
			adminUserId,
			actionType,
			beforeStatus,
			afterStatus,
			reason
		));
		statusHistoryRepository.save(new OrderStatusHistory(
			order,
			adminUserId,
			actionType.name(),
			beforeStatus,
			afterStatus,
			"ALLOWED",
			sideEffectSummary,
			reason
		));
	}

	private void notifyClaimChanged(Claim claim, Refund refund) {
		notificationService.transactionalSms(
			claim.getUser(),
			claim.getOrder(),
			claim.getOrder().getPaymentGroup(),
			claim,
			refund,
			NotificationType.CLAIM_STATUS_CHANGED
		);
	}
}
