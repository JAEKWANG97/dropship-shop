package com.dropshipshop.api.order;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.claim.domain.ClaimEvidence;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.repository.ClaimEvidenceRepository;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.fulfillment.domain.FulfillmentStatus;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.refund.domain.RefundReason;
import com.dropshipshop.api.refund.domain.RefundScope;
import com.dropshipshop.api.refund.domain.RefundStatus;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.domain.ShipmentStatus;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;

@Service
public class CustomerOrderQueryService {

	private static final EnumSet<OrderStatus> CUSTOMER_HISTORY_STATUSES = EnumSet.of(
		OrderStatus.PAYMENT_EXCEPTION,
		OrderStatus.SUPPLIER_ORDER_PENDING,
		OrderStatus.SUPPLIER_ORDERED,
		OrderStatus.OUT_OF_STOCK,
		OrderStatus.SHIPPED,
		OrderStatus.DELIVERED,
		OrderStatus.CANCELLED,
		OrderStatus.REFUND_REQUESTED,
		OrderStatus.REFUNDED
	);

	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final PaymentRepository paymentRepository;
	private final ShipmentRepository shipmentRepository;
	private final RefundRepository refundRepository;
	private final ClaimRepository claimRepository;
	private final ClaimEvidenceRepository claimEvidenceRepository;

	public CustomerOrderQueryService(
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		PaymentRepository paymentRepository,
		ShipmentRepository shipmentRepository,
		RefundRepository refundRepository,
		ClaimRepository claimRepository,
		ClaimEvidenceRepository claimEvidenceRepository
	) {
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.paymentRepository = paymentRepository;
		this.shipmentRepository = shipmentRepository;
		this.refundRepository = refundRepository;
		this.claimRepository = claimRepository;
		this.claimEvidenceRepository = claimEvidenceRepository;
	}

	@Transactional(readOnly = true)
	public OrderDtos.OrderListResponse listOrders(UUID userId) {
		List<OrderDtos.OrderSummaryResponse> orders = orderRepository
			.findAllByUser_IdAndStatusInOrderByCreatedAtDesc(userId, CUSTOMER_HISTORY_STATUSES)
			.stream()
			.map(this::toSummaryResponse)
			.toList();
		return new OrderDtos.OrderListResponse(orders);
	}

	@Transactional(readOnly = true)
	public OrderDtos.OrderDetailResponse getOrder(UUID userId, UUID orderId) {
		CustomerOrder order = orderRepository.findByIdAndUser_Id(orderId, userId)
			.filter(this::isCustomerVisible)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		return toDetailResponse(order);
	}

	private boolean isCustomerVisible(CustomerOrder order) {
		return CUSTOMER_HISTORY_STATUSES.contains(order.getStatus());
	}

	private OrderDtos.OrderSummaryResponse toSummaryResponse(CustomerOrder order) {
		return new OrderDtos.OrderSummaryResponse(
			order.getId(),
			order.getOrderNumber(),
			order.getPaymentGroup().getId(),
			order.getPaymentGroup().getCheckoutNumber(),
			order.getStatus(),
			order.getTotalAmount(),
			order.getCreatedAt()
		);
	}

	private OrderDtos.OrderDetailResponse toDetailResponse(CustomerOrder order) {
		List<OrderDtos.OrderItemResponse> items = orderItemRepository
			.findAllByOrder_IdOrderByCreatedAtAsc(order.getId())
			.stream()
			.map(this::toItemResponse)
			.toList();
		Payment payment = paymentRepository.findFirstByPaymentGroup_IdOrderByCreatedAtDesc(order.getPaymentGroup().getId())
			.orElse(null);
		Shipment shipment = shipmentRepository.findByOrder_Id(order.getId()).orElse(null);
		Refund refund = refundRepository.findByOrder_Id(order.getId())
			.orElseGet(() -> refundRepository
				.findByPaymentGroup_IdAndRefundScope(order.getPaymentGroup().getId(), RefundScope.PAYMENT_GROUP)
				.orElse(null));
		CustomerRefundProjection customerRefund = customerRefundProjection(refund);
		Claim claim = claimRepository.findFirstByOrder_IdOrderByCreatedAtDesc(order.getId()).orElse(null);
		List<OrderDtos.ClaimSummaryResponse> claims = claimRepository.findAllByOrder_IdOrderByCreatedAtAsc(order.getId())
			.stream()
			.map(this::toClaimSummary)
			.toList();
		return new OrderDtos.OrderDetailResponse(
			order.getId(),
			order.getOrderNumber(),
			order.getStatus(),
			order.getSubtotalAmount(),
			order.getShippingFee(),
			order.getDiscountAmount(),
			order.getTotalAmount(),
			order.getCreatedAt(),
			customerRefund.status(),
			customerRefund.label(),
			customerRefund.amount(),
			new OrderDtos.PaymentGroupSummaryResponse(
				order.getPaymentGroup().getId(),
				order.getPaymentGroup().getCheckoutNumber(),
				order.getPaymentGroup().getStatus(),
				order.getPaymentGroup().getTotalAmount(),
				order.getPaymentGroup().getApprovedAmount(),
				order.getPaymentGroup().getApprovedAt(),
				customerRefund.status(),
				customerRefund.label(),
				customerRefund.amount()
			),
			toPaymentSummary(payment),
			new OrderDtos.ShippingAddressResponse(
				order.getRecipientName(),
				order.getRecipientPhone(),
				order.getPostalCode(),
				order.getAddress1(),
				order.getAddress2()
			),
			items,
			new OrderDtos.FulfillmentSummaryResponse(FulfillmentStatus.PENDING),
			toShipmentSummary(shipment),
			toRefundSummary(refund),
			claims,
			toClaimSummary(claim)
		);
	}

	private OrderDtos.ClaimSummaryResponse toClaimSummary(Claim claim) {
		if (claim == null) {
			return null;
		}
		return new OrderDtos.ClaimSummaryResponse(
			claim.getId(),
			claim.getClaimType(),
			claim.getClaimReason(),
			claim.getStatus(),
			customerStatus(claim.getStatus()),
			customerStatusLabel(claim.getStatus()),
			claim.getRequestedAction(),
			claim.getCustomerMemo(),
			claim.getAdminReviewReason(),
			claim.getReviewedAt(),
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

	private OrderDtos.ClaimEvidenceResponse toClaimEvidenceResponse(ClaimEvidence evidence) {
		return new OrderDtos.ClaimEvidenceResponse(
			evidence.getId(),
			evidence.getFileUrl(),
			evidence.getOriginalFilename(),
			evidence.getContentType(),
			evidence.getSizeBytes(),
			evidence.getUploadedAt()
		);
	}

	private OrderDtos.RefundSummaryResponse toRefundSummary(Refund refund) {
		if (refund == null) {
			return new OrderDtos.RefundSummaryResponse(null, null);
		}
		return new OrderDtos.RefundSummaryResponse(
			refund.getStatus(),
			refund.getRefundAmount()
		);
	}

	private CustomerRefundProjection customerRefundProjection(Refund refund) {
		if (refund == null || !isReceivedPaymentException(refund)) {
			return CustomerRefundProjection.none();
		}
		boolean completed = refund.getStatus() == RefundStatus.COMPLETED;
		return new CustomerRefundProjection(
			completed ? "REFUNDED" : "REFUND_PROCESSING",
			completed ? "환불 완료" : "입금 확인 및 환불 처리 중",
			refund.getRefundAmount()
		);
	}

	private boolean isReceivedPaymentException(Refund refund) {
		return refund.getReason() == RefundReason.PAYMENT_AMOUNT_MISMATCH
			|| refund.getReason() == RefundReason.LATE_DEPOSIT_EXCEPTION
			|| refund.getReason() == RefundReason.SALE_UNAVAILABLE_AT_DEPOSIT;
	}

	private record CustomerRefundProjection(String status, String label, Long amount) {
		static CustomerRefundProjection none() {
			return new CustomerRefundProjection(null, null, null);
		}
	}

	private OrderDtos.ShipmentSummaryResponse toShipmentSummary(Shipment shipment) {
		if (shipment == null) {
			return new OrderDtos.ShipmentSummaryResponse(ShipmentStatus.READY, null, null);
		}
		return new OrderDtos.ShipmentSummaryResponse(
			shipment.getStatus(),
			shipment.getCarrier(),
			shipment.getTrackingNumber()
		);
	}

	private OrderDtos.PaymentSummaryResponse toPaymentSummary(Payment payment) {
		if (payment == null) {
			return new OrderDtos.PaymentSummaryResponse(null, null, null, null);
		}
		return new OrderDtos.PaymentSummaryResponse(
			payment.getId(),
			payment.getStatus(),
			payment.getApprovedAmount(),
			payment.getApprovedAt()
		);
	}

	private OrderDtos.OrderItemResponse toItemResponse(OrderItem item) {
		return new OrderDtos.OrderItemResponse(
			item.getId(),
			item.getProductName(),
			item.getProductSummary(),
			item.getOptionName(),
			item.getQuantity(),
			item.getUnitPrice(),
			item.getLineAmount(),
			item.getProductDetailVersion(),
			item.getProductNoticeVersion()
		);
	}

	private String customerStatus(ClaimStatus status) {
		return switch (status) {
			case REQUESTED, UNDER_REVIEW -> "REVIEWING";
			case EVIDENCE_REQUESTED -> "EVIDENCE_REQUESTED";
			case APPROVED -> "APPROVED";
			case REJECTED -> "REJECTED";
			case RETURN_WAITING -> "RETURN_WAITING";
			case RETURN_RECEIVED -> "RETURN_RECEIVED";
			case REFUND_PROCESSING -> "REFUND_PROCESSING";
			case EXCHANGE_SHIPPING -> "EXCHANGE_SHIPPING";
			case COMPLETED -> "COMPLETED";
			case WITHDRAWN -> "WITHDRAWN";
		};
	}

	private String customerStatusLabel(ClaimStatus status) {
		return switch (status) {
			case REQUESTED, UNDER_REVIEW -> "검토 중";
			case EVIDENCE_REQUESTED -> "증빙 요청";
			case APPROVED -> "승인됨";
			case REJECTED -> "거부됨";
			case RETURN_WAITING -> "반송 대기";
			case RETURN_RECEIVED -> "반품 수령됨";
			case REFUND_PROCESSING -> "환불 처리 중";
			case EXCHANGE_SHIPPING -> "교환 배송 중";
			case COMPLETED -> "완료";
			case WITHDRAWN -> "철회됨";
		};
	}
}
