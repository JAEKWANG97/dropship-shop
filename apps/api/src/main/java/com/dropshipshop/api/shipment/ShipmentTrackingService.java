package com.dropshipshop.api.shipment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.order.domain.AdminOrderActionHistory;
import com.dropshipshop.api.order.domain.AdminOrderActionType;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.OrderStatusHistory;
import com.dropshipshop.api.order.repository.AdminOrderActionHistoryRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
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

	private final ShipmentRepository shipmentRepository;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;
	private final OrderStatusHistoryRepository statusHistoryRepository;

	ShipmentTrackingService(
		ShipmentRepository shipmentRepository,
		AdminOrderActionHistoryRepository actionHistoryRepository,
		OrderStatusHistoryRepository statusHistoryRepository
	) {
		this.shipmentRepository = shipmentRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.statusHistoryRepository = statusHistoryRepository;
	}

	@Transactional
	TrackingSyncResponse syncShipment(UUID shipmentId, TrackingSyncRequest request) {
		Shipment shipment = shipmentRepository.findById(shipmentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
		sync(shipment, request.trackingStatus(), request.failureReason());
		return toResponse(shipment);
	}

	@Transactional
	TrackingSyncResponse manuallyCorrectShipment(UUID shipmentId, UUID adminUserId, ManualCorrectionRequest request) {
		if (request.status() != ShipmentStatus.DELIVERED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only delivered correction is supported");
		}
		Shipment shipment = shipmentRepository.findById(shipmentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shipment not found"));
		OrderStatus beforeStatus = shipment.getOrder().getStatus();
		try {
			boolean delivered = shipment.markDeliveredByManualCorrection(Instant.now(), adminUserId, request.reason());
			if (delivered) {
				shipment.getOrder().markDeliveredByAdminCorrection();
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
		for (InternalTrackingSyncItem item : request.shipments()) {
			List<Shipment> shipments = shipmentRepository.findAllByCarrierAndTrackingNumber(item.carrier(), item.trackingNumber());
			if (shipments.isEmpty()) {
				notFound++;
				continue;
			}
			for (Shipment shipment : shipments) {
				SyncResult result = sync(shipment, item.trackingStatus(), item.failureReason());
				matched++;
				if (result.delivered()) {
					delivered++;
				}
				if (result.failed()) {
					failed++;
				}
			}
		}
		return new InternalTrackingSyncResponse(request.shipments().size(), matched, delivered, failed, notFound);
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

	private record SyncResult(boolean delivered, boolean failed) {
	}
}
