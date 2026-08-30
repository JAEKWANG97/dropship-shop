package com.dropshipshop.api.order;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.catalog.domain.InventoryMode;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductReviewStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierAvailability;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.checkout.CheckoutLockService;
import com.dropshipshop.api.checkout.CheckoutReservationService;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
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
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentCommandType;
import com.dropshipshop.api.payment.domain.PaymentEvent;
import com.dropshipshop.api.payment.domain.PaymentEventType;
import com.dropshipshop.api.payment.domain.PaymentExceptionReason;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.payment.repository.PaymentEventRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.procurement.DomeggookPurchaseService;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.refund.domain.RefundReason;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.supplierportal.SupplierContractTerminalService;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.SupplierPortalInputPolicy;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
class AdminOrderPaymentService {

	private final CustomerOrderRepository orderRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentEventRepository paymentEventRepository;
	private final RefundRepository refundRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;
	private final OrderStatusHistoryRepository statusHistoryRepository;
	private final AdminOrderQueryService adminOrderQueryService;
	private final NotificationService notificationService;
	private final DomeggookPurchaseService domeggookPurchaseService;
	private final CheckoutLockService checkoutLockService;
	private final CheckoutReservationService reservationService;
	private final SupplierContractTerminalService contractTerminalService;
	private final SupplierPortalHasher hasher;
	private final SupplierPortalInputPolicy inputPolicy;
	private final ObjectMapper objectMapper;

	AdminOrderPaymentService(
		CustomerOrderRepository orderRepository,
		PaymentRepository paymentRepository,
		PaymentEventRepository paymentEventRepository,
		RefundRepository refundRepository,
		FulfillmentRepository fulfillmentRepository,
		AdminOrderActionHistoryRepository actionHistoryRepository,
		OrderStatusHistoryRepository statusHistoryRepository,
		AdminOrderQueryService adminOrderQueryService,
		NotificationService notificationService,
		DomeggookPurchaseService domeggookPurchaseService,
		CheckoutLockService checkoutLockService,
		CheckoutReservationService reservationService,
		SupplierContractTerminalService contractTerminalService,
		SupplierPortalHasher hasher,
		SupplierPortalInputPolicy inputPolicy,
		ObjectMapper objectMapper
	) {
		this.orderRepository = orderRepository;
		this.paymentRepository = paymentRepository;
		this.paymentEventRepository = paymentEventRepository;
		this.refundRepository = refundRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.adminOrderQueryService = adminOrderQueryService;
		this.notificationService = notificationService;
		this.domeggookPurchaseService = domeggookPurchaseService;
		this.checkoutLockService = checkoutLockService;
		this.reservationService = reservationService;
		this.contractTerminalService = contractTerminalService;
		this.hasher = hasher;
		this.inputPolicy = inputPolicy;
		this.objectMapper = objectMapper;
	}

	@Transactional
	AdminOrderDtos.BankTransferPaymentCommandResponse confirmBankTransferDeposit(
		UUID orderId,
		UUID adminUserId,
		String idempotencyKey,
		AdminOrderDtos.BankTransferDepositConfirmRequest request
	) {
		return processDeposit(orderId, adminUserId, idempotencyKey,
			new Receipt(request.actualDepositorName(), request.actualAmount(), request.depositedAt(),
				request.transactionReference(), request.reason()),
			PaymentCommandType.CONFIRM_BANK_TRANSFER_DEPOSIT);
	}

	@Transactional
	AdminOrderDtos.BankTransferPaymentCommandResponse recordLateBankTransferDeposit(
		UUID orderId,
		UUID adminUserId,
		String idempotencyKey,
		AdminOrderDtos.BankTransferLateDepositRequest request
	) {
		return processDeposit(orderId, adminUserId, inputPolicy.requireIdempotencyKey(idempotencyKey),
			new Receipt(request.actualDepositorName(), request.actualAmount(), request.depositedAt(),
				request.transactionReference(), request.reason()),
			PaymentCommandType.RECORD_LATE_DEPOSIT);
	}

	@Transactional
	AdminOrderDtos.AdminOrderActionResponse cancelUnpaidBankTransfer(
		UUID orderId,
		UUID adminUserId,
		AdminOrderDtos.BankTransferUnpaidCancelRequest request
	) {
		CheckoutLockService.LockedCheckout checkout = checkoutLockService.lock(paymentGroupId(orderId));
		CustomerOrder selectedOrder = selectedOrder(checkout, orderId);
		requirePendingCheckout(checkout);
		Instant now = Instant.now();
		try {
			reservationService.release(checkout, now);
			checkout.paymentGroup().cancelUnpaidDeposit(adminUserId, request.reason(), now);
			for (CustomerOrder order : checkout.orders()) {
				OrderStatus beforeStatus = order.getStatus();
				order.cancelUnpaidDeposit();
				recordHistory(order, adminUserId, AdminOrderActionType.BANK_TRANSFER_UNPAID_CANCELLED,
					beforeStatus, "Bank transfer unpaid checkout cancelled", request.reason());
			}
			paymentEventRepository.save(new PaymentEvent(
				null, checkout.paymentGroup(), null, PaymentEventType.BANK_TRANSFER_UNPAID_CANCELLED,
				"Bank transfer unpaid checkout cancelled for checkout " + checkout.paymentGroup().getCheckoutNumber(), now
			));
			return actionResponse(selectedOrder);
		} catch (IllegalStateException | IllegalArgumentException exception) {
			throw conflict(exception.getMessage());
		}
	}

	@Transactional
	AdminOrderDtos.BankTransferPaymentCommandResponse recordBankTransferDepositMismatch(
		UUID orderId,
		UUID adminUserId,
		String idempotencyKey,
		AdminOrderDtos.BankTransferDepositMismatchRequest request
	) {
		if (request.actualDepositorName() == null || request.actualAmount() == null
			|| request.depositedAt() == null || request.transactionReference() == null || request.reason() == null) {
			throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
				"Complete bank-transfer receipt evidence is required");
		}
		return processDeposit(orderId, adminUserId, inputPolicy.requireIdempotencyKey(idempotencyKey),
			new Receipt(request.actualDepositorName(), request.actualAmount(), request.depositedAt(),
				request.transactionReference(), request.reason()),
			PaymentCommandType.RECORD_AMOUNT_MISMATCH);
	}

	private AdminOrderDtos.BankTransferPaymentCommandResponse processDeposit(
		UUID orderId,
		UUID adminUserId,
		String idempotencyKey,
		Receipt receipt,
		PaymentCommandType commandType
	) {
		UUID paymentGroupId = paymentGroupId(orderId);
		String key = idempotencyKey == null ? null : inputPolicy.requireIdempotencyKey(idempotencyKey);
		String requestHash = commandHash(commandType, adminUserId, receipt);
		AdminOrderDtos.BankTransferPaymentCommandResponse replay = replay(paymentGroupId, key, commandType, requestHash);
		if (replay != null) {
			return replay;
		}
		CheckoutLockService.LockedCheckout checkout = checkoutLockService.lock(paymentGroupId);
		replay = replay(paymentGroupId, key, commandType, requestHash);
		if (replay != null) {
			return replay;
		}
		CustomerOrder selectedOrder = selectedOrder(checkout, orderId);
		boolean portalOrigin = hasPortalOrigin(checkout);
		if (commandType == PaymentCommandType.CONFIRM_BANK_TRANSFER_DEPOSIT && portalOrigin && key == null) {
			inputPolicy.requireIdempotencyKey(null);
		}
		validateReceiptCommand(checkout.paymentGroup(), commandType, receipt);
		fulfillmentRepository.findAllByPaymentGroupIdForUpdate(paymentGroupId);
		boolean cancelledTerminal = checkout.paymentGroup().getStatus() == PaymentGroupStatus.CANCELLED;
		if (cancelledTerminal) {
			requireQualifyingUnpaidCancellation(checkout);
		}

		if (receipt.actualAmount() != checkout.paymentGroup().getTotalAmount()) {
			if (commandType != PaymentCommandType.RECORD_AMOUNT_MISMATCH) {
				throw conflict("Deposit amount differs from checkout total; use the deposit-mismatch action");
			}
			return receivedPaymentException(checkout, selectedOrder, adminUserId, key, requestHash, commandType,
				receipt, PaymentExceptionReason.AMOUNT_MISMATCH, RefundReason.PAYMENT_AMOUNT_MISMATCH);
		}
		if (commandType == PaymentCommandType.RECORD_AMOUNT_MISMATCH) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.DEPOSIT_AMOUNT_NOT_MISMATCHED,
				"Deposit amount matches the checkout total");
		}
		if (cancelledTerminal) {
			return receivedPaymentException(checkout, selectedOrder, adminUserId, key, requestHash, commandType,
				receipt, PaymentExceptionReason.APPROVED_AFTER_EXPIRED, RefundReason.LATE_DEPOSIT_EXCEPTION);
		}
		if (checkout.paymentGroup().getPolicyConfirmedAt() == null) {
			if (portalOrigin) {
				return receivedPaymentException(checkout, selectedOrder, adminUserId, key, requestHash, commandType,
					receipt, PaymentExceptionReason.SELLABILITY_CHECK_FAILED,
					RefundReason.SALE_UNAVAILABLE_AT_DEPOSIT);
			}
			validatePolicyConfirmed(checkout.paymentGroup());
		}
		if (commandType == PaymentCommandType.CONFIRM_BANK_TRANSFER_DEPOSIT) {
			requirePendingCheckout(checkout);
		} else {
			requireLateCheckout(checkout, portalOrigin);
			reservationService.release(checkout, Instant.now());
		}
		if (portalOrigin) {
			expirePortalContracts(checkout, adminUserId);
			if (!isCurrentlySaleable(checkout)) {
				return receivedPaymentException(checkout, selectedOrder, adminUserId, key, requestHash, commandType,
					receipt, PaymentExceptionReason.SELLABILITY_CHECK_FAILED,
					RefundReason.SALE_UNAVAILABLE_AT_DEPOSIT);
			}
			if (receipt.depositedAt().isAfter(checkout.paymentGroup().getExpiresAt())) {
				return receivedPaymentException(checkout, selectedOrder, adminUserId, key, requestHash, commandType,
					receipt, PaymentExceptionReason.APPROVED_AFTER_EXPIRED, RefundReason.LATE_DEPOSIT_EXCEPTION);
			}
		} else {
			validateLegacySellability(checkout);
		}
		if (commandType == PaymentCommandType.RECORD_LATE_DEPOSIT && !reservationService.canReacquire(checkout)) {
			return receivedPaymentException(checkout, selectedOrder, adminUserId, key, requestHash, commandType,
				receipt, PaymentExceptionReason.APPROVED_AFTER_EXPIRED, RefundReason.LATE_DEPOSIT_EXCEPTION);
		}
		return approveDeposit(checkout, selectedOrder, adminUserId, key, requestHash, commandType, receipt);
	}

	private AdminOrderDtos.BankTransferPaymentCommandResponse approveDeposit(
		CheckoutLockService.LockedCheckout checkout,
		CustomerOrder selectedOrder,
		UUID adminUserId,
		String key,
		String requestHash,
		PaymentCommandType commandType,
		Receipt receipt
	) {
		String providerPaymentKey = bankTransferPaymentKey(checkout.paymentGroup());
		ensurePaymentAbsent(providerPaymentKey);
		Instant now = Instant.now();
		if (commandType == PaymentCommandType.RECORD_LATE_DEPOSIT) {
			reservationService.reacquireAndConsume(checkout, now);
			checkout.paymentGroup().confirmLateBankTransferDeposit(adminUserId, receipt.depositorName(),
				receipt.actualAmount(), receipt.depositedAt(), receipt.transactionReference(), receipt.reason(), now);
		} else {
			reservationService.consume(checkout, now);
			checkout.paymentGroup().confirmBankTransferDeposit(adminUserId, receipt.depositorName(),
				receipt.actualAmount(), receipt.depositedAt(), receipt.transactionReference(), receipt.reason(), now);
		}
		Payment payment = paymentRepository.save(Payment.bankTransferApproved(
			checkout.paymentGroup(), providerPaymentKey, receipt.actualAmount(), now));
		for (CustomerOrder order : checkout.orders()) {
			OrderStatus beforeStatus = order.getStatus();
			if (commandType == PaymentCommandType.RECORD_LATE_DEPOSIT) {
				order.confirmLateBankTransferDeposit();
			} else {
				order.confirmBankTransferDeposit();
			}
			if (orderItemsFor(checkout, order).stream()
				.allMatch(item -> item.getManagementChannelSnapshot() == ProductManagementChannel.COREABLE)) {
				domeggookPurchaseService.queueAfterDeposit(order, adminUserId);
			}
			recordHistory(order, adminUserId, AdminOrderActionType.BANK_TRANSFER_DEPOSIT_CONFIRMED,
				beforeStatus, "Bank transfer deposit confirmed", receipt.reason());
			notificationService.transactionalSms(order.getUser(), order, checkout.paymentGroup(), null, null,
				NotificationType.PAYMENT_COMPLETED);
		}
		AdminOrderDtos.BankTransferPaymentCommandResponse response = response(selectedOrder, "APPROVED", null,
			receipt, payment, null, List.of(), null);
		recordCommandEvent(payment, checkout.paymentGroup(), commandType, key, requestHash,
			commandType == PaymentCommandType.RECORD_LATE_DEPOSIT
				? PaymentEventType.BANK_TRANSFER_LATE_DEPOSIT_RECORDED
				: PaymentEventType.BANK_TRANSFER_DEPOSIT_CONFIRMED,
			response, now);
		return response;
	}

	private AdminOrderDtos.BankTransferPaymentCommandResponse receivedPaymentException(
		CheckoutLockService.LockedCheckout checkout,
		CustomerOrder selectedOrder,
		UUID adminUserId,
		String key,
		String requestHash,
		PaymentCommandType commandType,
		Receipt receipt,
		PaymentExceptionReason exceptionReason,
		RefundReason refundReason
	) {
		String providerPaymentKey = bankTransferPaymentKey(checkout.paymentGroup());
		ensurePaymentAbsent(providerPaymentKey);
		Instant now = Instant.now();
		reservationService.release(checkout, now);
		checkout.paymentGroup().recordReceivedPaymentException(adminUserId, receipt.depositorName(),
			receipt.actualAmount(), receipt.depositedAt(), receipt.transactionReference(), receipt.reason(), now);
		Payment payment = paymentRepository.save(Payment.bankTransferException(checkout.paymentGroup(),
			providerPaymentKey, receipt.actualAmount(), exceptionReason, now));
		for (CustomerOrder order : checkout.orders()) {
			OrderStatus beforeStatus = order.getStatus();
			order.markPaymentException();
			statusHistoryRepository.save(new OrderStatusHistory(order, adminUserId,
				AdminOrderActionType.BANK_TRANSFER_RECEIVED_EXCEPTION.name(), beforeStatus,
				OrderStatus.PAYMENT_EXCEPTION, "ALLOWED", "Received bank transfer requires refund", receipt.reason()));
			order.markRefundRequested();
			actionHistoryRepository.save(new AdminOrderActionHistory(order, adminUserId,
				AdminOrderActionType.BANK_TRANSFER_RECEIVED_EXCEPTION, beforeStatus, order.getStatus(), receipt.reason()));
			statusHistoryRepository.save(new OrderStatusHistory(order, adminUserId,
				AdminOrderActionType.BANK_TRANSFER_RECEIVED_EXCEPTION.name(), OrderStatus.PAYMENT_EXCEPTION,
				OrderStatus.REFUND_REQUESTED, "ALLOWED", "Received bank transfer refund requested", receipt.reason()));
		}
		Refund groupRefund = null;
		List<Refund> orderRefunds;
		if (refundReason == RefundReason.PAYMENT_AMOUNT_MISMATCH) {
			groupRefund = refundRepository.save(Refund.receivedPaymentGroup(
				checkout.paymentGroup(), payment, receipt.actualAmount(), now));
			orderRefunds = List.of();
		} else {
			orderRefunds = checkout.orders().stream()
				.map(order -> refundRepository.save(Refund.receivedPaymentOrder(order, payment, refundReason, now)))
				.toList();
		}
		AdminOrderDtos.BankTransferPaymentCommandResponse response = response(selectedOrder, "PAYMENT_EXCEPTION",
			exceptionReason, receipt, payment, groupRefund, orderRefunds,
			groupRefund == null ? "COREABLE_COMPLETE_BANK_REFUND" : "COREABLE_APPROVE_AND_COMPLETE_BANK_REFUND");
		recordCommandEvent(payment, checkout.paymentGroup(), commandType, key, requestHash,
			PaymentEventType.PAYMENT_EXCEPTION, response, now);
		return response;
	}

	private void requirePendingCheckout(CheckoutLockService.LockedCheckout checkout) {
		if (checkout.paymentGroup().getStatus() != PaymentGroupStatus.PAYMENT_PENDING
			|| checkout.orders().stream().anyMatch(order -> order.getStatus() != OrderStatus.PAYMENT_PENDING)) {
			throw conflict("Bank transfer confirmation requires a pending checkout");
		}
	}

	private void requireLateCheckout(CheckoutLockService.LockedCheckout checkout, boolean portalOrigin) {
		if (checkout.paymentGroup().getStatus() != PaymentGroupStatus.EXPIRED
			|| checkout.orders().stream().anyMatch(order -> order.getStatus() != OrderStatus.EXPIRED)) {
			throw conflict("Late deposit requires an expired checkout");
		}
		if (!portalOrigin) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.PORTAL_LATE_DEPOSIT_UNSUPPORTED,
				"Late-deposit recovery is not supported for a legacy-only checkout");
		}
	}

	private void requireQualifyingUnpaidCancellation(CheckoutLockService.LockedCheckout checkout) {
		UUID paymentGroupId = checkout.paymentGroup().getId();
		if (checkout.paymentGroup().getUnpaidCancelledAt() == null
			|| checkout.orders().stream().anyMatch(order -> order.getStatus() != OrderStatus.CANCELLED)
			|| !paymentRepository.findAllByPaymentGroupIdForUpdate(paymentGroupId).isEmpty()
			|| refundRepository.existsByPaymentGroup_Id(paymentGroupId)
			|| fulfillmentRepository.existsByOrder_PaymentGroup_Id(paymentGroupId)) {
			throw conflict("Cancelled checkout is not an unpaid-only terminal checkout");
		}
	}

	private void expirePortalContracts(CheckoutLockService.LockedCheckout checkout, UUID adminUserId) {
		Set<UUID> portalSupplierIds = checkout.items().stream()
			.filter(item -> item.getManagementChannelSnapshot() == ProductManagementChannel.SUPPLIER_PORTAL)
			.map(item -> item.getSupplier().getId())
			.collect(Collectors.toSet());
		Instant now = Instant.now();
		for (Supplier supplier : checkout.suppliers()) {
			if (portalSupplierIds.contains(supplier.getId())) {
				contractTerminalService.expireIfOverdue(
					supplier, adminUserId, "Contract expired during deposit revalidation", now);
			}
		}
	}

	private boolean isCurrentlySaleable(CheckoutLockService.LockedCheckout checkout) {
		Instant now = Instant.now();
		for (OrderItem item : checkout.items()) {
			Product product = item.getProduct();
			ProductOption option = checkout.optionsById().get(item.getProductOption().getId());
			if (!supplierSnapshotMatches(item) || option == null
				|| item.getInventoryModeSnapshot() != option.getInventoryMode()
				|| product.getStatus() != ProductStatus.ACTIVE || option.getStatus() != ProductOptionStatus.ACTIVE
				|| !product.getComplianceStatus().allowsSale()
				|| option.getSupplierAvailability() != SupplierAvailability.AVAILABLE
				|| item.getSupplier().getStatus() != SupplierStatus.ACTIVE) {
				return false;
			}
			if (item.getManagementChannelSnapshot() == ProductManagementChannel.SUPPLIER_PORTAL
				&& (product.getManagementChannel() != ProductManagementChannel.SUPPLIER_PORTAL
					|| (product.getReviewStatus() != ProductReviewStatus.AUTO_APPROVED
						&& product.getReviewStatus() != ProductReviewStatus.APPROVED)
					|| !item.getSupplier().hasTimeValidContract(now))) {
				return false;
			}
		}
		return true;
	}

	private void validateLegacySellability(CheckoutLockService.LockedCheckout checkout) {
		for (OrderItem item : checkout.items()) {
			ProductOption option = checkout.optionsById().get(item.getProductOption().getId());
			if (!supplierSnapshotMatches(item) || option == null
				|| item.getInventoryModeSnapshot() != option.getInventoryMode()
				|| item.getInventoryModeSnapshot() != InventoryMode.UNTRACKED
				|| item.getProduct().getStatus() != ProductStatus.ACTIVE
				|| option.getStatus() != ProductOptionStatus.ACTIVE) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order contains unavailable item");
			}
		}
	}

	private boolean supplierSnapshotMatches(OrderItem item) {
		UUID snapshotSupplierId = item.getSupplier().getId();
		return snapshotSupplierId.equals(item.getOrder().getSupplier().getId())
			&& snapshotSupplierId.equals(item.getProduct().getSupplier().getId());
	}

	private void validateReceiptCommand(PaymentGroup paymentGroup, PaymentCommandType commandType, Receipt receipt) {
		if (receipt.depositorName() == null || receipt.depositorName().isBlank()
			|| receipt.actualAmount() <= 0 || receipt.depositedAt() == null
			|| receipt.transactionReference() == null || receipt.transactionReference().isBlank()
			|| receipt.reason() == null || receipt.reason().isBlank()) {
			throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
				"Complete bank-transfer receipt evidence is required");
		}
		if (receipt.depositedAt().isAfter(Instant.now())) {
			throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
				"Deposit received time cannot be in the future");
		}
		PaymentGroupStatus status = paymentGroup.getStatus();
		boolean allowed = status == PaymentGroupStatus.PAYMENT_PENDING
			|| (commandType != PaymentCommandType.CONFIRM_BANK_TRANSFER_DEPOSIT
				&& (status == PaymentGroupStatus.EXPIRED || status == PaymentGroupStatus.CANCELLED));
		if (!allowed) {
			throw conflict("Payment command is not allowed from the current checkout state");
		}
	}

	private void validatePolicyConfirmed(PaymentGroup paymentGroup) {
		if (paymentGroup.getPolicyConfirmedAt() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Checkout policy confirmation is required before deposit confirmation");
		}
	}

	private void ensurePaymentAbsent(String providerPaymentKey) {
		if (paymentRepository.findByProviderPaymentKey(providerPaymentKey).isPresent()) {
			throw conflict("Bank transfer payment is already recorded");
		}
	}

	private AdminOrderDtos.BankTransferPaymentCommandResponse response(
		CustomerOrder selectedOrder,
		String outcome,
		PaymentExceptionReason exceptionReason,
		Receipt receipt,
		Payment payment,
		Refund groupRefund,
		List<Refund> orderRefunds,
		String nextAction
	) {
		PaymentGroup paymentGroup = selectedOrder.getPaymentGroup();
		return new AdminOrderDtos.BankTransferPaymentCommandResponse(
			selectedOrder.getId(), selectedOrder.getStatus(),
			adminOrderQueryService.toFulfillmentResponse(selectedOrder, null), null,
			outcome, exceptionReason, paymentGroup.getTotalAmount(), receipt.actualAmount(),
			paymentGroup.getStatus(),
			orderRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(paymentGroup.getId()).stream()
				.map(CustomerOrder::getStatus).toList(),
			payment == null ? null : new AdminOrderDtos.PaymentCommandPaymentResponse(
				payment.getId(), payment.getProvider(), payment.getStatus(), receipt.actualAmount(),
				receipt.depositedAt(), receipt.transactionReference()),
			groupRefund == null ? null : refundResponse(groupRefund),
			orderRefunds.stream().map(this::refundResponse).toList(),
			false, "PAYMENT_EXCEPTION".equals(outcome) ? "REFUND_PROCESSING" : "PAYMENT_COMPLETED", nextAction);
	}

	private AdminOrderDtos.PaymentCommandRefundResponse refundResponse(Refund refund) {
		return new AdminOrderDtos.PaymentCommandRefundResponse(
			refund.getId(), refund.getOrder() == null ? null : refund.getOrder().getId(),
			refund.getRefundScope(), refund.getReason(), refund.getStatus(), refund.getRefundAmount());
	}

	private void recordCommandEvent(
		Payment payment,
		PaymentGroup paymentGroup,
		PaymentCommandType commandType,
		String key,
		String requestHash,
		PaymentEventType eventType,
		AdminOrderDtos.BankTransferPaymentCommandResponse response,
		Instant now
	) {
		if (key == null) {
			paymentEventRepository.save(new PaymentEvent(payment, paymentGroup, payment.getProviderPaymentKey(),
				eventType, "Bank transfer command completed for checkout " + paymentGroup.getCheckoutNumber(), now));
			return;
		}
		paymentEventRepository.save(PaymentEvent.command(payment, paymentGroup, null, payment.getProviderPaymentKey(),
			eventType, commandType, key, requestHash, json(response),
			"Bank transfer command completed for checkout " + paymentGroup.getCheckoutNumber(), now));
	}

	private AdminOrderDtos.BankTransferPaymentCommandResponse replay(
		UUID paymentGroupId,
		String key,
		PaymentCommandType commandType,
		String requestHash
	) {
		if (key == null) {
			return null;
		}
		PaymentEvent event = paymentEventRepository
			.findByPaymentGroup_IdAndIdempotencyKeyAndCommandTypeIsNotNull(paymentGroupId, key).orElse(null);
		if (event == null) {
			return null;
		}
		if (!event.matchesCommand(commandType, requestHash) || event.getResultSnapshot() == null) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_CONFLICT,
				"Idempotency key conflict");
		}
		try {
			return objectMapper.readValue(event.getResultSnapshot(),
				AdminOrderDtos.BankTransferPaymentCommandResponse.class);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to read payment command result");
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize payment command result");
		}
	}

	private String commandHash(PaymentCommandType commandType, UUID adminUserId, Receipt receipt) {
		return hasher.hmac("bank-transfer-payment-command", commandType.name(), adminUserId.toString(),
			hasher.normalizeText(receipt.depositorName()), Long.toString(receipt.actualAmount()),
			receipt.depositedAt() == null ? null : receipt.depositedAt().toString(),
			hasher.normalizeText(receipt.transactionReference()), hasher.normalizeText(receipt.reason()));
	}

	private UUID paymentGroupId(UUID orderId) {
		return orderRepository.findPaymentGroupIdByOrderId(orderId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
	}

	private CustomerOrder selectedOrder(CheckoutLockService.LockedCheckout checkout, UUID orderId) {
		return checkout.orders().stream().filter(order -> order.getId().equals(orderId)).findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
	}

	private boolean hasPortalOrigin(CheckoutLockService.LockedCheckout checkout) {
		return checkout.items().stream()
			.anyMatch(item -> item.getManagementChannelSnapshot() == ProductManagementChannel.SUPPLIER_PORTAL);
	}

	private List<OrderItem> orderItemsFor(CheckoutLockService.LockedCheckout checkout, CustomerOrder order) {
		return checkout.items().stream().filter(item -> item.getOrder().getId().equals(order.getId())).toList();
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
			order, adminUserId, actionType, beforeStatus, order.getStatus(), reason));
		if (beforeStatus != order.getStatus()) {
			statusHistoryRepository.save(new OrderStatusHistory(order, adminUserId, actionType.name(),
				beforeStatus, order.getStatus(), "ALLOWED", sideEffectSummary, reason));
		}
	}

	private AdminOrderDtos.AdminOrderActionResponse actionResponse(CustomerOrder order) {
		return new AdminOrderDtos.AdminOrderActionResponse(
			order.getId(), order.getStatus(), adminOrderQueryService.toFulfillmentResponse(order, null), null);
	}

	private ApiErrorException conflict(String message) {
		return new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, message);
	}

	private record Receipt(
		String depositorName,
		long actualAmount,
		Instant depositedAt,
		String transactionReference,
		String reason
	) {
	}
}
