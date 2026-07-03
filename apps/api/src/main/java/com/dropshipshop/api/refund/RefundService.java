package com.dropshipshop.api.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentProvider;
import com.dropshipshop.api.payment.repository.PaymentEventRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.payment.toss.TossCancelledPayment;
import com.dropshipshop.api.payment.toss.TossPaymentException;
import com.dropshipshop.api.payment.toss.TossPaymentsClient;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.refund.domain.RefundReason;
import com.dropshipshop.api.refund.domain.RefundStatus;
import com.dropshipshop.api.refund.repository.RefundRepository;

@Service
public class RefundService {

	private final RefundRepository refundRepository;
	private final ClaimRepository claimRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentEventRepository paymentEventRepository;
	private final CustomerOrderRepository orderRepository;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;
	private final OrderStatusHistoryRepository statusHistoryRepository;
	private final TossPaymentsClient tossPaymentsClient;
	private final NotificationService notificationService;

	RefundService(
		RefundRepository refundRepository,
		ClaimRepository claimRepository,
		PaymentRepository paymentRepository,
		PaymentEventRepository paymentEventRepository,
		CustomerOrderRepository orderRepository,
		AdminOrderActionHistoryRepository actionHistoryRepository,
		OrderStatusHistoryRepository statusHistoryRepository,
		TossPaymentsClient tossPaymentsClient,
		NotificationService notificationService
	) {
		this.refundRepository = refundRepository;
		this.claimRepository = claimRepository;
		this.paymentRepository = paymentRepository;
		this.paymentEventRepository = paymentEventRepository;
		this.orderRepository = orderRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.tossPaymentsClient = tossPaymentsClient;
		this.notificationService = notificationService;
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
				.map(this::toAdminResponse)
				.toList()
		);
	}

	@Transactional
	RefundDtos.AdminRefundResponse approve(UUID refundId, UUID adminUserId, RefundDtos.RefundApprovalRequest request) {
		Refund refund = findRefund(refundId);
		try {
			refund.approve(adminUserId, request.reason(), Instant.now());
			return toAdminResponse(refund);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@Transactional
	RefundDtos.AdminRefundResponse requestPgCancel(UUID refundId) {
		Refund refund = findRefund(refundId);
		return executePgCancel(refund, false);
	}

	@Transactional
	RefundDtos.AdminRefundResponse retryPgCancel(UUID refundId) {
		Refund refund = findRefund(refundId);
		return executePgCancel(refund, true);
	}

	@Transactional
	RefundDtos.AdminRefundResponse markManualReview(
		UUID refundId,
		UUID adminUserId,
		RefundDtos.RefundManualReviewRequest request
	) {
		Refund refund = findRefund(refundId);
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
		RefundDtos.ManualBankTransferRefundCompleteRequest request
	) {
		Refund refund = findRefund(refundId);
		Payment payment = paymentRepository.findFirstByPaymentGroup_IdOrderByCreatedAtDesc(refund.getPaymentGroup().getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approved payment not found"));
		if (payment.getProvider() != PaymentProvider.BANK_TRANSFER) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manual refund completion is allowed only for bank transfer payments");
		}
		Instant now = Instant.now();
		OrderStatus beforeStatus = refund.getOrder().getStatus();
		try {
			refund.getOrder().markRefundRequested();
			refund.completeManualBankTransfer(
				payment,
				adminUserId,
				request.reason(),
				request.bankName(),
				request.accountNumber(),
				request.accountHolder(),
				now
			);
			refund.getPaymentGroup().applyRefund(refund.getRefundAmount());
			boolean fullyRefunded = refund.getPaymentGroup().getStatus() == PaymentGroupStatus.REFUNDED;
			payment.markRefundCompleted(fullyRefunded);
			refund.getOrder().markRefunded();
			claimRepository.findByRefund_Id(refund.getId())
				.ifPresent(claim -> claim.complete(now));
			actionHistoryRepository.save(new AdminOrderActionHistory(
				refund.getOrder(),
				adminUserId,
				AdminOrderActionType.MANUAL_REFUND_COMPLETED,
				beforeStatus,
				refund.getOrder().getStatus(),
				request.reason()
			));
			statusHistoryRepository.save(new OrderStatusHistory(
				refund.getOrder(),
				adminUserId,
				AdminOrderActionType.MANUAL_REFUND_COMPLETED.name(),
				beforeStatus,
				refund.getOrder().getStatus(),
				"ALLOWED",
				"Manual bank-transfer refund completed",
				request.reason()
			));
			paymentEventRepository.save(new PaymentEvent(
				payment,
				refund.getPaymentGroup(),
				payment.getProviderPaymentKey(),
				PaymentEventType.MANUAL_REFUND_COMPLETED,
				"Manual bank-transfer refund completed for order " + refund.getOrder().getOrderNumber(),
				now
			));
			notificationService.transactionalSms(
				refund.getOrder().getUser(),
				refund.getOrder(),
				refund.getPaymentGroup(),
				null,
				refund,
				NotificationType.REFUND_COMPLETED
			);
			return toAdminResponse(refund);
		} catch (IllegalStateException | IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@Transactional(readOnly = true)
	public RefundDtos.AdminRefundResponse toAdminResponse(Refund refund) {
		Payment payment = refund.getPayment();
		return new RefundDtos.AdminRefundResponse(
			refund.getId(),
			refund.getOrder().getId(),
			refund.getOrder().getOrderNumber(),
			refund.getOrder().getStatus(),
			refund.getPaymentGroup().getId(),
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
			refund.getRequestedAt(),
			refund.getCompletedAt(),
			refund.getFailedAt(),
			refund.getCreatedAt()
		);
	}

	private Refund createRefund(CustomerOrder order, RefundReason reason) {
		return refundRepository.findByOrder_Id(order.getId())
			.orElseGet(() -> {
				Refund refund = refundRepository.save(new Refund(order, reason));
				paymentEventRepository.save(new PaymentEvent(
					null,
					order.getPaymentGroup(),
					null,
					PaymentEventType.REFUND_REQUESTED,
					"Refund requested for order " + order.getOrderNumber(),
					Instant.now()
				));
				return refund;
			});
	}

	private RefundDtos.AdminRefundResponse executePgCancel(Refund refund, boolean retry) {
		if (refund.getStatus() == RefundStatus.COMPLETED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund is already completed");
		}
		if (retry && refund.getStatus() != RefundStatus.RETRY_REQUIRED && refund.getStatus() != RefundStatus.FAILED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund is not retryable");
		}
		Payment payment = paymentRepository.findFirstByPaymentGroup_IdOrderByCreatedAtDesc(refund.getPaymentGroup().getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approved payment not found"));
		Instant now = Instant.now();
		String idempotencyKey = refundIdempotencyKey(refund, retry);
		try {
			refund.getOrder().markRefundRequested();
			refund.requestPgCancel(payment, idempotencyKey, now, retry);
			TossCancelledPayment cancelledPayment = tossPaymentsClient.cancel(
				payment.getProviderPaymentKey(),
				refund.getReason().name(),
				refund.getRefundAmount(),
				idempotencyKey
			);
			Instant completedAt = Instant.now();
			refund.complete(cancelledPayment.cancelTransactionKey(), cancelledPayment.rawStatus(), completedAt);
			refund.getPaymentGroup().applyRefund(refund.getRefundAmount());
			boolean fullyRefunded = refund.getPaymentGroup().getStatus() == PaymentGroupStatus.REFUNDED;
			payment.markRefundCompleted(fullyRefunded);
			refund.getOrder().markRefunded();
			claimRepository.findByRefund_Id(refund.getId())
				.ifPresent(claim -> claim.complete(completedAt));
			paymentEventRepository.save(new PaymentEvent(
				payment,
				refund.getPaymentGroup(),
				payment.getProviderPaymentKey(),
				PaymentEventType.REFUND_COMPLETED,
				"Refund completed for order " + refund.getOrder().getOrderNumber(),
				completedAt
			));
			notificationService.transactionalSms(
				refund.getOrder().getUser(),
				refund.getOrder(),
				refund.getPaymentGroup(),
				null,
				refund,
				NotificationType.REFUND_COMPLETED
			);
			return toAdminResponse(refund);
		} catch (IllegalStateException | IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		} catch (TossPaymentException exception) {
			Instant failedAt = Instant.now();
			if (retry) {
				refund.markManualReviewRequired("TOSS_CANCEL_FAILED", exception.getMessage(), failedAt);
			} else {
				refund.markRetryRequired("TOSS_CANCEL_FAILED", exception.getMessage(), failedAt);
			}
			payment.markRefundFailed("TOSS_CANCEL_FAILED", exception.getMessage());
			paymentEventRepository.save(new PaymentEvent(
				payment,
				refund.getPaymentGroup(),
				payment.getProviderPaymentKey(),
				PaymentEventType.REFUND_FAILED,
				exception.getMessage(),
				failedAt
			));
			return toAdminResponse(refund);
		}
	}

	private Refund findRefund(UUID refundId) {
		return refundRepository.findById(refundId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found"));
	}

	private String refundIdempotencyKey(Refund refund, boolean retry) {
		if (refund.getIdempotencyKey() != null) {
			return refund.getIdempotencyKey();
		}
		if (retry) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund idempotency key is missing");
		}
		return "refund-" + refund.getId();
	}
}
