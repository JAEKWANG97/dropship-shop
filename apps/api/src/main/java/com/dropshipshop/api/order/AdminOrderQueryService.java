package com.dropshipshop.api.order;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;
import com.dropshipshop.api.user.domain.UserAccount;

@Service
class AdminOrderQueryService {

	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final PaymentRepository paymentRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final ShipmentRepository shipmentRepository;

	AdminOrderQueryService(
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		PaymentRepository paymentRepository,
		FulfillmentRepository fulfillmentRepository,
		ShipmentRepository shipmentRepository
	) {
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.paymentRepository = paymentRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.shipmentRepository = shipmentRepository;
	}

	@Transactional(readOnly = true)
	AdminOrderDtos.AdminOrderListResponse listSupplierOrderPendingOrders() {
		List<AdminOrderDtos.AdminOrderSummaryResponse> orders = orderRepository
			.findAllByStatusOrderByCreatedAtAsc(OrderStatus.SUPPLIER_ORDER_PENDING)
			.stream()
			.map(this::toSummaryResponse)
			.toList();
		return new AdminOrderDtos.AdminOrderListResponse(orders);
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
		return toDetailResponse(order, payment, fulfillment, shipment, items);
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
			order.getTotalAmount(),
			order.getCreatedAt()
		);
	}

	private AdminOrderDtos.AdminOrderDetailResponse toDetailResponse(
		CustomerOrder order,
		Payment payment,
		Fulfillment fulfillment,
		Shipment shipment,
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
				order.getAddress2()
			),
			new AdminOrderDtos.AdminPaymentGroupResponse(
				order.getPaymentGroup().getId(),
				order.getPaymentGroup().getCheckoutNumber(),
				order.getPaymentGroup().getStatus(),
				order.getPaymentGroup().getTotalAmount(),
				order.getPaymentGroup().getApprovedAmount(),
				order.getPaymentGroup().getApprovedAt()
			),
			toPaymentResponse(payment),
			toFulfillmentResponse(order, fulfillment),
			toShipmentResponse(shipment),
			items
		);
	}

	private AdminOrderDtos.AdminPaymentResponse toPaymentResponse(Payment payment) {
		if (payment == null) {
			return null;
		}
		return new AdminOrderDtos.AdminPaymentResponse(
			payment.getId(),
			payment.getStatus(),
			payment.getMethod(),
			payment.getRequestedAmount(),
			payment.getApprovedAmount(),
			payment.getApprovedAt()
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
			shipment.getManualCorrectionReason()
		);
	}

	AdminOrderDtos.AdminFulfillmentResponse toFulfillmentResponse(CustomerOrder order, Fulfillment fulfillment) {
		if (fulfillment == null) {
			return new AdminOrderDtos.AdminFulfillmentResponse(
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
				null
			);
		}
		return new AdminOrderDtos.AdminFulfillmentResponse(
			fulfillment.getId(),
			fulfillment.getStatus(),
			fulfillment.getSupplierOrderStartedAt(),
			order.getAddressLockedAt(),
			order.getAddressLockedByAdminId(),
			fulfillment.getSupplierOrderNumber(),
			fulfillment.getOrderedByAdminId(),
			fulfillment.getOrderedAt(),
			fulfillment.getExpectedShipDate(),
			fulfillment.getSupplierResponseMemo(),
			fulfillment.getOutOfStockReason()
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
