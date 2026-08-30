package com.dropshipshop.api.shipment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.AdminOrderActionHistory;
import com.dropshipshop.api.order.domain.AdminOrderActionType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.OrderStatusHistory;
import com.dropshipshop.api.order.repository.AdminOrderActionHistoryRepository;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.shipment.ShipmentTrackingDtos.InternalTrackingSyncItem;
import com.dropshipshop.api.shipment.ShipmentTrackingDtos.InternalTrackingSyncRequest;
import com.dropshipshop.api.shipment.ShipmentTrackingDtos.InternalTrackingSyncResponse;
import com.dropshipshop.api.shipment.ShipmentTrackingDtos.ManualCorrectionRequest;
import com.dropshipshop.api.shipment.ShipmentTrackingDtos.TrackingSyncRequest;
import com.dropshipshop.api.shipment.ShipmentTrackingDtos.TrackingSyncResponse;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.domain.ShipmentStatus;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;

@Service
class ShipmentTrackingService {

	private static final Comparator<UUID> POSTGRES_UUID_ORDER = (left, right) -> {
		int mostSignificant = Long.compareUnsigned(
			left.getMostSignificantBits(), right.getMostSignificantBits());
		return mostSignificant != 0
			? mostSignificant
			: Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
	};

	private final ShipmentRepository shipmentRepository;
	private final CustomerOrderRepository orderRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;
	private final OrderStatusHistoryRepository statusHistoryRepository;
	private final NotificationService notificationService;

	ShipmentTrackingService(
		ShipmentRepository shipmentRepository,
		CustomerOrderRepository orderRepository,
		FulfillmentRepository fulfillmentRepository,
		AdminOrderActionHistoryRepository actionHistoryRepository,
		OrderStatusHistoryRepository statusHistoryRepository,
		NotificationService notificationService
	) {
		this.shipmentRepository = shipmentRepository;
		this.orderRepository = orderRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.notificationService = notificationService;
	}

	@Transactional
	TrackingSyncResponse syncShipment(UUID shipmentId, TrackingSyncRequest request) {
		Shipment shipment = lockLegacyShipment(shipmentId);
		sync(shipment, request.trackingStatus(), request.failureReason());
		return toResponse(shipment);
	}

	@Transactional
	TrackingSyncResponse manuallyCorrectShipment(UUID shipmentId, UUID adminUserId, ManualCorrectionRequest request) {
		if (request.status() != ShipmentStatus.DELIVERED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only delivered correction is supported");
		}
		Shipment shipment = lockLegacyShipment(shipmentId);
		OrderStatus beforeStatus = shipment.getOrder().getStatus();
		try {
			boolean delivered = shipment.markDeliveredByManualCorrection(Instant.now(), adminUserId, request.reason());
			if (delivered) {
				shipment.getOrder().markDeliveredByAdminCorrection();
				notifyDelivered(shipment);
			}
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
		actionHistoryRepository.save(new AdminOrderActionHistory(
			shipment.getOrder(),
			adminUserId,
			AdminOrderActionType.SHIPMENT_MANUAL_CORRECTION,
			beforeStatus,
			shipment.getOrder().getStatus(),
			request.reason()
		));
		recordStatusHistory(
			shipment,
			adminUserId,
			AdminOrderActionType.SHIPMENT_MANUAL_CORRECTION.name(),
			beforeStatus,
			"Manual shipment correction",
			request.reason()
		);
		return toResponse(shipment);
	}

	@Transactional
	InternalTrackingSyncResponse syncInternal(InternalTrackingSyncRequest request) {
		int matched = 0;
		int delivered = 0;
		int failed = 0;
		int notFound = 0;
		List<InternalSyncWorkItem> workItems = new ArrayList<>();
		for (int requestIndex = 0; requestIndex < request.shipments().size(); requestIndex++) {
			InternalTrackingSyncItem item = request.shipments().get(requestIndex);
			List<Shipment> shipments = shipmentRepository.findAllByCarrierAndTrackingNumber(item.carrier(), item.trackingNumber());
			if (shipments.isEmpty()) {
				notFound++;
				continue;
			}
			for (Shipment shipment : shipments) {
				workItems.add(new InternalSyncWorkItem(
					shipment.getOrder().getId(), shipment.getId(), requestIndex, item
				));
			}
		}
		workItems.sort(Comparator
			.comparing(InternalSyncWorkItem::orderId, POSTGRES_UUID_ORDER)
			.thenComparing(InternalSyncWorkItem::shipmentId, POSTGRES_UUID_ORDER)
			.thenComparingInt(InternalSyncWorkItem::requestIndex));

		for (InternalSyncWorkItem workItem : workItems) {
			try {
				Shipment shipment = lockLegacyShipment(workItem.shipmentId());
				SyncResult result = sync(
					shipment, workItem.item().trackingStatus(), workItem.item().failureReason());
				matched++;
				if (result.delivered()) {
					delivered++;
				}
				if (result.failed()) {
					failed++;
				}
			} catch (ResponseStatusException exception) {
				if (exception.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
					throw exception;
				}
				matched++;
				failed++;
			}
		}
		return new InternalTrackingSyncResponse(request.shipments().size(), matched, delivered, failed, notFound);
	}

	private Shipment lockLegacyShipment(UUID shipmentId) {
		UUID orderId = shipmentRepository.findOrderIdByShipmentId(shipmentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
		CustomerOrder order = orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
		Fulfillment fulfillment = fulfillmentRepository.findByOrderIdForUpdate(orderId).orElse(null);
		if (fulfillment != null && fulfillment.getChannel() == FulfillmentChannel.SUPPLIER_PORTAL) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Supplier portal fulfillment requires the portal shipment workflow");
		}
		Shipment shipment = shipmentRepository.findAllByOrderIdForUpdate(order.getId()).stream()
			.filter(candidate -> candidate.getId().equals(shipmentId))
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
		if (shipment.isPortal()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
				"Portal shipment requires the versioned portal shipment workflow");
		}
		return shipment;
	}

	private SyncResult sync(Shipment shipment, String trackingStatus, String failureReason) {
		if (isBlank(trackingStatus) && isBlank(failureReason)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trackingStatus or failureReason is required");
		}
		Instant now = Instant.now();
		if (!isBlank(failureReason)) {
			shipment.recordTrackingSyncFailure(now, failureReason);
			return new SyncResult(false, true);
		}
		if ("DELIVERED".equalsIgnoreCase(trackingStatus)) {
			boolean delivered = shipment.markDeliveredByTracking(now);
			if (delivered) {
				OrderStatus beforeStatus = shipment.getOrder().getStatus();
				shipment.getOrder().markDeliveredByTracking();
				recordStatusHistory(shipment, null, "SHIPMENT_TRACKING_SYNC", beforeStatus, "Tracking status delivered", null);
				notifyDelivered(shipment);
			}
			return new SyncResult(delivered, false);
		}
		shipment.markTrackingSynced(now);
		return new SyncResult(false, false);
	}

	private TrackingSyncResponse toResponse(Shipment shipment) {
		return new TrackingSyncResponse(
			shipment.getId(),
			shipment.getStatus(),
			shipment.getOrder().getStatus(),
			shipment.getTrackingSyncedAt(),
			shipment.getTrackingSyncFailureReason(),
			shipment.getManualCorrectionReason()
		);
	}

	private void recordStatusHistory(
		Shipment shipment,
		UUID actorUserId,
		String actionType,
		OrderStatus beforeStatus,
		String sideEffectSummary,
		String reason
	) {
		if (beforeStatus == shipment.getOrder().getStatus()) {
			return;
		}
		statusHistoryRepository.save(new OrderStatusHistory(
			shipment.getOrder(),
			actorUserId,
			actionType,
			beforeStatus,
			shipment.getOrder().getStatus(),
			"ALLOWED",
			sideEffectSummary,
			reason
		));
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private void notifyDelivered(Shipment shipment) {
		if (!notificationService.exists(shipment.getOrder(), NotificationType.DELIVERY_COMPLETED)) {
			notificationService.transactionalSms(
				shipment.getOrder().getUser(),
				shipment.getOrder(),
				shipment.getOrder().getPaymentGroup(),
				null,
				null,
				NotificationType.DELIVERY_COMPLETED
			);
		}
	}

	private record SyncResult(boolean delivered, boolean failed) {
	}

	private record InternalSyncWorkItem(
		UUID orderId,
		UUID shipmentId,
		int requestIndex,
		InternalTrackingSyncItem item
	) {
	}
}
