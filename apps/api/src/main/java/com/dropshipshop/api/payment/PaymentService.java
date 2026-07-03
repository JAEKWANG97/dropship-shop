package com.dropshipshop.api.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentEvent;
import com.dropshipshop.api.payment.domain.PaymentEventType;
import com.dropshipshop.api.payment.domain.PaymentExceptionReason;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.domain.PaymentMethod;
import com.dropshipshop.api.payment.repository.PaymentEventRepository;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.payment.toss.TossApprovedPayment;
import com.dropshipshop.api.payment.toss.TossPaymentException;
import com.dropshipshop.api.payment.toss.TossPaymentsClient;

@Service
public class PaymentService {

	private final PaymentGroupRepository paymentGroupRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentEventRepository paymentEventRepository;
	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final TossPaymentsClient tossPaymentsClient;
	private final PaymentExceptionService paymentExceptionService;
	private final NotificationService notificationService;
	private final Clock clock;

	public PaymentService(
		PaymentGroupRepository paymentGroupRepository,
		PaymentRepository paymentRepository,
		PaymentEventRepository paymentEventRepository,
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		TossPaymentsClient tossPaymentsClient,
		PaymentExceptionService paymentExceptionService,
		NotificationService notificationService
	) {
		this.paymentGroupRepository = paymentGroupRepository;
		this.paymentRepository = paymentRepository;
		this.paymentEventRepository = paymentEventRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.tossPaymentsClient = tossPaymentsClient;
		this.paymentExceptionService = paymentExceptionService;
		this.notificationService = notificationService;
		this.clock = Clock.systemUTC();
	}

	@Transactional(noRollbackFor = ResponseStatusException.class)
	public PaymentDtos.PaymentConfirmResponse confirmTossPayment(UUID userId, PaymentDtos.TossConfirmRequest request) {
		PaymentGroup paymentGroup = findPaymentGroup(userId, request.checkoutNumber());
		return paymentRepository.findByProviderPaymentKey(request.paymentKey())
			.map(existing -> handleDuplicateConfirm(existing, paymentGroup))
			.orElseGet(() -> confirmNewPayment(paymentGroup, request));
	}

	private PaymentDtos.PaymentConfirmResponse confirmNewPayment(
		PaymentGroup paymentGroup,
		PaymentDtos.TossConfirmRequest request
	) {
		validateConfirmPreconditions(paymentGroup, request);
		Instant now = Instant.now(clock);
		paymentEventRepository.save(new PaymentEvent(
			null,
			paymentGroup,
			request.paymentKey(),
			PaymentEventType.CONFIRM_REQUESTED,
			"Confirm requested",
			now
		));

		TossApprovedPayment approvedPayment;
		try {
			approvedPayment = tossPaymentsClient.confirm(
				request.paymentKey(),
				paymentGroup.getCheckoutNumber(),
				paymentGroup.getTotalAmount()
			);
		} catch (TossPaymentException exception) {
			Payment payment = paymentRepository.save(Payment.cancelRequired(
				paymentGroup,
				request.paymentKey(),
				PaymentMethod.CARD,
				paymentGroup.getTotalAmount(),
				null,
				PaymentExceptionReason.PG_CONFIRMATION_ERROR,
				null,
				now
			));
			markPaymentException(paymentGroup);
			paymentEventRepository.save(new PaymentEvent(
				payment,
				paymentGroup,
				request.paymentKey(),
				PaymentEventType.PAYMENT_EXCEPTION,
				exception.getMessage(),
				now
			));
			orders(paymentGroup).forEach(order -> notificationService.transactionalSms(
				order.getUser(),
				order,
				paymentGroup,
				null,
				null,
				NotificationType.PAYMENT_EXCEPTION
			));
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Toss Payments confirm failed");
		}

		if (approvedPayment.totalAmount() != paymentGroup.getTotalAmount()) {
			Payment payment = paymentRepository.save(Payment.cancelRequired(
				paymentGroup,
				request.paymentKey(),
				approvedPayment.method(),
				paymentGroup.getTotalAmount(),
				approvedPayment.totalAmount(),
				PaymentExceptionReason.AMOUNT_MISMATCH,
				approvedPayment.rawStatus(),
				now
			));
			markPaymentException(paymentGroup);
			paymentEventRepository.save(new PaymentEvent(
				payment,
				paymentGroup,
				request.paymentKey(),
				PaymentEventType.PAYMENT_EXCEPTION,
				"Approved amount mismatch",
				now
			));
			orders(paymentGroup).forEach(order -> notificationService.transactionalSms(
				order.getUser(),
				order,
				paymentGroup,
				null,
				null,
				NotificationType.PAYMENT_EXCEPTION
			));
			paymentExceptionService.executeExceptionCancel(payment);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approved amount does not match checkout amount");
		}

		Payment payment = paymentRepository.save(Payment.approved(
			paymentGroup,
			approvedPayment.paymentKey(),
			approvedPayment.method(),
			paymentGroup.getTotalAmount(),
			approvedPayment.totalAmount(),
			approvedPayment.approvedAt(),
			approvedPayment.rawStatus(),
			now
		));
		paymentGroup.approve(approvedPayment.totalAmount(), approvedPayment.approvedAt());
		List<CustomerOrder> orders = orders(paymentGroup);
		orders.forEach(CustomerOrder::markSupplierOrderPending);
		paymentEventRepository.save(new PaymentEvent(
			payment,
			paymentGroup,
			approvedPayment.paymentKey(),
			PaymentEventType.CONFIRM_APPROVED,
			"Confirm approved",
			now
		));
		orders.forEach(order -> notificationService.transactionalSms(
			order.getUser(),
			order,
			paymentGroup,
			null,
			null,
			NotificationType.PAYMENT_COMPLETED
		));
		return toResponse(paymentGroup, payment, orders);
	}

	private PaymentDtos.PaymentConfirmResponse handleDuplicateConfirm(Payment payment, PaymentGroup paymentGroup) {
		if (!payment.getPaymentGroup().getId().equals(paymentGroup.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment key already belongs to another checkout");
		}
		return toResponse(paymentGroup, payment, orders(paymentGroup));
	}

	private void validateConfirmPreconditions(PaymentGroup paymentGroup, PaymentDtos.TossConfirmRequest request) {
		if (paymentGroup.getStatus() != PaymentGroupStatus.PAYMENT_PENDING) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout is not payment pending");
		}
		if (paymentGroup.getExpiresAt().isBefore(Instant.now(clock))) {
			paymentGroup.expire();
			orders(paymentGroup).forEach(CustomerOrder::expire);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout is expired");
		}
		if (paymentGroup.getPolicyConfirmedAt() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout policy confirmation is required");
		}
		if (request.amount() != paymentGroup.getTotalAmount()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested amount does not match checkout amount");
		}
		if (!isSellable(paymentGroup)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout contains unavailable item");
		}
	}

	private boolean isSellable(PaymentGroup paymentGroup) {
		for (CustomerOrder order : orders(paymentGroup)) {
			for (OrderItem item : orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(order.getId())) {
				if (item.getProduct().getStatus() != ProductStatus.ACTIVE
					|| item.getProductOption().getStatus() != ProductOptionStatus.ACTIVE) {
					return false;
				}
			}
		}
		return true;
	}

	private void markPaymentException(PaymentGroup paymentGroup) {
		paymentGroup.markPaymentException();
		orders(paymentGroup).forEach(CustomerOrder::markPaymentException);
	}

	private PaymentGroup findPaymentGroup(UUID userId, String checkoutNumber) {
		return paymentGroupRepository.findByCheckoutNumberAndUser_Id(checkoutNumber, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout not found"));
	}

	private List<CustomerOrder> orders(PaymentGroup paymentGroup) {
		return orderRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(paymentGroup.getId());
	}

	private PaymentDtos.PaymentConfirmResponse toResponse(
		PaymentGroup paymentGroup,
		Payment payment,
		List<CustomerOrder> orders
	) {
		long approvedAmount = payment.getApprovedAmount() == null ? 0 : payment.getApprovedAmount();
		return new PaymentDtos.PaymentConfirmResponse(
			payment.getId(),
			paymentGroup.getCheckoutNumber(),
			payment.getStatus(),
			paymentGroup.getStatus(),
			approvedAmount,
			payment.getApprovedAt(),
			orders.stream().map(this::toOrderStatusResponse).toList()
		);
	}

	private PaymentDtos.OrderStatusResponse toOrderStatusResponse(CustomerOrder order) {
		return new PaymentDtos.OrderStatusResponse(order.getId(), order.getOrderNumber(), order.getStatus());
	}
}
