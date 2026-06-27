package com.dropshipshop.api.order;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentStatus;
import com.dropshipshop.api.payment.repository.PaymentRepository;

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

	public CustomerOrderQueryService(
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		PaymentRepository paymentRepository
	) {
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.paymentRepository = paymentRepository;
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
			displayStatus(order.getStatus()),
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
		return new OrderDtos.OrderDetailResponse(
			order.getId(),
			order.getOrderNumber(),
			displayStatus(order.getStatus()),
			order.getSubtotalAmount(),
			order.getShippingFee(),
			order.getDiscountAmount(),
			order.getTotalAmount(),
			order.getCreatedAt(),
			new OrderDtos.PaymentGroupSummaryResponse(
				order.getPaymentGroup().getId(),
				order.getPaymentGroup().getCheckoutNumber(),
				paymentGroupDisplayStatus(order.getPaymentGroup().getStatus()),
				order.getPaymentGroup().getTotalAmount(),
				order.getPaymentGroup().getApprovedAmount(),
				order.getPaymentGroup().getApprovedAt()
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
			new OrderDtos.FulfillmentSummaryResponse("발주 대기"),
			new OrderDtos.ShipmentSummaryResponse("배송 전", null, null),
			new OrderDtos.RefundSummaryResponse("환불 없음", null)
		);
	}

	private OrderDtos.PaymentSummaryResponse toPaymentSummary(Payment payment) {
		if (payment == null) {
			return new OrderDtos.PaymentSummaryResponse(null, "결제 정보 없음", null, null);
		}
		return new OrderDtos.PaymentSummaryResponse(
			payment.getId(),
			paymentDisplayStatus(payment.getStatus()),
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

	private String displayStatus(OrderStatus status) {
		return switch (status) {
			case PAYMENT_PENDING -> "결제 대기";
			case EXPIRED -> "주문 만료";
			case PAYMENT_EXCEPTION -> "결제 확인 중";
			case SUPPLIER_ORDER_PENDING -> "결제 완료";
			case SUPPLIER_ORDERED -> "상품 준비 중";
			case SHIPPED -> "배송 중";
			case DELIVERED -> "배송 완료";
			case OUT_OF_STOCK -> "품절 안내";
			case CANCELLED -> "취소 완료";
			case REFUND_REQUESTED -> "환불 처리 중";
			case REFUNDED -> "환불 완료";
		};
	}

	private String paymentDisplayStatus(PaymentStatus status) {
		return switch (status) {
			case APPROVED -> "결제 완료";
			case CANCEL_REQUIRED, CANCEL_REQUESTED, CANCEL_FAILED, REVIEW_REQUIRED -> "결제 확인 중";
			case CANCELLED -> "결제 취소 완료";
			case REFUND_REQUESTED, PARTIALLY_REFUNDED, REFUNDED, REFUND_FAILED -> "환불 처리 중";
			case READY -> "결제 대기";
			case FAILED -> "결제 실패";
		};
	}

	private String paymentGroupDisplayStatus(com.dropshipshop.api.payment.domain.PaymentGroupStatus status) {
		return switch (status) {
			case PAYMENT_PENDING -> "결제 대기";
			case APPROVED -> "결제 완료";
			case PARTIALLY_REFUNDED -> "부분 환불";
			case REFUNDED -> "환불 완료";
			case PAYMENT_EXCEPTION -> "결제 확인 중";
			case EXPIRED -> "주문 만료";
			case CANCELLED -> "취소 완료";
			case CANCEL_FAILED -> "결제 취소 확인 필요";
		};
	}
}
