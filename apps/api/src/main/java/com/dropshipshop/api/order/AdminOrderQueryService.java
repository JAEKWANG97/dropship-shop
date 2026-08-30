package com.dropshipshop.api.order;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.claim.domain.ClaimEvidence;
import com.dropshipshop.api.claim.repository.ClaimEvidenceRepository;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.AdminOrderActionHistory;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.OrderStatusHistory;
import com.dropshipshop.api.order.repository.AdminOrderActionHistoryRepository;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.refund.domain.RefundScope;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;
import com.dropshipshop.api.user.domain.UserAccount;

@Service
class AdminOrderQueryService {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Instant EARLIEST_ORDER_TIME = Instant.parse("1970-01-01T00:00:00Z");
	private static final Instant LATEST_ORDER_TIME = Instant.parse("9999-12-31T00:00:00Z");

	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final PaymentRepository paymentRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final ShipmentRepository shipmentRepository;
	private final RefundRepository refundRepository;
	private final ClaimRepository claimRepository;
	private final ClaimEvidenceRepository claimEvidenceRepository;
	private final OrderStatusHistoryRepository statusHistoryRepository;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;

	AdminOrderQueryService(
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		PaymentRepository paymentRepository,
		FulfillmentRepository fulfillmentRepository,
		ShipmentRepository shipmentRepository,
		RefundRepository refundRepository,
		ClaimRepository claimRepository,
		ClaimEvidenceRepository claimEvidenceRepository,
		OrderStatusHistoryRepository statusHistoryRepository,
		AdminOrderActionHistoryRepository actionHistoryRepository
	) {
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.paymentRepository = paymentRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.shipmentRepository = shipmentRepository;
		this.refundRepository = refundRepository;
		this.claimRepository = claimRepository;
		this.claimEvidenceRepository = claimEvidenceRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.actionHistoryRepository = actionHistoryRepository;
	}

	@Transactional(readOnly = true)
	AdminOrderDtos.AdminOrderListResponse listOrders(
		String query,
		OrderStatus status,
		LocalDate from,
		LocalDate to,
		int page,
		int size
	) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
		}
		String keyword = query == null || query.isBlank()
			? "%"
			: "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
		Sort sort = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
		Page<CustomerOrder> orders = orderRepository.findAdminOrders(
			status == null ? OrderStatus.SUPPLIER_ORDER_PENDING : status,
			keyword,
			from == null ? EARLIEST_ORDER_TIME : from.atStartOfDay(SEOUL).toInstant(),
			to == null ? LATEST_ORDER_TIME : to.plusDays(1).atStartOfDay(SEOUL).toInstant(),
			PageRequest.of(page, size, sort)
		);
		return new AdminOrderDtos.AdminOrderListResponse(
			orders.getContent().stream()
			.map(this::toSummaryResponse)
			.toList(),
			orders.getNumber(),
			orders.getSize(),
			orders.getTotalElements(),
			orders.getTotalPages()
		);
	}

	@Transactional(readOnly = true)
	AdminOrderDtos.AdminOrderDetailResponse getOrder(UUID orderId) {
		CustomerOrder order = orderRepository.findById(orderId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		List<AdminOrderDtos.AdminOrderItemResponse> items = orderItemRepository
			.findAllByOrder_IdOrderByCreatedAtAsc(order.getId())
			.stream()
			.map(this::toItemResponse)
			.toList();
		Payment payment = paymentRepository.findFirstByPaymentGroup_IdOrderByCreatedAtDesc(order.getPaymentGroup().getId())
			.orElse(null);
		Fulfillment fulfillment = fulfillmentRepository.findByOrder_Id(order.getId()).orElse(null);
		Shipment shipment = shipmentRepository.findByOrder_Id(order.getId()).orElse(null);
		Refund refund = refundRepository.findByOrder_Id(order.getId())
			.orElseGet(() -> refundRepository
				.findByPaymentGroup_IdAndRefundScope(order.getPaymentGroup().getId(), RefundScope.PAYMENT_GROUP)
				.orElse(null));
		Claim claim = claimRepository.findFirstByOrder_IdOrderByCreatedAtDesc(order.getId()).orElse(null);
		return toDetailResponse(order, payment, fulfillment, shipment, refund, claim, items);
	}

	@Transactional(readOnly = true)
	AdminOrderDtos.OrderStatusHistoryListResponse listOrderStatusHistory(UUID orderId) {
		orderRepository.findById(orderId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		return new AdminOrderDtos.OrderStatusHistoryListResponse(
			statusHistoryRepository.findAllByOrder_IdOrderByCreatedAtAsc(orderId)
				.stream()
				.map(this::toStatusHistoryResponse)
				.toList()
		);
	}

	@Transactional(readOnly = true)
	AdminOrderDtos.AdminActionHistoryListResponse listAdminActions(UUID orderId) {
		if (orderId != null) {
			orderRepository.findById(orderId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		}
		return new AdminOrderDtos.AdminActionHistoryListResponse(
			(orderId == null
				? actionHistoryRepository.findAllByOrderByCreatedAtDesc()
				: actionHistoryRepository.findAllByOrder_IdOrderByCreatedAtDesc(orderId))
				.stream()
				.map(this::toActionHistoryResponse)
				.toList()
		);
	}

	private AdminOrderDtos.AdminOrderSummaryResponse toSummaryResponse(CustomerOrder order) {
		UserAccount customer = order.getUser();
		Supplier supplier = order.getSupplier();
		return new AdminOrderDtos.AdminOrderSummaryResponse(
			order.getId(),
			order.getOrderNumber(),
			order.getStatus(),
			supplier.getId(),
			supplier.getName(),
			customer.getId(),
			customer.getEmail(),
			order.getPaymentGroup().getCheckoutNumber(),
			orderItemRepository.countByOrder_Id(order.getId()),
			order.getTotalAmount(),
			order.getCreatedAt()
		);
	}

	private AdminOrderDtos.OrderStatusHistoryResponse toStatusHistoryResponse(OrderStatusHistory history) {
		return new AdminOrderDtos.OrderStatusHistoryResponse(
			history.getId(),
			history.getActorUserId(),
			history.getActionType(),
			history.getFromStatus(),
			history.getToStatus(),
			history.getGuardResult(),
			history.getSideEffectSummary(),
			history.getReason(),
			history.getCreatedAt()
		);
	}

	private AdminOrderDtos.AdminActionHistoryResponse toActionHistoryResponse(AdminOrderActionHistory history) {
		return new AdminOrderDtos.AdminActionHistoryResponse(
			history.getId(),
			history.getOrder().getId(),
			history.getAdminUserId(),
			history.getActionType(),
			history.getBeforeStatus(),
			history.getAfterStatus(),
			history.getReason(),
			history.getCreatedAt()
		);
	}

	private AdminOrderDtos.AdminOrderDetailResponse toDetailResponse(
		CustomerOrder order,
		Payment payment,
		Fulfillment fulfillment,
		Shipment shipment,
		Refund refund,
		Claim claim,
		List<AdminOrderDtos.AdminOrderItemResponse> items
	) {
		UserAccount customer = order.getUser();
		Supplier supplier = order.getSupplier();
		return new AdminOrderDtos.AdminOrderDetailResponse(
			order.getId(),
			order.getOrderNumber(),
			order.getStatus(),
			order.getCreatedAt(),
			new AdminOrderDtos.SupplierResponse(
				supplier.getId(),
				supplier.getName(),
				supplier.getContactName(),
				supplier.getPhone(),
				supplier.getEmail()
			),
			new AdminOrderDtos.CustomerResponse(
				customer.getId(),
				customer.getEmail(),
				customer.getDisplayName()
			),
			new AdminOrderDtos.AdminShippingAddressResponse(
				order.getRecipientName(),
				order.getRecipientPhone(),
				order.getPostalCode(),
				order.getAddress1(),
				order.getAddress2(),
				order.getDeliveryMemo()
			),
			new AdminOrderDtos.AdminPaymentGroupResponse(
				order.getPaymentGroup().getId(),
				order.getPaymentGroup().getCheckoutNumber(),
				order.getPaymentGroup().getStatus(),
				order.getPaymentGroup().getTotalAmount(),
				order.getPaymentGroup().getApprovedAmount(),
				order.getPaymentGroup().getApprovedAt(),
				toBankTransferDepositResponse(order.getPaymentGroup())
			),
			toPaymentResponse(payment),
			toFulfillmentResponse(order, fulfillment),
			toShipmentResponse(shipment),
			toRefundResponse(refund),
			toClaimResponse(claim),
			items
		);
	}

	private AdminOrderDtos.AdminPaymentResponse toPaymentResponse(Payment payment) {
		if (payment == null) {
			return null;
		}
		return new AdminOrderDtos.AdminPaymentResponse(
			payment.getId(),
			payment.getProvider(),
			payment.getStatus(),
			payment.getMethod(),
			payment.getRequestedAmount(),
			payment.getApprovedAmount(),
			payment.getApprovedAt()
		);
	}

	private AdminOrderDtos.AdminBankTransferDepositResponse toBankTransferDepositResponse(PaymentGroup paymentGroup) {
		return new AdminOrderDtos.AdminBankTransferDepositResponse(
			paymentGroup.getBankTransferBankName(),
			paymentGroup.getBankTransferAccountNumber(),
			paymentGroup.getBankTransferAccountHolder(),
			paymentGroup.getBankTransferDepositorName(),
			paymentGroup.getBankTransferCashReceiptNotice(),
			paymentGroup.getDepositConfirmedByAdminId(),
			paymentGroup.getDepositConfirmedAt(),
			paymentGroup.getDepositConfirmationReason(),
			paymentGroup.getActualDepositorName(),
			paymentGroup.getActualDepositAmount(),
			paymentGroup.getDepositReceivedAt(),
			paymentGroup.getDepositTransactionReference(),
			paymentGroup.getDepositMismatchMemo(),
			paymentGroup.getDepositMismatchRecordedByAdminId(),
			paymentGroup.getDepositMismatchRecordedAt(),
			paymentGroup.getUnpaidCancelledByAdminId(),
			paymentGroup.getUnpaidCancelledAt(),
			paymentGroup.getUnpaidCancelReason()
		);
	}

	AdminOrderDtos.AdminShipmentResponse toShipmentResponse(Shipment shipment) {
		if (shipment == null) {
			return null;
		}
		return new AdminOrderDtos.AdminShipmentResponse(
			shipment.getId(),
			shipment.getStatus(),
			shipment.getCarrier(),
			shipment.getTrackingNumber(),
			shipment.getShippedAt(),
			shipment.getDeliveredAt(),
			shipment.getTrackingSyncedAt(),
			shipment.getTrackingSyncFailureReason(),
			shipment.isManualOverride(),
			shipment.getManualCorrectedByAdminId(),
			shipment.getManualCorrectedAt(),
			shipment.getManualCorrectionReason()
		);
	}

	AdminOrderDtos.AdminRefundResponse toRefundResponse(Refund refund) {
		if (refund == null) {
			return null;
		}
		return new AdminOrderDtos.AdminRefundResponse(
			refund.getId(),
			refund.getOrder() == null ? null : refund.getOrder().getId(),
			refund.getOrder() == null ? null : refund.getOrder().getOrderNumber(),
			refund.getPaymentGroup().getId(),
			refund.getRefundScope() == RefundScope.PAYMENT_GROUP
				? orderRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(refund.getPaymentGroup().getId())
					.stream().map(CustomerOrder::getId).toList()
				: List.of(refund.getOrder().getId()),
			refund.getReason(),
			refund.getStatus(),
			refund.getRefundAmount(),
			refund.getRefundScope(),
			refund.getProviderPaymentKey(),
			refund.getProviderCancelTransactionKey(),
			refund.getFailureCode(),
			refund.getFailureMessage(),
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
			refund.getFailedAt()
		);
	}

	AdminOrderDtos.AdminClaimResponse toClaimResponse(Claim claim) {
		if (claim == null) {
			return null;
		}
		return new AdminOrderDtos.AdminClaimResponse(
			claim.getId(),
			claim.getClaimType(),
			claim.getClaimReason(),
			claim.getStatus(),
			claim.getRequestedAction(),
			claim.getCustomerMemo(),
			claim.getReviewedByAdminId(),
			claim.getAdminReviewReason(),
			claim.getReviewedAt(),
			claim.getReturnReceivedByAdminId(),
			claim.getReturnReceivedAt(),
			claim.getReturnReceivedMemo(),
			claim.getRefundId(),
			claim.getCompletedAt(),
			claim.getCreatedAt(),
			claimEvidenceRepository.findAllByClaim_IdOrderByUploadedAtAsc(claim.getId()).stream()
				.map(this::toClaimEvidenceResponse)
				.toList()
		);
	}

	private AdminOrderDtos.AdminClaimEvidenceResponse toClaimEvidenceResponse(ClaimEvidence evidence) {
		return new AdminOrderDtos.AdminClaimEvidenceResponse(
			evidence.getId(),
			evidence.getFileUrl(),
			evidence.getOriginalFilename(),
			evidence.getContentType(),
			evidence.getSizeBytes(),
			evidence.getUploadedAt()
		);
	}

	AdminOrderDtos.AdminFulfillmentResponse toFulfillmentResponse(CustomerOrder order, Fulfillment fulfillment) {
		if (fulfillment == null) {
			return new AdminOrderDtos.AdminFulfillmentResponse(
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				order.getSupplierOrderStartedAt(),
				order.getAddressLockedAt(),
				order.getAddressLockedByAdminId(),
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
				null
			);
		}
		return new AdminOrderDtos.AdminFulfillmentResponse(
			fulfillment.getId(),
			fulfillment.getStatus(),
			fulfillment.getChannel(),
			fulfillment.getRequestedAt(),
			fulfillment.getOperationalOwner(),
			fulfillment.getPiiAccessCutoffAt(),
			fulfillment.getHandedOverAt(),
			fulfillment.getHandedOverReason(),
			fulfillment.getHandedOverByAdminId(),
			fulfillment.getSupplierOrderStartedAt(),
			order.getAddressLockedAt(),
			order.getAddressLockedByAdminId(),
			fulfillment.getSupplierOrderNumber(),
			fulfillment.getOrderedByAdminId(),
			fulfillment.getOrderedAt(),
			fulfillment.getExpectedShipDate(),
			fulfillment.getSupplierResponseMemo(),
			fulfillment.getOutOfStockReason(),
			fulfillment.getPurchaseProvider(),
			fulfillment.getPurchaseStatus(),
			fulfillment.getExpectedSourceAmount(),
			fulfillment.getActualSourceAmount(),
			fulfillment.getLastPurchaseError(),
			fulfillment.getPurchaseSyncedAt(),
			fulfillment.getSupplierCancelStatus()
		);
	}

	private AdminOrderDtos.AdminOrderItemResponse toItemResponse(OrderItem item) {
		return new AdminOrderDtos.AdminOrderItemResponse(
			item.getId(),
			item.getProduct().getId(),
			item.getProductOption().getId(),
			item.getProductName(),
			item.getOptionName(),
			item.getQuantity(),
			item.getUnitPrice(),
			item.getLineAmount(),
			item.getProductDetailVersion(),
			item.getProductNoticeVersion()
		);
	}
}
