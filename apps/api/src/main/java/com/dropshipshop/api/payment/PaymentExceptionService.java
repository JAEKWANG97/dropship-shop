package com.dropshipshop.api.payment;

import java.time.Instant;
import java.util.EnumSet;
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
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.domain.PaymentStatus;
import com.dropshipshop.api.payment.repository.PaymentEventRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.payment.toss.TossCancelledPayment;
import com.dropshipshop.api.payment.toss.TossPaymentException;
import com.dropshipshop.api.payment.toss.TossPaymentsClient;

@Service
public class PaymentExceptionService {

	private static final EnumSet<PaymentStatus> QUEUE_STATUSES = EnumSet.of(
		PaymentStatus.CANCEL_REQUIRED,
		PaymentStatus.CANCEL_REQUESTED,
		PaymentStatus.CANCEL_FAILED,
		PaymentStatus.REVIEW_REQUIRED
	);

	private final PaymentRepository paymentRepository;
	private final PaymentEventRepository paymentEventRepository;
	private final CustomerOrderRepository orderRepository;
	private final TossPaymentsClient tossPaymentsClient;

	PaymentExceptionService(
		PaymentRepository paymentRepository,
		PaymentEventRepository paymentEventRepository,
		CustomerOrderRepository orderRepository,
		TossPaymentsClient tossPaymentsClient
	) {
		this.paymentRepository = paymentRepository;
		this.paymentEventRepository = paymentEventRepository;
		this.orderRepository = orderRepository;
		this.tossPaymentsClient = tossPaymentsClient;
	}

	@Transactional(readOnly = true)
	public PaymentDtos.AdminPaymentExceptionListResponse listPaymentExceptions() {
		return new PaymentDtos.AdminPaymentExceptionListResponse(
			paymentRepository.findAllByStatusInOrderByCreatedAtAsc(QUEUE_STATUSES)
				.stream()
				.map(this::toAdminResponse)
				.toList()
		);
	}

	@Transactional
	public PaymentDtos.AdminPaymentExceptionResponse retryCancel(UUID paymentId) {
		Payment payment = paymentRepository.findById(paymentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
		if (payment.getStatus() == PaymentStatus.CANCELLED) {
			return toAdminResponse(payment);
		}
		if (payment.getStatus() != PaymentStatus.CANCEL_REQUIRED && payment.getStatus() != PaymentStatus.CANCEL_FAILED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment exception cancel is not retryable");
		}
		executeExceptionCancel(payment);
		return toAdminResponse(payment);
	}

	void executeExceptionCancel(Payment payment) {
		if (payment.getStatus() == PaymentStatus.CANCELLED) {
			return;
		}
		PaymentGroup paymentGroup = payment.getPaymentGroup();
		Instant now = Instant.now();
		String idempotencyKey = payment.getIdempotencyKey() == null
			? "payment-exception-cancel-" + payment.getId()
			: payment.getIdempotencyKey();
		payment.requestExceptionCancel(idempotencyKey, now);
		paymentEventRepository.save(new PaymentEvent(
			payment,
			paymentGroup,
			payment.getProviderPaymentKey(),
			PaymentEventType.PAYMENT_EXCEPTION_CANCEL_REQUESTED,
			"Payment exception cancel requested",
			now
		));

		try {
			TossCancelledPayment cancelledPayment = tossPaymentsClient.cancel(
				payment.getProviderPaymentKey(),
				cancelReason(payment),
				cancelAmount(payment),
				idempotencyKey
			);
			Instant cancelledAt = Instant.now();
			payment.completeExceptionCancel(
				cancelledPayment.cancelTransactionKey(),
				cancelledPayment.rawStatus(),
				cancelledAt
			);
			paymentGroup.markCancelled();
			orders(paymentGroup).forEach(CustomerOrder::markCancelledFromPaymentException);
			paymentEventRepository.save(new PaymentEvent(
				payment,
				paymentGroup,
				payment.getProviderPaymentKey(),
				PaymentEventType.PAYMENT_EXCEPTION_CANCEL_COMPLETED,
				"Payment exception cancel completed",
				cancelledAt
			));
		} catch (TossPaymentException exception) {
			Instant failedAt = Instant.now();
			payment.failExceptionCancel("TOSS_CANCEL_FAILED", exception.getMessage());
			paymentGroup.markCancelFailed();
			paymentEventRepository.save(new PaymentEvent(
				payment,
				paymentGroup,
				payment.getProviderPaymentKey(),
				PaymentEventType.PAYMENT_EXCEPTION_CANCEL_FAILED,
				exception.getMessage(),
				failedAt
			));
		}
	}

	private long cancelAmount(Payment payment) {
		return payment.getApprovedAmount() == null ? payment.getRequestedAmount() : payment.getApprovedAmount();
	}

	private String cancelReason(Payment payment) {
		return "PAYMENT_EXCEPTION_" + (payment.getExceptionReason() == null ? "UNKNOWN" : payment.getExceptionReason().name());
	}

	private List<CustomerOrder> orders(PaymentGroup paymentGroup) {
		return orderRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(paymentGroup.getId());
	}

	private PaymentDtos.AdminPaymentExceptionResponse toAdminResponse(Payment payment) {
		PaymentGroup paymentGroup = payment.getPaymentGroup();
		return new PaymentDtos.AdminPaymentExceptionResponse(
			payment.getId(),
			paymentGroup.getId(),
			paymentGroup.getCheckoutNumber(),
			paymentGroup.getUser().getEmail(),
			payment.getStatus(),
			paymentGroup.getStatus(),
			payment.getExceptionReason(),
			payment.getRequestedAmount(),
			payment.getApprovedAmount(),
			payment.getProviderPaymentKey(),
			payment.getProviderCancelTransactionKey(),
			payment.getIdempotencyKey(),
			payment.getFailureCode(),
			payment.getFailureMessage(),
			payment.getCancelRequestedAt(),
			payment.getCancelledAt(),
			payment.getCreatedAt()
		);
	}
}
