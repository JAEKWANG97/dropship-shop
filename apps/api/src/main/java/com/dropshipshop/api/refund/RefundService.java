package com.dropshipshop.api.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.checkout.CheckoutLockService;

import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.AdminOrderActionHistory;
import com.dropshipshop.api.order.domain.AdminOrderActionType;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.OrderStatusHistory;
import com.dropshipshop.api.order.repository.AdminOrderActionHistoryRepository;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentEvent;
import com.dropshipshop.api.payment.domain.PaymentEventType;
import com.dropshipshop.api.payment.domain.PaymentCommandType;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentProvider;
import com.dropshipshop.api.payment.repository.PaymentEventRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.refund.domain.RefundReason;
import com.dropshipshop.api.refund.domain.RefundStatus;
import com.dropshipshop.api.refund.domain.RefundScope;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.SupplierPortalInputPolicy;
import com.dropshipshop.api.fulfillment.SupplierFulfillmentHandoverService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class RefundService {

	private final RefundRepository refundRepository;
	private final ClaimRepository claimRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentEventRepository paymentEventRepository;
	private final CheckoutLockService checkoutLockService;
	private final CustomerOrderRepository orderRepository;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;
	private final OrderStatusHistoryRepository statusHistoryRepository;
	private final NotificationService notificationService;
	private final SupplierPortalHasher hasher;
	private final SupplierPortalInputPolicy inputPolicy;
	private final ObjectMapper objectMapper;
	private final SupplierFulfillmentHandoverService handoverService;

	RefundService(
		RefundRepository refundRepository,
		ClaimRepository claimRepository,
		PaymentRepository paymentRepository,
		PaymentEventRepository paymentEventRepository,
		CheckoutLockService checkoutLockService,
		CustomerOrderRepository orderRepository,
		AdminOrderActionHistoryRepository actionHistoryRepository,
		OrderStatusHistoryRepository statusHistoryRepository,
		NotificationService notificationService,
		SupplierPortalHasher hasher,
		SupplierPortalInputPolicy inputPolicy,
		ObjectMapper objectMapper,
		SupplierFulfillmentHandoverService handoverService
	) {
		this.refundRepository = refundRepository;
		this.claimRepository = claimRepository;
		this.paymentRepository = paymentRepository;
		this.paymentEventRepository = paymentEventRepository;
		this.checkoutLockService = checkoutLockService;
		this.orderRepository = orderRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.notificationService = notificationService;
		this.hasher = hasher;
		this.inputPolicy = inputPolicy;
		this.objectMapper = objectMapper;
		this.handoverService = handoverService;
	}

	@Transactional
	public Refund createCustomerCancelRefund(CustomerOrder order) {
		return createRefund(order, RefundReason.CUSTOMER_CANCEL);
	}

	@Transactional
	public Refund createOutOfStockRefund(CustomerOrder order) {
		return createRefund(order, RefundReason.SUPPLIER_OUT_OF_STOCK);
	}

	@Transactional
	public Refund createReturnRefund(CustomerOrder order) {
		Refund refund = createRefund(order, RefundReason.RETURN_REQUESTED);
		if (refund.getReason() != RefundReason.RETURN_REQUESTED) {
			throw new IllegalStateException("Order already has another refund");
		}
		return refund;
	}

	@Transactional(readOnly = true)
	RefundDtos.AdminRefundListResponse listRefunds() {
		return new RefundDtos.AdminRefundListResponse(
			refundRepository.findAllByOrderByCreatedAtAsc()
				.stream()
				.map(this::toAdminListItem)
				.toList()
		);
	}

	@Transactional
	RefundDtos.AdminRefundResponse approve(UUID refundId, UUID adminUserId, RefundDtos.RefundApprovalRequest request) {
		Refund refund = findRefundForUpdate(refundId);
		try {
			refund.approve(adminUserId, request.reason(), Instant.now());
			return toAdminResponse(refund);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@Transactional
	RefundDtos.AdminRefundResponse markManualReview(
		UUID refundId,
		UUID adminUserId,
		RefundDtos.RefundManualReviewRequest request
	) {
		Refund refund = findRefundForUpdate(refundId);
		try {
			if (request.status() == RefundStatus.APPROVED) {
				refund.approve(adminUserId, request.reason(), Instant.now());
			} else if (request.status() == RefundStatus.REJECTED) {
				refund.reject(adminUserId, request.reason(), Instant.now());
			} else {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manual review status must be APPROVED or REJECTED");
			}
			return toAdminResponse(refund);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@Transactional
	RefundDtos.AdminRefundResponse completeManualBankTransferRefund(
		UUID refundId,
		UUID adminUserId,
		String idempotencyKey,
		RefundDtos.ManualBankTransferRefundCompleteRequest request
	) {
		UUID paymentGroupId = refundRepository.findPaymentGroupIdById(refundId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found"));
		String key = idempotencyKey == null ? null : inputPolicy.requireIdempotencyKey(idempotencyKey);
		String requestHash = refundCommandHash(refundId, adminUserId, request);
		RefundDtos.AdminRefundResponse replay = refundReplay(paymentGroupId, key, requestHash);
		if (replay != null) {
			return replay;
		}
		CheckoutLockService.LockedCheckout checkout = checkoutLockService.lock(paymentGroupId);
		PaymentGroup paymentGroup = checkout.paymentGroup();
		replay = refundReplay(paymentGroupId, key, requestHash);
		if (replay != null) {
			return replay;
		}
		List<Payment> payments = paymentRepository.findAllByPaymentGroupIdForUpdate(paymentGroupId);
		List<CustomerOrder> groupOrders = checkout.orders();
		Refund refund = refundRepository.findByIdForUpdate(refundId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found"));
		if (!refund.getPaymentGroup().getId().equals(paymentGroup.getId())) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.REFUND_PAYMENT_GROUP_MISMATCH,
				"Refund does not belong to the locked payment group");
		}
		boolean receivedPaymentException = refund.isReceivedPaymentException();
		if (receivedPaymentException && key == null) {
			inputPolicy.requireIdempotencyKey(null);
		}
		if (receivedPaymentException && request.transferredAmount() == null) {
			throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
				"Transferred amount is required for a received-payment exception refund");
		}
		long transferredAmount = request.transferredAmount() == null
			? refund.getRefundAmount()
			: request.transferredAmount();
		if (transferredAmount != refund.getRefundAmount()) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT,
				"Transferred amount must equal the immutable refund amount");
		}
		Payment payment = refund.getPayment() != null
			? refund.getPayment()
			: payments.stream().findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approved payment not found"));
		if (payment.getProvider() != PaymentProvider.BANK_TRANSFER) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manual refund completion is allowed only for bank transfer payments");
		}
		Instant now = Instant.now();
		try {
			refund.completeManualBankTransfer(
				payment,
				adminUserId,
				request.reason(),
				request.bankName(),
				request.accountNumber(),
				request.accountHolder(),
				request.transferredAt(),
				request.transactionReference(),
				now
			);
			if (receivedPaymentException) {
				paymentGroup.applyReceivedPaymentExceptionRefund(refund.getRefundAmount(), refund.getReason());
			} else {
				paymentGroup.applyRefund(refund.getRefundAmount());
			}
			boolean fullyRefunded = paymentGroup.getStatus() == PaymentGroupStatus.REFUNDED;
			payment.markRefundCompleted(fullyRefunded);
			List<CustomerOrder> appliedOrders = refund.getRefundScope() == RefundScope.PAYMENT_GROUP
				? groupOrders
				: List.of(refund.getOrder());
			for (CustomerOrder order : appliedOrders) {
				OrderStatus beforeStatus = order.getStatus();
				handoverService.takeOverTerminal(order, now);
				order.markRefundRequested();
				order.markRefunded();
				actionHistoryRepository.save(new AdminOrderActionHistory(order, adminUserId,
					AdminOrderActionType.MANUAL_REFUND_COMPLETED, beforeStatus, order.getStatus(), request.reason()));
				statusHistoryRepository.save(new OrderStatusHistory(order, adminUserId,
					AdminOrderActionType.MANUAL_REFUND_COMPLETED.name(), beforeStatus, order.getStatus(), "ALLOWED",
					"Manual bank-transfer refund completed", request.reason()));
			}
			if (refund.getOrder() != null) {
				claimRepository.findByRefund_Id(refund.getId()).ifPresent(claim -> claim.complete(now));
			}
			RefundDtos.AdminRefundResponse response = toAdminResponse(refund);
			if (receivedPaymentException) {
				paymentEventRepository.save(PaymentEvent.command(payment, paymentGroup, refund.getOrder(),
					payment.getProviderPaymentKey(), PaymentEventType.MANUAL_REFUND_COMPLETED,
					PaymentCommandType.COMPLETE_RECEIVED_EXCEPTION_REFUND, key, requestHash,
					json(RefundCommandResult.from(response)),
					"Manual received-payment exception refund completed", now));
			} else {
				paymentEventRepository.save(new PaymentEvent(payment, paymentGroup, refund.getOrder(),
					payment.getProviderPaymentKey(),
					PaymentEventType.MANUAL_REFUND_COMPLETED,
					"Manual bank-transfer refund completed for order " + refund.getOrder().getOrderNumber(), now));
				notificationService.transactionalSms(refund.getOrder().getUser(), refund.getOrder(), paymentGroup,
					null, refund, NotificationType.REFUND_COMPLETED);
			}
			return response;
		} catch (IllegalStateException | IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@Transactional(readOnly = true)
	public RefundDtos.AdminRefundResponse toAdminResponse(Refund refund) {
		Payment payment = refund.getPayment();
		return new RefundDtos.AdminRefundResponse(
			refund.getId(),
			refund.getOrder() == null ? null : refund.getOrder().getId(),
			refund.getOrder() == null ? null : refund.getOrder().getOrderNumber(),
			refund.getOrder() == null ? null : refund.getOrder().getStatus(),
			refund.getPaymentGroup().getId(),
			appliedOrderIds(refund),
			refund.getPaymentGroup().getStatus(),
			payment == null ? null : payment.getId(),
			payment == null ? null : payment.getStatus(),
			refund.getReason(),
			refund.getStatus(),
			refund.getRefundAmount(),
			refund.getRefundScope(),
			refund.getProviderPaymentKey(),
			refund.getProviderCancelTransactionKey(),
			refund.getIdempotencyKey(),
			refund.getFailureCode(),
			refund.getFailureMessage(),
			refund.getRawProviderStatus(),
			refund.getReviewedByAdminId(),
			refund.getAdminReviewReason(),
			refund.getReviewedAt(),
			refund.getManualRefundedByAdminId(),
			refund.getManualRefundedAt(),
			refund.getManualRefundReason(),
			refund.getManualRefundBankName(),
			refund.getManualRefundAccountNumber(),
			refund.getManualRefundAccountHolder(),
			refund.getManualRefundTransferredAt(),
			refund.getManualRefundTransactionReference(),
			refund.getRequestedAt(),
			refund.getCompletedAt(),
			refund.getFailedAt(),
			refund.getCreatedAt()
		);
	}

	private RefundDtos.AdminRefundListItemResponse toAdminListItem(Refund refund) {
		return new RefundDtos.AdminRefundListItemResponse(
			refund.getId(),
			refund.getOrder() == null ? null : refund.getOrder().getId(),
			refund.getOrder() == null ? null : refund.getOrder().getOrderNumber(),
			refund.getOrder() == null ? null : refund.getOrder().getStatus(),
			refund.getPaymentGroup().getId(),
			appliedOrderIds(refund),
			refund.getReason(),
			refund.getStatus(),
			refund.getRefundAmount(),
			refund.getRefundScope(),
			refund.getRequestedAt(),
			refund.getCompletedAt(),
			refund.getCreatedAt()
		);
	}

	private List<UUID> appliedOrderIds(Refund refund) {
		if (refund.getRefundScope() == RefundScope.PAYMENT_GROUP) {
			return orderRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(refund.getPaymentGroup().getId())
				.stream().map(CustomerOrder::getId).toList();
		}
		return List.of(refund.getOrder().getId());
	}

	private String refundCommandHash(
		UUID refundId,
		UUID adminUserId,
		RefundDtos.ManualBankTransferRefundCompleteRequest request
	) {
		return hasher.hmac(
			"received-payment-exception-refund",
			PaymentCommandType.COMPLETE_RECEIVED_EXCEPTION_REFUND.name(),
			refundId.toString(),
			adminUserId.toString(),
			request.transferredAmount() == null ? null : request.transferredAmount().toString(),
			hasher.normalizeText(request.reason()),
			hasher.normalizeText(request.bankName()),
			hasher.normalizeText(request.accountNumber()),
			hasher.normalizeText(request.accountHolder()),
			request.transferredAt() == null ? null : request.transferredAt().toString(),
			hasher.normalizeText(request.transactionReference())
		);
	}

	private RefundDtos.AdminRefundResponse refundReplay(UUID paymentGroupId, String key, String requestHash) {
		if (key == null) {
			return null;
		}
		PaymentEvent event = paymentEventRepository
			.findByPaymentGroup_IdAndIdempotencyKeyAndCommandTypeIsNotNull(paymentGroupId, key)
			.orElse(null);
		if (event == null) {
			return null;
		}
		if (!event.matchesCommand(PaymentCommandType.COMPLETE_RECEIVED_EXCEPTION_REFUND, requestHash)
			|| event.getResultSnapshot() == null) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_CONFLICT,
				"Idempotency key conflict");
		}
		try {
			return objectMapper.readValue(event.getResultSnapshot(), RefundCommandResult.class).toResponse();
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to read refund command result");
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize refund command result");
		}
	}

	private Refund createRefund(CustomerOrder order, RefundReason reason) {
		CustomerOrder lockedOrder = orderRepository.findByIdForUpdate(order.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		return refundRepository.findByOrder_Id(lockedOrder.getId())
			.orElseGet(() -> {
				Refund refund = refundRepository.save(new Refund(lockedOrder, reason));
				paymentEventRepository.save(new PaymentEvent(
					null,
					lockedOrder.getPaymentGroup(),
					lockedOrder,
					null,
					PaymentEventType.REFUND_REQUESTED,
					"Refund requested for order " + lockedOrder.getOrderNumber(),
					Instant.now()
				));
				return refund;
			});
	}

	private Refund findRefundForUpdate(UUID refundId) {
		RefundRepository.RefundLockTarget target = refundRepository.findLockTargetById(refundId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found"));
		if (isReceivedPaymentException(target.getReason())) {
			checkoutLockService.lock(target.getPaymentGroupId());
		} else {
			if (target.getOrderId() == null) {
				throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT,
					"Order-scoped refund is missing its parent order");
			}
			orderRepository.findByIdForUpdate(target.getOrderId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		}
		Refund refund = refundRepository.findByIdForUpdate(refundId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found"));
		if (!refund.getPaymentGroup().getId().equals(target.getPaymentGroupId())
			|| (target.getOrderId() != null
				&& (refund.getOrder() == null || !refund.getOrder().getId().equals(target.getOrderId())))) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT,
				"Refund lock target changed while acquiring its parent lock");
		}
		return refund;
	}

	private boolean isReceivedPaymentException(RefundReason reason) {
		return reason == RefundReason.PAYMENT_AMOUNT_MISMATCH
			|| reason == RefundReason.LATE_DEPOSIT_EXCEPTION
			|| reason == RefundReason.SALE_UNAVAILABLE_AT_DEPOSIT;
	}

	private record RefundCommandResult(
		UUID refundId,
		UUID orderId,
		String orderNumber,
		OrderStatus orderStatus,
		UUID paymentGroupId,
		List<UUID> appliedOrderIds,
		PaymentGroupStatus paymentGroupStatus,
		UUID paymentId,
		com.dropshipshop.api.payment.domain.PaymentStatus paymentStatus,
		RefundReason reason,
		RefundStatus status,
		long refundAmount,
		RefundScope refundScope,
		Instant completedAt,
		Instant createdAt
	) {
		static RefundCommandResult from(RefundDtos.AdminRefundResponse response) {
			return new RefundCommandResult(
				response.refundId(), response.orderId(), response.orderNumber(), response.orderStatus(),
				response.paymentGroupId(), response.appliedOrderIds(), response.paymentGroupStatus(),
				response.paymentId(), response.paymentStatus(), response.reason(), response.status(),
				response.refundAmount(), response.refundScope(), response.completedAt(), response.createdAt()
			);
		}

		RefundDtos.AdminRefundResponse toResponse() {
			return new RefundDtos.AdminRefundResponse(
				refundId, orderId, orderNumber, orderStatus, paymentGroupId, appliedOrderIds,
				paymentGroupStatus, paymentId, paymentStatus, reason, status, refundAmount, refundScope,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				completedAt,
				null,
				createdAt
			);
		}
	}

}
