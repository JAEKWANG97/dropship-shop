package com.dropshipshop.api.claim;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.refund.RefundService;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;

@Service
class CustomerClaimService {

	private static final EnumSet<ClaimStatus> ACTIVE_CLAIM_STATUSES = EnumSet.of(
		ClaimStatus.REQUESTED,
		ClaimStatus.UNDER_REVIEW,
		ClaimStatus.APPROVED,
		ClaimStatus.EVIDENCE_REQUESTED,
		ClaimStatus.RETURN_WAITING,
		ClaimStatus.RETURN_RECEIVED,
		ClaimStatus.EXCHANGE_SHIPPING,
		ClaimStatus.REFUND_PROCESSING
	);

	private final CustomerOrderRepository orderRepository;
	private final ClaimRepository claimRepository;
	private final RefundService refundService;
	private final ShipmentRepository shipmentRepository;
	private final NotificationService notificationService;

	CustomerClaimService(
		CustomerOrderRepository orderRepository,
		ClaimRepository claimRepository,
		RefundService refundService,
		ShipmentRepository shipmentRepository,
		NotificationService notificationService
	) {
		this.orderRepository = orderRepository;
		this.claimRepository = claimRepository;
		this.refundService = refundService;
		this.shipmentRepository = shipmentRepository;
		this.notificationService = notificationService;
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
		notificationService.email(order.getUser(), order, order.getPaymentGroup(), claim, null, NotificationType.CLAIM_STATUS_CHANGED);
		return toResponse(claim);
	}

	@Transactional
	ClaimDtos.ClaimResponse createClaim(UUID userId, UUID orderId, ClaimDtos.CustomerClaimRequest request) {
		CustomerOrder order = findCustomerOrder(userId, orderId);
		if (request.claimType() == ClaimType.CANCEL) {
			return createCancellationClaim(order, request);
		}
		return createReturnExchangeClaim(order, request);
	}

	private ClaimDtos.ClaimResponse createCancellationClaim(CustomerOrder order, ClaimDtos.CustomerClaimRequest request) {
		if (!order.canRequestCancellationClaim()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not eligible for cancellation claim");
		}
		rejectDuplicateClaim(order, ClaimType.CANCEL);
		Claim claim = claimRepository.save(new Claim(
			order,
			order.getUser(),
			ClaimType.CANCEL,
			request.claimReason(),
			ClaimStatus.REQUESTED,
			RequestedAction.REFUND,
			request.customerMemo()
		));
		notificationService.email(order.getUser(), order, order.getPaymentGroup(), claim, null, NotificationType.CLAIM_STATUS_CHANGED);
		return toResponse(claim);
	}

	private ClaimDtos.ClaimResponse createReturnExchangeClaim(CustomerOrder order, ClaimDtos.CustomerClaimRequest request) {
		if (order.getStatus() != OrderStatus.DELIVERED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return or exchange claim is allowed only after delivery");
		}
		Shipment shipment = shipmentRepository.findByOrder_Id(order.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivered shipment not found"));
		validateReturnExchangeWindow(request.claimReason(), shipment.getDeliveredAt());
		rejectDuplicateClaim(order, request.claimType());
		Claim claim = claimRepository.save(new Claim(
			order,
			order.getUser(),
			request.claimType(),
			request.claimReason(),
			ClaimStatus.REQUESTED,
			request.claimType() == ClaimType.RETURN ? RequestedAction.REFUND : RequestedAction.EXCHANGE,
			request.customerMemo()
		));
		notificationService.email(order.getUser(), order, order.getPaymentGroup(), claim, null, NotificationType.CLAIM_STATUS_CHANGED);
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
		rejectDuplicateClaim(order, ClaimType.CANCEL);
	}

	private void rejectDuplicateClaim(CustomerOrder order, ClaimType claimType) {
		if (claimRepository.existsByOrder_IdAndClaimTypeAndStatusIn(
			order.getId(),
			claimType,
			ACTIVE_CLAIM_STATUSES
		)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Active claim already exists");
		}
	}

	private void validateReturnExchangeWindow(ClaimReason reason, Instant deliveredAt) {
		if (deliveredAt == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivered time is required for return or exchange claim");
		}
		Instant now = Instant.now();
		if (reason == ClaimReason.SIMPLE_CHANGE_OF_MIND && deliveredAt.isBefore(now.minus(7, ChronoUnit.DAYS))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simple change-of-mind claim period has expired");
		}
		if (reason != ClaimReason.SIMPLE_CHANGE_OF_MIND && deliveredAt.isBefore(now.minus(90, ChronoUnit.DAYS))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seller-fault claim period has expired");
		}
	}
}
