package com.dropshipshop.api.order;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.fulfillment.domain.FulfillmentStatus;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.refund.domain.Refund;
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

	public CustomerOrderQueryService(
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		PaymentRepository paymentRepository,
		ShipmentRepository shipmentRepository,
		RefundRepository refundRepository
	) {
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.paymentRepository = paymentRepository;
		this.shipmentRepository = shipmentRepository;
		this.refundRepository = refundRepository;
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
		Refund refund = refundRepository.findByOrder_Id(order.getId()).orElse(null);
		return new OrderDtos.OrderDetailResponse(
			order.getId(),
			order.getOrderNumber(),
			order.getStatus(),
			order.getSubtotalAmount(),
			order.getShippingFee(),
			order.getDiscountAmount(),
			order.getTotalAmount(),
			order.getCreatedAt(),
			new OrderDtos.PaymentGroupSummaryResponse(
				order.getPaymentGroup().getId(),
				order.getPaymentGroup().getCheckoutNumber(),
				order.getPaymentGroup().getStatus(),
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
			new OrderDtos.FulfillmentSummaryResponse(FulfillmentStatus.PENDING),
			toShipmentSummary(shipment),
			toRefundSummary(refund)
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
}
