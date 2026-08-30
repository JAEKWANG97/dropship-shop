package com.dropshipshop.api.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
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
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.domain.ShipmentItem;
import com.dropshipshop.api.shipment.repository.ShipmentItemRepository;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;

@Service
class AdminOrderShipmentService {

	private final CustomerOrderRepository orderRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final ShipmentRepository shipmentRepository;
	private final ShipmentItemRepository shipmentItemRepository;
	private final OrderItemRepository orderItemRepository;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;
	private final OrderStatusHistoryRepository statusHistoryRepository;
	private final AdminOrderQueryService adminOrderQueryService;
	private final NotificationService notificationService;

	AdminOrderShipmentService(
		CustomerOrderRepository orderRepository,
		FulfillmentRepository fulfillmentRepository,
		ShipmentRepository shipmentRepository,
		ShipmentItemRepository shipmentItemRepository,
		OrderItemRepository orderItemRepository,
		AdminOrderActionHistoryRepository actionHistoryRepository,
		OrderStatusHistoryRepository statusHistoryRepository,
		AdminOrderQueryService adminOrderQueryService,
		NotificationService notificationService
	) {
		this.orderRepository = orderRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.shipmentRepository = shipmentRepository;
		this.shipmentItemRepository = shipmentItemRepository;
		this.orderItemRepository = orderItemRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.adminOrderQueryService = adminOrderQueryService;
		this.notificationService = notificationService;
	}

	@Transactional
	AdminOrderDtos.AdminOrderActionResponse createShipment(
		UUID orderId,
		UUID adminUserId,
		AdminOrderDtos.ShipmentCreateRequest request
	) {
		CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		Fulfillment fulfillment = fulfillmentRepository.findByOrderIdForUpdate(orderId).orElse(null);
		if (fulfillment != null && fulfillment.getChannel() == FulfillmentChannel.SUPPLIER_PORTAL) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Supplier portal fulfillment requires the portal shipment workflow");
		}
		List<Shipment> existingShipments = shipmentRepository.findAllByOrderIdForUpdate(order.getId());
		List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdForUpdate(order.getId());
		OrderStatus beforeStatus = order.getStatus();
		if (!existingShipments.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipment already exists for order");
		}
		if (orderItems.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Order items are required for shipment allocation");
		}
		try {
			order.markShipped();
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}

		Shipment shipment = shipmentRepository.saveAndFlush(
			new Shipment(order, request.carrier(), request.trackingNumber(), Instant.now())
		);
		shipmentItemRepository.saveAll(orderItems.stream()
			.map(item -> new ShipmentItem(shipment, item, item.getQuantity()))
			.toList());
		notificationService.transactionalSms(order.getUser(), order, order.getPaymentGroup(), null, null, NotificationType.SHIPMENT_STARTED);
		actionHistoryRepository.save(new AdminOrderActionHistory(
			order,
			adminUserId,
			AdminOrderActionType.SHIPMENT_STARTED,
			beforeStatus,
			order.getStatus(),
			"Shipment tracking entered"
		));
		statusHistoryRepository.save(new OrderStatusHistory(
			order,
			adminUserId,
			AdminOrderActionType.SHIPMENT_STARTED.name(),
			beforeStatus,
			order.getStatus(),
			"ALLOWED",
			"Shipment tracking entered",
			"Shipment tracking entered"
		));
		return new AdminOrderDtos.AdminOrderActionResponse(
			order.getId(),
			order.getStatus(),
			adminOrderQueryService.toFulfillmentResponse(order, fulfillment),
			adminOrderQueryService.toShipmentResponse(shipment)
		);
	}
}
