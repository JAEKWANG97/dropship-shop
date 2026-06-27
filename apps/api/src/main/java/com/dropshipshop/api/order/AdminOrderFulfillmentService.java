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
import com.dropshipshop.api.order.repository.AdminOrderActionHistoryRepository;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.refund.RefundService;

@Service
class AdminOrderFulfillmentService {

	private final CustomerOrderRepository orderRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;
	private final AdminOrderQueryService adminOrderQueryService;
	private final RefundService refundService;

	AdminOrderFulfillmentService(
		CustomerOrderRepository orderRepository,
		FulfillmentRepository fulfillmentRepository,
		AdminOrderActionHistoryRepository actionHistoryRepository,
		AdminOrderQueryService adminOrderQueryService,
		RefundService refundService
	) {
		this.orderRepository = orderRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.adminOrderQueryService = adminOrderQueryService;
		this.refundService = refundService;
	}

	@Transactional
	AdminOrderDtos.AdminOrderActionResponse startSupplierWork(
		UUID orderId,
		UUID adminUserId,
		AdminOrderDtos.SupplierWorkStartRequest request
	) {
		CustomerOrder order = findOrder(orderId);
		OrderStatus beforeStatus = order.getStatus();
		Instant now = Instant.now();
		try {
			order.startSupplierOrderWork(adminUserId, now);
			Fulfillment fulfillment = getOrCreateFulfillment(order);
			fulfillment.startWork(now);
			fulfillmentRepository.save(fulfillment);
			recordHistory(order, adminUserId, AdminOrderActionType.SUPPLIER_WORK_START, beforeStatus, request.reason());
			return actionResponse(order, fulfillment);
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	@Transactional
	AdminOrderDtos.AdminOrderActionResponse markSupplierOrderCompleted(
		UUID orderId,
		UUID adminUserId,
		AdminOrderDtos.SupplierOrderCompletedRequest request
	) {
		CustomerOrder order = findOrder(orderId);
		OrderStatus beforeStatus = order.getStatus();
		Instant now = Instant.now();
		try {
			Fulfillment fulfillment = fulfillmentRepository.findByOrder_Id(order.getId())
				.orElseThrow(() -> new IllegalStateException("Supplier work must start before supplier ordering"));
			order.markSupplierOrdered();
			fulfillment.markOrdered(
				request.supplierOrderNumber(),
				orderedAddressSnapshot(order),
				adminUserId,
				request.expectedShipDate(),
				request.supplierResponseMemo(),
				now
			);
			recordHistory(order, adminUserId, AdminOrderActionType.SUPPLIER_ORDER_COMPLETED, beforeStatus, request.reason());
			return actionResponse(order, fulfillment);
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	@Transactional
	AdminOrderDtos.AdminOrderActionResponse markOutOfStock(
		UUID orderId,
		UUID adminUserId,
		AdminOrderDtos.OutOfStockRequest request
	) {
		CustomerOrder order = findOrder(orderId);
		OrderStatus beforeStatus = order.getStatus();
		try {
			order.markOutOfStock();
			Fulfillment fulfillment = getOrCreateFulfillment(order);
			fulfillment.markOutOfStock(request.reason());
			fulfillmentRepository.save(fulfillment);
			refundService.createOutOfStockRefund(order);
			recordHistory(order, adminUserId, AdminOrderActionType.OUT_OF_STOCK, beforeStatus, request.reason());
			return actionResponse(order, fulfillment);
		} catch (IllegalStateException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	private CustomerOrder findOrder(UUID orderId) {
		return orderRepository.findById(orderId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
	}

	private Fulfillment getOrCreateFulfillment(CustomerOrder order) {
		return fulfillmentRepository.findByOrder_Id(order.getId())
			.orElseGet(() -> new Fulfillment(order));
	}

	private void recordHistory(
		CustomerOrder order,
		UUID adminUserId,
		AdminOrderActionType actionType,
		OrderStatus beforeStatus,
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
	}

	private AdminOrderDtos.AdminOrderActionResponse actionResponse(CustomerOrder order, Fulfillment fulfillment) {
		return new AdminOrderDtos.AdminOrderActionResponse(
			order.getId(),
			order.getStatus(),
			adminOrderQueryService.toFulfillmentResponse(order, fulfillment),
			null
		);
	}

	private String orderedAddressSnapshot(CustomerOrder order) {
		return """
			recipientName: %s
			recipientPhone: %s
			postalCode: %s
			address1: %s
			address2: %s
			""".formatted(
			order.getRecipientName(),
			order.getRecipientPhone(),
			order.getPostalCode(),
			order.getAddress1(),
			order.getAddress2() == null ? "" : order.getAddress2()
		);
	}
}
