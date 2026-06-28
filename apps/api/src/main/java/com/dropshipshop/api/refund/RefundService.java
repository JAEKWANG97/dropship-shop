package com.dropshipshop.api.refund;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentEvent;
import com.dropshipshop.api.payment.domain.PaymentEventType;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
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
	private final PaymentRepository paymentRepository;
	private final PaymentEventRepository paymentEventRepository;
	private final CustomerOrderRepository orderRepository;
	private final TossPaymentsClient tossPaymentsClient;

	RefundService(
		RefundRepository refundRepository,
		PaymentRepository paymentRepository,
		PaymentEventRepository paymentEventRepository,
		CustomerOrderRepository orderRepository,
		TossPaymentsClient tossPaymentsClient
	) {
		this.refundRepository = refundRepository;
		this.paymentRepository = paymentRepository;
		this.paymentEventRepository = paymentEventRepository;
		this.orderRepository = orderRepository;
		this.tossPaymentsClient = tossPaymentsClient;
	}

	@Transactional
	public Refund createCustomerCancelRefund(CustomerOrder order) {
		return createRefund(order, RefundReason.CUSTOMER_CANCEL);
	}

	@Transactional
	public Refund createOutOfStockRefund(CustomerOrder order) {
		return createRefund(order, RefundReason.SUPPLIER_OUT_OF_STOCK);
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
	RefundDtos.AdminRefundResponse requestPgCancel(UUID refundId) {
		Refund refund = findRefund(refundId);
		return executePgCancel(refund, false);
	}

	@Transactional
	RefundDtos.AdminRefundResponse retryPgCancel(UUID refundId) {
		Refund refund = findRefund(refundId);
		return executePgCancel(refund, true);
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
		String idempotencyKey = retry
			? "refund-" + refund.getId() + "-retry-" + now.toEpochMilli()
			: "refund-" + refund.getId();
		try {
			refund.getOrder().markRefundRequested();
			refund.requestPgCancel(payment, idempotencyKey, now);
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
			paymentEventRepository.save(new PaymentEvent(
				payment,
				refund.getPaymentGroup(),
				payment.getProviderPaymentKey(),
				PaymentEventType.REFUND_COMPLETED,
				"Refund completed for order " + refund.getOrder().getOrderNumber(),
				completedAt
			));
			return toAdminResponse(refund);
		} catch (IllegalStateException | IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		} catch (TossPaymentException exception) {
			Instant failedAt = Instant.now();
			refund.markRetryRequired("TOSS_CANCEL_FAILED", exception.getMessage(), failedAt);
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
}
