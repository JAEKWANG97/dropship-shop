package com.dropshipshop.api.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.order.domain.AdminOrderActionHistory;
import com.dropshipshop.api.order.domain.AdminOrderActionType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.OrderStatusHistory;
import com.dropshipshop.api.order.repository.AdminOrderActionHistoryRepository;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentEvent;
import com.dropshipshop.api.payment.domain.PaymentEventType;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentEventRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.procurement.DomeggookPurchaseService;

@Service
class AdminOrderPaymentService {

	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentEventRepository paymentEventRepository;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;
	private final OrderStatusHistoryRepository statusHistoryRepository;
	private final AdminOrderQueryService adminOrderQueryService;
	private final NotificationService notificationService;
	private final DomeggookPurchaseService domeggookPurchaseService;

	AdminOrderPaymentService(
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		PaymentRepository paymentRepository,
		PaymentEventRepository paymentEventRepository,
		AdminOrderActionHistoryRepository actionHistoryRepository,
		OrderStatusHistoryRepository statusHistoryRepository,
		AdminOrderQueryService adminOrderQueryService,
		NotificationService notificationService,
		DomeggookPurchaseService domeggookPurchaseService
	) {
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.paymentRepository = paymentRepository;
		this.paymentEventRepository = paymentEventRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.adminOrderQueryService = adminOrderQueryService;
		this.notificationService = notificationService;
		this.domeggookPurchaseService = domeggookPurchaseService;
	}

	@Transactional
	AdminOrderDtos.AdminOrderActionResponse confirmBankTransferDeposit(
		UUID orderId,
		UUID adminUserId,
		AdminOrderDtos.BankTransferDepositConfirmRequest request
	) {
		CustomerOrder selectedOrder = findOrder(orderId);
		PaymentGroup paymentGroup = selectedOrder.getPaymentGroup();
		List<CustomerOrder> groupOrders = pendingGroupOrders(paymentGroup);
		validatePolicyConfirmed(paymentGroup);
		validateSellability(groupOrders);
		String providerPaymentKey = bankTransferPaymentKey(paymentGroup);
		paymentRepository.findByProviderPaymentKey(providerPaymentKey).ifPresent(payment -> {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank transfer payment is already recorded");
		});

		Instant now = Instant.now();
		try {
			paymentGroup.confirmBankTransferDeposit(
				adminUserId,
				request.actualDepositorName(),
				request.actualAmount(),
				request.depositedAt(),
				request.transactionReference(),
				request.reason(),
				now
			);
			Payment payment = paymentRepository.save(Payment.bankTransferApproved(
				paymentGroup,
				providerPaymentKey,
				request.actualAmount(),
				now
			));
			for (CustomerOrder order : groupOrders) {
				OrderStatus beforeStatus = order.getStatus();
				order.confirmBankTransferDeposit();
				domeggookPurchaseService.queueAfterDeposit(order, adminUserId);
				recordHistory(
					order,
					adminUserId,
					AdminOrderActionType.BANK_TRANSFER_DEPOSIT_CONFIRMED,
					beforeStatus,
					"Bank transfer deposit confirmed",
					request.reason()
				);
				notificationService.transactionalSms(order.getUser(), order, paymentGroup, null, null, NotificationType.PAYMENT_COMPLETED);
			}
			paymentEventRepository.save(new PaymentEvent(
				payment,
				paymentGroup,
				payment.getProviderPaymentKey(),
				PaymentEventType.BANK_TRANSFER_DEPOSIT_CONFIRMED,
				"Bank transfer deposit confirmed for checkout " + paymentGroup.getCheckoutNumber(),
				now
			));
			return actionResponse(selectedOrder);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@Transactional
	AdminOrderDtos.AdminOrderActionResponse cancelUnpaidBankTransfer(
		UUID orderId,
		UUID adminUserId,
		AdminOrderDtos.BankTransferUnpaidCancelRequest request
	) {
		CustomerOrder selectedOrder = findOrder(orderId);
		PaymentGroup paymentGroup = selectedOrder.getPaymentGroup();
		List<CustomerOrder> groupOrders = pendingGroupOrders(paymentGroup);
		Instant now = Instant.now();
		try {
			paymentGroup.cancelUnpaidDeposit(adminUserId, request.reason(), now);
			for (CustomerOrder order : groupOrders) {
				OrderStatus beforeStatus = order.getStatus();
				order.cancelUnpaidDeposit();
				recordHistory(
					order,
					adminUserId,
					AdminOrderActionType.BANK_TRANSFER_UNPAID_CANCELLED,
					beforeStatus,
					"Bank transfer unpaid checkout cancelled",
					request.reason()
				);
			}
			paymentEventRepository.save(new PaymentEvent(
				null,
				paymentGroup,
				null,
				PaymentEventType.BANK_TRANSFER_UNPAID_CANCELLED,
				"Bank transfer unpaid checkout cancelled for checkout " + paymentGroup.getCheckoutNumber(),
				now
			));
			return actionResponse(selectedOrder);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@Transactional
	AdminOrderDtos.AdminOrderActionResponse recordBankTransferDepositMismatch(
		UUID orderId,
		UUID adminUserId,
		AdminOrderDtos.BankTransferDepositMismatchRequest request
	) {
		CustomerOrder selectedOrder = findOrder(orderId);
		PaymentGroup paymentGroup = selectedOrder.getPaymentGroup();
		List<CustomerOrder> groupOrders = pendingGroupOrders(paymentGroup);
		Instant now = Instant.now();
		try {
			paymentGroup.recordDepositMismatch(adminUserId, request.memo(), now);
			for (CustomerOrder order : groupOrders) {
				actionHistoryRepository.save(new AdminOrderActionHistory(
					order,
					adminUserId,
					AdminOrderActionType.BANK_TRANSFER_DEPOSIT_MISMATCH_RECORDED,
					order.getStatus(),
					order.getStatus(),
					request.memo()
				));
			}
			paymentEventRepository.save(new PaymentEvent(
				null,
				paymentGroup,
				null,
				PaymentEventType.BANK_TRANSFER_DEPOSIT_MISMATCH_RECORDED,
				request.memo(),
				now
			));
			return actionResponse(selectedOrder);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	private CustomerOrder findOrder(UUID orderId) {
		return orderRepository.findById(orderId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
	}

	private List<CustomerOrder> pendingGroupOrders(PaymentGroup paymentGroup) {
		List<CustomerOrder> orders = orderRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(paymentGroup.getId());
		if (orders.isEmpty() || orders.stream().anyMatch(order -> order.getStatus() != OrderStatus.PAYMENT_PENDING)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank transfer action is allowed only while all checkout orders are payment pending");
		}
		return orders;
	}

	private void validatePolicyConfirmed(PaymentGroup paymentGroup) {
		if (paymentGroup.getPolicyConfirmedAt() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout policy confirmation is required before deposit confirmation");
		}
	}

	private void validateSellability(List<CustomerOrder> orders) {
		for (CustomerOrder order : orders) {
			for (OrderItem item : orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(order.getId())) {
				if (item.getProduct().getStatus() != ProductStatus.ACTIVE
					|| item.getProductOption().getStatus() != ProductOptionStatus.ACTIVE) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order contains unavailable item");
				}
			}
		}
	}

	private String bankTransferPaymentKey(PaymentGroup paymentGroup) {
		return "BANK-" + paymentGroup.getCheckoutNumber();
	}

	private void recordHistory(
		CustomerOrder order,
		UUID adminUserId,
		AdminOrderActionType actionType,
		OrderStatus beforeStatus,
		String sideEffectSummary,
		String reason
	) {
		actionHistoryRepository.save(new AdminOrderActionHistory(
			order,
			adminUserId,
			actionType,
			beforeStatus,
			order.getStatus(),
			reason
		));
		if (beforeStatus != order.getStatus()) {
			statusHistoryRepository.save(new OrderStatusHistory(
				order,
				adminUserId,
				actionType.name(),
				beforeStatus,
				order.getStatus(),
				"ALLOWED",
				sideEffectSummary,
				reason
			));
		}
	}

	private AdminOrderDtos.AdminOrderActionResponse actionResponse(CustomerOrder order) {
		return new AdminOrderDtos.AdminOrderActionResponse(
			order.getId(),
			order.getStatus(),
			adminOrderQueryService.toFulfillmentResponse(order, null),
			null
		);
	}
}
