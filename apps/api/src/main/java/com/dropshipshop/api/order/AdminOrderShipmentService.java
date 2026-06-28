package com.dropshipshop.api.order;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.AdminOrderActionHistory;
import com.dropshipshop.api.order.domain.AdminOrderActionType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.OrderStatusHistory;
import com.dropshipshop.api.order.repository.AdminOrderActionHistoryRepository;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;

@Service
class AdminOrderShipmentService {

	private final CustomerOrderRepository orderRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final ShipmentRepository shipmentRepository;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;
	private final OrderStatusHistoryRepository statusHistoryRepository;
	private final AdminOrderQueryService adminOrderQueryService;

	AdminOrderShipmentService(
		CustomerOrderRepository orderRepository,
		FulfillmentRepository fulfillmentRepository,
		ShipmentRepository shipmentRepository,
		AdminOrderActionHistoryRepository actionHistoryRepository,
		OrderStatusHistoryRepository statusHistoryRepository,
		AdminOrderQueryService adminOrderQueryService
	) {
		this.orderRepository = orderRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.shipmentRepository = shipmentRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.adminOrderQueryService = adminOrderQueryService;
	}

	@Transactional
	AdminOrderDtos.AdminOrderActionResponse createShipment(
		UUID orderId,
		UUID adminUserId,
		AdminOrderDtos.ShipmentCreateRequest request
	) {
		CustomerOrder order = orderRepository.findById(orderId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		OrderStatus beforeStatus = order.getStatus();
		if (shipmentRepository.existsByOrder_Id(order.getId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipment already exists for order");
		}
		try {
			order.markShipped();
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}

		Shipment shipment = shipmentRepository.save(new Shipment(order, request.carrier(), request.trackingNumber(), Instant.now()));
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
		Fulfillment fulfillment = fulfillmentRepository.findByOrder_Id(order.getId()).orElse(null);
		return new AdminOrderDtos.AdminOrderActionResponse(
			order.getId(),
			order.getStatus(),
			adminOrderQueryService.toFulfillmentResponse(order, fulfillment),
			adminOrderQueryService.toShipmentResponse(shipment)
		);
	}
}
