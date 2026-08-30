package com.dropshipshop.api.shipment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.fulfillment.SupplierFulfillmentHandoverService;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.OrderStatusHistory;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.domain.ShipmentActorType;
import com.dropshipshop.api.shipment.domain.ShipmentChangeAction;
import com.dropshipshop.api.shipment.domain.ShipmentChangeHistory;
import com.dropshipshop.api.shipment.domain.ShipmentItem;
import com.dropshipshop.api.shipment.domain.ShipmentStatus;
import com.dropshipshop.api.shipment.repository.ShipmentChangeHistoryRepository;
import com.dropshipshop.api.shipment.repository.ShipmentItemRepository;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.SupplierPortalInputPolicy;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PortalShipmentService {

	private static final Set<OrderStatus> PORTAL_ACTION_STATUSES = Set.of(
		OrderStatus.SUPPLIER_ORDER_PENDING,
		OrderStatus.TRACKING_REGISTERED
	);

	private final SupplierRepository supplierRepository;
	private final UserAccountRepository userAccountRepository;
	private final CustomerOrderRepository orderRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final OrderItemRepository orderItemRepository;
	private final OrderStatusHistoryRepository orderStatusHistoryRepository;
	private final ShipmentRepository shipmentRepository;
	private final ShipmentItemRepository shipmentItemRepository;
	private final ShipmentChangeHistoryRepository historyRepository;
	private final ClaimRepository claimRepository;
	private final RefundRepository refundRepository;
	private final CarrierRegistry carrierRegistry;
	private final SupplierPortalFeatureGate featureGate;
	private final SupplierPortalInputPolicy inputPolicy;
	private final SupplierPortalHasher hasher;
	private final ObjectMapper objectMapper;
	private final SupplierFulfillmentHandoverService handoverService;
	private final NotificationService notificationService;
	private final TransactionTemplate writeTransaction;

	PortalShipmentService(
		SupplierRepository supplierRepository,
		UserAccountRepository userAccountRepository,
		CustomerOrderRepository orderRepository,
		FulfillmentRepository fulfillmentRepository,
		OrderItemRepository orderItemRepository,
		OrderStatusHistoryRepository orderStatusHistoryRepository,
		ShipmentRepository shipmentRepository,
		ShipmentItemRepository shipmentItemRepository,
		ShipmentChangeHistoryRepository historyRepository,
		ClaimRepository claimRepository,
		RefundRepository refundRepository,
		CarrierRegistry carrierRegistry,
		SupplierPortalFeatureGate featureGate,
		SupplierPortalInputPolicy inputPolicy,
		SupplierPortalHasher hasher,
		ObjectMapper objectMapper,
		SupplierFulfillmentHandoverService handoverService,
		NotificationService notificationService,
		PlatformTransactionManager transactionManager
	) {
		this.supplierRepository = supplierRepository;
		this.userAccountRepository = userAccountRepository;
		this.orderRepository = orderRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.orderItemRepository = orderItemRepository;
		this.orderStatusHistoryRepository = orderStatusHistoryRepository;
		this.shipmentRepository = shipmentRepository;
		this.shipmentItemRepository = shipmentItemRepository;
		this.historyRepository = historyRepository;
		this.claimRepository = claimRepository;
		this.refundRepository = refundRepository;
		this.carrierRegistry = carrierRegistry;
		this.featureGate = featureGate;
		this.inputPolicy = inputPolicy;
		this.hasher = hasher;
		this.objectMapper = objectMapper;
		this.handoverService = handoverService;
		this.notificationService = notificationService;
		this.writeTransaction = new TransactionTemplate(transactionManager);
	}

	public PortalShipmentDtos.CarrierListResponse carriers() {
		return new PortalShipmentDtos.CarrierListResponse(carrierRegistry.carriers().stream()
			.map(carrier -> new PortalShipmentDtos.CarrierResponse(
				carrier.carrierCode(), carrier.carrierName(), true
			))
			.toList());
	}

	@Transactional(readOnly = true)
	public PortalShipmentDtos.CustomerShipmentListResponse listCustomer(UUID userId, UUID orderId) {
		CustomerOrder order = orderRepository.findByIdAndUser_Id(orderId, userId).orElseThrow(this::notFound);
		ReadAggregate aggregate = readAggregate(order);
		return new PortalShipmentDtos.CustomerShipmentListResponse(
			aggregate.shipments().stream()
				.filter(shipment -> !shipment.isVoided())
				.map(shipment -> toCustomer(shipment, aggregate.allocationsByShipment().getOrDefault(
					shipment.getId(), List.of())))
				.toList(),
			allocationComplete(aggregate.items(), aggregate.shipments(), aggregate.allocations())
		);
	}

	@Transactional(readOnly = true)
	public PortalShipmentDtos.SupplierShipmentListResponse listSupplier(UUID actorUserId, String orderNumber) {
		Supplier supplier = requireActiveTenant(actorUserId, false);
		Instant now = Instant.now();
		validateActiveTenant(supplier, actorUserId, now);
		Fulfillment fulfillment = fulfillmentRepository.findSupplierDetail(supplier.getId(), orderNumber)
			.orElseThrow(this::notFound);
		CustomerOrder order = fulfillment.getOrder();
		if (!supplier.getId().equals(order.getSupplier().getId())
			|| fulfillment.getChannel() != FulfillmentChannel.SUPPLIER_PORTAL
			|| fulfillment.getOperationalOwner() != FulfillmentOperationalOwner.SUPPLIER) {
			throw notFound();
		}
		ReadAggregate aggregate = readAggregate(order);
		boolean mutable = fulfillment.getOperationalOwner() == FulfillmentOperationalOwner.SUPPLIER
			&& PORTAL_ACTION_STATUSES.contains(order.getStatus())
			&& fulfillment.getPiiAccessCutoffAt() != null
			&& now.isBefore(fulfillment.getPiiAccessCutoffAt());
		List<PortalShipmentDtos.UnallocatedItemResponse> unallocated = unallocatedItems(
			aggregate.items(), aggregate.shipments(), aggregate.allocations());
		boolean complete = !aggregate.items().isEmpty() && unallocated.isEmpty()
			&& aggregate.shipments().stream().anyMatch(shipment -> !shipment.isVoided());
		boolean canRegister = mutable && !complete;
		return new PortalShipmentDtos.SupplierShipmentListResponse(
			aggregate.shipments().stream()
				.map(shipment -> toSupplier(shipment,
					aggregate.allocationsByShipment().getOrDefault(shipment.getId(), List.of()), mutable))
				.toList(),
			unallocated,
			complete,
			canRegister,
			canRegister ? "REGISTER_SHIPMENT" : complete ? "NONE" : "CONTACT_COREABLE"
		);
	}

	@Transactional(readOnly = true)
	public PortalShipmentDtos.AdminShipmentListResponse listAdmin(UUID orderId) {
		CustomerOrder order = orderRepository.findById(orderId).orElseThrow(this::notFound);
		ReadAggregate aggregate = readAggregate(order);
		return new PortalShipmentDtos.AdminShipmentListResponse(
			aggregate.shipments().stream()
				.map(shipment -> toAdmin(shipment,
					aggregate.allocationsByShipment().getOrDefault(shipment.getId(), List.of()), true))
				.toList(),
			unallocatedItems(aggregate.items(), aggregate.shipments(), aggregate.allocations()),
			allocationComplete(aggregate.items(), aggregate.shipments(), aggregate.allocations())
		);
	}

	public PortalShipmentDtos.SupplierShipmentResponse createSupplier(
		UUID actorUserId,
		String orderNumber,
		String idempotencyKey,
		PortalShipmentDtos.ShipmentCreateRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		NormalizedCreate normalized = normalizeCreate(request);
		String hash = creationHash("SUPPLIER_CREATE", ShipmentActorType.SUPPLIER, actorUserId, normalized);
		CommandOutcome<PortalShipmentDtos.SupplierShipmentResponse> outcome = requireOutcome(
			writeTransaction.execute(status -> createSupplierLocked(
				actorUserId, orderNumber, key, hash, normalized
			))
		);
		return unwrap(outcome);
	}

	public PortalShipmentDtos.AdminShipmentResponse createAdmin(
		UUID actorUserId,
		UUID orderId,
		String idempotencyKey,
		PortalShipmentDtos.ShipmentCreateRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		NormalizedCreate normalized = normalizeCreate(request);
		String hash = creationHash("ADMIN_CREATE", ShipmentActorType.ADMIN, actorUserId, normalized);
		Shipment storedReplay = shipmentRepository.findByOrder_IdAndIdempotencyKey(orderId, key).orElse(null);
		if (storedReplay != null) {
			return readCreationReplay(storedReplay, hash, PortalShipmentDtos.AdminShipmentResponse.class);
		}
		featureGate.requireOperationalMutationReleased();
		return requireOutcome(writeTransaction.execute(status ->
			CommandOutcome.success(createAdminLocked(actorUserId, orderId, key, hash, normalized))
		)).value();
	}

	public PortalShipmentDtos.SupplierShipmentResponse correctSupplier(
		UUID actorUserId,
		String orderNumber,
		UUID shipmentId,
		String idempotencyKey,
		PortalShipmentDtos.TrackingCorrectionRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = inputPolicy.requirePiiFreeReason(request.reason(), 200);
		CarrierRegistry.Carrier carrier = requireCarrier(request.carrierCode());
		String tracking = requireTrackingNumber(request.trackingNumber());
		String hash = actionHash("SUPPLIER_CORRECTED", ShipmentActorType.SUPPLIER, actorUserId,
			Long.toString(request.expectedVersion()), carrier.carrierCode(), tracking, reason);
		CommandOutcome<PortalShipmentDtos.SupplierShipmentResponse> outcome = requireOutcome(
			writeTransaction.execute(status -> correctSupplierLocked(
				actorUserId, orderNumber, shipmentId, key, hash, request.expectedVersion(), carrier,
				tracking, reason
			))
		);
		return unwrap(outcome);
	}

	public PortalShipmentDtos.AdminShipmentResponse correctAdmin(
		UUID actorUserId,
		UUID shipmentId,
		String idempotencyKey,
		PortalShipmentDtos.TrackingCorrectionRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = inputPolicy.requirePiiFreeReason(request.reason(), 200);
		CarrierRegistry.Carrier carrier = requireCarrier(request.carrierCode());
		String tracking = requireTrackingNumber(request.trackingNumber());
		String hash = actionHash("ADMIN_CORRECTED", ShipmentActorType.ADMIN, actorUserId,
			Long.toString(request.expectedVersion()), carrier.carrierCode(), tracking, reason);
		return requireOutcome(writeTransaction.execute(status -> CommandOutcome.success(
			correctAdminLocked(actorUserId, shipmentId, key, hash, request.expectedVersion(), carrier,
				tracking, reason)
		))).value();
	}

	public PortalShipmentDtos.AdminShipmentResponse voidAdmin(
		UUID actorUserId,
		UUID shipmentId,
		String idempotencyKey,
		PortalShipmentDtos.ShipmentVoidRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = inputPolicy.requirePiiFreeReason(request.reason(), 200);
		String hash = actionHash("ADMIN_VOIDED", ShipmentActorType.ADMIN, actorUserId,
			Long.toString(request.expectedVersion()), reason);
		return requireOutcome(writeTransaction.execute(status -> CommandOutcome.success(
			voidAdminLocked(actorUserId, shipmentId, key, hash, request.expectedVersion(), reason)
		))).value();
	}

	public PortalShipmentDtos.AdminShipmentResponse completeDelivery(
		UUID actorUserId,
		UUID shipmentId,
		String idempotencyKey,
		PortalShipmentDtos.DeliveryCompleteRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = inputPolicy.requirePiiFreeReason(request.reason(), 200);
		String hash = actionHash("ADMIN_DELIVERY_COMPLETED", ShipmentActorType.ADMIN, actorUserId,
			Long.toString(request.expectedVersion()), request.deliveredAt().toString(),
			request.evidenceObservedAt().toString(), reason);
		return requireOutcome(writeTransaction.execute(status -> CommandOutcome.success(
			completeDeliveryLocked(actorUserId, shipmentId, key, hash, request.expectedVersion(),
				request.deliveredAt(), request.evidenceObservedAt(), reason)
		))).value();
	}

	public PortalShipmentDtos.AdminShipmentResponse correctDelivery(
		UUID actorUserId,
		UUID shipmentId,
		String idempotencyKey,
		PortalShipmentDtos.DeliveryCorrectionRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = inputPolicy.requirePiiFreeReason(request.reason(), 200);
		validateDeliveryCorrectionRequest(request);
		String action = request.correctionType() == PortalShipmentDtos.DeliveryCorrectionType.REOPEN_TRACKING
			? ShipmentChangeAction.ADMIN_DELIVERY_REOPENED.name()
			: ShipmentChangeAction.ADMIN_DELIVERED_AT_CORRECTED.name();
		String hash = actionHash(action, ShipmentActorType.ADMIN, actorUserId,
			Long.toString(request.expectedVersion()),
			request.correctedDeliveredAt() == null ? null : request.correctedDeliveredAt().toString(),
			request.evidenceObservedAt() == null ? null : request.evidenceObservedAt().toString(), reason);
		return requireOutcome(writeTransaction.execute(status -> CommandOutcome.success(
			correctDeliveryLocked(actorUserId, shipmentId, key, hash, request, reason)
		))).value();
	}

	private CommandOutcome<PortalShipmentDtos.SupplierShipmentResponse> createSupplierLocked(
		UUID actorUserId,
		String orderNumber,
		String key,
		String hash,
		NormalizedCreate request
	) {
		Supplier supplier = requireActiveTenant(actorUserId, true);
		UUID orderId = fulfillmentRepository.findSupplierDetailOrderId(supplier.getId(), orderNumber)
			.orElseThrow(this::notFound);
		LockedAggregate aggregate = lockAggregate(orderId);
		verifySupplierScope(aggregate, supplier, orderNumber);
		Shipment replay = creationReplay(aggregate.shipments(), key);
		if (replay != null) {
			return CommandOutcome.success(readCreationReplay(replay, hash,
				PortalShipmentDtos.SupplierShipmentResponse.class));
		}
		Instant now = Instant.now();
		validateActiveTenant(supplier, actorUserId, now);
		if (isCutoffReached(aggregate.fulfillment(), now)) {
			handoverService.enforceCutoffLazy(aggregate.fulfillment().getId(), now);
			return CommandOutcome.cutoff();
		}
		requireSupplierMutationState(aggregate);
		CreatedShipment created = createLocked(aggregate, actorUserId, ShipmentActorType.SUPPLIER,
			key, hash, request, now);
		PortalShipmentDtos.SupplierShipmentResponse response = toSupplier(
			created.shipment(), created.allocations(), true);
		created.shipment().storeCreationResult(json(response));
		return CommandOutcome.success(response);
	}

	private PortalShipmentDtos.AdminShipmentResponse createAdminLocked(
		UUID actorUserId,
		UUID orderId,
		String key,
		String hash,
		NormalizedCreate request
	) {
		LockedAggregate aggregate = lockAggregate(orderId);
		verifyPortalAggregate(aggregate);
		Shipment replay = creationReplay(aggregate.shipments(), key);
		if (replay != null) {
			return readCreationReplay(replay, hash, PortalShipmentDtos.AdminShipmentResponse.class);
		}
		if (aggregate.fulfillment().getOperationalOwner() != FulfillmentOperationalOwner.COREABLE
			|| !PORTAL_ACTION_STATUSES.contains(aggregate.order().getStatus())) {
			throw conflict("Portal fulfillment is not available for Coreable shipment registration");
		}
		Instant now = Instant.now();
		CreatedShipment created = createLocked(aggregate, actorUserId, ShipmentActorType.ADMIN,
			key, hash, request, now);
		PortalShipmentDtos.AdminShipmentResponse response = toAdmin(
			created.shipment(), created.allocations(), false);
		created.shipment().storeCreationResult(json(response));
		return response;
	}

	private CommandOutcome<PortalShipmentDtos.SupplierShipmentResponse> correctSupplierLocked(
		UUID actorUserId,
		String orderNumber,
		UUID shipmentId,
		String key,
		String hash,
		long expectedVersion,
		CarrierRegistry.Carrier carrier,
		String tracking,
		String reason
	) {
		Supplier supplier = requireActiveTenant(actorUserId, true);
		UUID orderId = fulfillmentRepository.findSupplierDetailOrderId(supplier.getId(), orderNumber)
			.orElseThrow(this::notFound);
		LockedAggregate aggregate = lockAggregate(orderId);
		verifySupplierScope(aggregate, supplier, orderNumber);
		Shipment shipment = requireTarget(aggregate, shipmentId);
		PortalShipmentDtos.SupplierShipmentResponse replay = replayHistory(
			shipment, key, hash, PortalShipmentDtos.SupplierShipmentResponse.class);
		if (replay != null) return CommandOutcome.success(replay);
		Instant now = Instant.now();
		validateActiveTenant(supplier, actorUserId, now);
		if (isCutoffReached(aggregate.fulfillment(), now)) {
			handoverService.enforceCutoffLazy(aggregate.fulfillment().getId(), now);
			return CommandOutcome.cutoff();
		}
		requireSupplierMutationState(aggregate);
		requireVersion(shipment, expectedVersion);
		String before = snapshot(shipment);
		applyDomain(() -> shipment.correctTracking(carrier.carrierCode(), carrier.carrierName(), tracking));
		shipmentRepository.saveAndFlush(shipment);
		String after = snapshot(shipment);
		List<ShipmentItem> allocations = allocationsFor(aggregate.allocations(), shipmentId);
		PortalShipmentDtos.SupplierShipmentResponse response = toSupplier(shipment, allocations, true);
		appendHistory(shipment, actorUserId, ShipmentActorType.SUPPLIER,
			ShipmentChangeAction.SUPPLIER_CORRECTED, before, after, reason, null, hash, key, response);
		return CommandOutcome.success(response);
	}

	private PortalShipmentDtos.AdminShipmentResponse correctAdminLocked(
		UUID actorUserId,
		UUID shipmentId,
		String key,
		String hash,
		long expectedVersion,
		CarrierRegistry.Carrier carrier,
		String tracking,
		String reason
	) {
		LockedAggregate aggregate = lockShipmentAggregate(shipmentId);
		Shipment shipment = requirePortalTarget(aggregate, shipmentId);
		PortalShipmentDtos.AdminShipmentResponse replay = replayHistory(
			shipment, key, hash, PortalShipmentDtos.AdminShipmentResponse.class);
		if (replay != null) return replay;
		requireVersion(shipment, expectedVersion);
		String before = snapshot(shipment);
		applyDomain(() -> shipment.correctTracking(carrier.carrierCode(), carrier.carrierName(), tracking));
		shipmentRepository.saveAndFlush(shipment);
		PortalShipmentDtos.AdminShipmentResponse response = toAdmin(shipment,
			allocationsFor(aggregate.allocations(), shipmentId), false);
		appendHistory(shipment, actorUserId, ShipmentActorType.ADMIN, ShipmentChangeAction.ADMIN_CORRECTED,
			before, snapshot(shipment), reason, null, hash, key, response);
		return response;
	}

	private PortalShipmentDtos.AdminShipmentResponse voidAdminLocked(
		UUID actorUserId,
		UUID shipmentId,
		String key,
		String hash,
		long expectedVersion,
		String reason
	) {
		LockedAggregate aggregate = lockShipmentAggregate(shipmentId);
		Shipment shipment = requirePortalTarget(aggregate, shipmentId);
		PortalShipmentDtos.AdminShipmentResponse replay = replayHistory(
			shipment, key, hash, PortalShipmentDtos.AdminShipmentResponse.class);
		if (replay != null) return replay;
		requireVersion(shipment, expectedVersion);
		String before = snapshot(shipment);
		applyDomain(shipment::voidShipment);
		shipmentRepository.saveAndFlush(shipment);
		OrderStatus beforeOrderStatus = aggregate.order().getStatus();
		recalculateOrder(aggregate);
		recordOrderStatusChange(aggregate.order(), actorUserId, ShipmentChangeAction.ADMIN_VOIDED.name(),
			beforeOrderStatus, "Portal shipment voided and allocations recalculated", reason);
		PortalShipmentDtos.AdminShipmentResponse response = toAdmin(shipment,
			allocationsFor(aggregate.allocations(), shipmentId), false);
		appendHistory(shipment, actorUserId, ShipmentActorType.ADMIN, ShipmentChangeAction.ADMIN_VOIDED,
			before, snapshot(shipment), reason, null, hash, key, response);
		return response;
	}

	private PortalShipmentDtos.AdminShipmentResponse completeDeliveryLocked(
		UUID actorUserId,
		UUID shipmentId,
		String key,
		String hash,
		long expectedVersion,
		Instant deliveredAt,
		Instant evidenceObservedAt,
		String reason
	) {
		LockedAggregate aggregate = lockShipmentAggregate(shipmentId);
		Shipment shipment = requirePortalTarget(aggregate, shipmentId);
		PortalShipmentDtos.AdminShipmentResponse replay = replayHistory(
			shipment, key, hash, PortalShipmentDtos.AdminShipmentResponse.class);
		if (replay != null) return replay;
		requireVersion(shipment, expectedVersion);
		String before = snapshot(shipment);
		applyDomain(() -> shipment.completePortalDelivery(deliveredAt, evidenceObservedAt));
		shipmentRepository.saveAndFlush(shipment);
		OrderStatus beforeOrderStatus = aggregate.order().getStatus();
		boolean delivered = recalculateOrder(aggregate);
		recordOrderStatusChange(aggregate.order(), actorUserId,
			ShipmentChangeAction.ADMIN_DELIVERY_COMPLETED.name(), beforeOrderStatus,
			"Portal shipment delivery evidence recalculated", reason);
		if (delivered) {
			notificationService.transactionalSms(aggregate.order().getUser(), aggregate.order(),
				aggregate.order().getPaymentGroup(), null, null, NotificationType.DELIVERY_COMPLETED);
		}
		PortalShipmentDtos.AdminShipmentResponse response = toAdmin(shipment,
			allocationsFor(aggregate.allocations(), shipmentId), false);
		appendHistory(shipment, actorUserId, ShipmentActorType.ADMIN,
			ShipmentChangeAction.ADMIN_DELIVERY_COMPLETED, before, snapshot(shipment), reason,
			evidenceObservedAt, hash, key, response);
		return response;
	}

	private PortalShipmentDtos.AdminShipmentResponse correctDeliveryLocked(
		UUID actorUserId,
		UUID shipmentId,
		String key,
		String hash,
		PortalShipmentDtos.DeliveryCorrectionRequest request,
		String reason
	) {
		LockedAggregate aggregate = lockShipmentAggregate(shipmentId);
		Shipment shipment = requirePortalTarget(aggregate, shipmentId);
		PortalShipmentDtos.AdminShipmentResponse replay = replayHistory(
			shipment, key, hash, PortalShipmentDtos.AdminShipmentResponse.class);
		if (replay != null) return replay;
		requireVersion(shipment, request.expectedVersion());
		Instant originalDeliveredAt = shipment.getDeliveredAt();
		if (originalDeliveredAt == null || !shipment.isPortalDeliveryEvidence()) {
			throw conflict("Only an admin-completed portal delivery can be corrected");
		}
		if (claimRepository.existsByOrder_IdAndCreatedAtAfter(aggregate.order().getId(), originalDeliveredAt)
			|| refundRepository.existsByOrder_IdAndCreatedAtAfter(aggregate.order().getId(), originalDeliveredAt)
			|| refundRepository.existsByPaymentGroup_IdAndOrderIsNullAndCreatedAtAfter(
				aggregate.order().getPaymentGroup().getId(), originalDeliveredAt)) {
			throw conflict("A later claim or refund blocks delivery correction");
		}
		String before = snapshot(shipment);
		ShipmentChangeAction action;
		Instant evidenceObservedAt;
		if (request.correctionType() == PortalShipmentDtos.DeliveryCorrectionType.REOPEN_TRACKING) {
			applyDomain(shipment::reopenPortalDelivery);
			action = ShipmentChangeAction.ADMIN_DELIVERY_REOPENED;
			evidenceObservedAt = null;
		} else {
			applyDomain(() -> shipment.correctPortalDeliveredAt(
				request.correctedDeliveredAt(), request.evidenceObservedAt()));
			action = ShipmentChangeAction.ADMIN_DELIVERED_AT_CORRECTED;
			evidenceObservedAt = request.evidenceObservedAt();
		}
		shipmentRepository.saveAndFlush(shipment);
		boolean wasDelivered = aggregate.order().getStatus() == OrderStatus.DELIVERED;
		OrderStatus beforeOrderStatus = aggregate.order().getStatus();
		recalculateOrder(aggregate);
		recordOrderStatusChange(aggregate.order(), actorUserId, action.name(), beforeOrderStatus,
			"Portal shipment delivery correction recalculated", reason);
		if (wasDelivered && aggregate.order().getStatus() != OrderStatus.DELIVERED) {
			notificationService.transactionalSms(aggregate.order().getUser(), aggregate.order(),
				aggregate.order().getPaymentGroup(), null, null, NotificationType.DELIVERY_STATUS_CORRECTED);
		}
		PortalShipmentDtos.AdminShipmentResponse response = toAdmin(shipment,
			allocationsFor(aggregate.allocations(), shipmentId), false);
		appendHistory(shipment, actorUserId, ShipmentActorType.ADMIN, action, before, snapshot(shipment),
			reason, evidenceObservedAt, hash, key, response);
		return response;
	}

	private CreatedShipment createLocked(
		LockedAggregate aggregate,
		UUID actorUserId,
		ShipmentActorType actorType,
		String key,
		String hash,
		NormalizedCreate request,
		Instant now
	) {
		List<ResolvedAllocation> resolved = resolveAllocations(aggregate, request.allocations());
		Shipment shipment = Shipment.portal(aggregate.order(), request.carrier().carrierCode(),
			request.carrier().carrierName(), request.trackingNumber(), now, actorUserId, actorType, key, hash);
		shipmentRepository.save(shipment);
		List<ShipmentItem> allocations = resolved.stream()
			.map(allocation -> new ShipmentItem(shipment, allocation.item(), allocation.quantity()))
			.toList();
		shipmentItemRepository.saveAll(allocations);
		OrderStatus beforeOrderStatus = aggregate.order().getStatus();
		applyDomain(aggregate.order()::markTrackingRegistered);
		String action = actorType == ShipmentActorType.SUPPLIER
			? "SUPPLIER_SHIPMENT_REGISTERED"
			: "ADMIN_PORTAL_SHIPMENT_REGISTERED";
		recordOrderStatusChange(aggregate.order(), actorUserId, action, beforeOrderStatus,
			"Portal shipment tracking registered", "Portal shipment tracking registered");
		aggregate.fulfillment().shortenPiiAccessCutoffAt(now.plus(30, ChronoUnit.DAYS));
		return new CreatedShipment(shipment, allocations);
	}

	private List<ResolvedAllocation> resolveAllocations(
		LockedAggregate aggregate,
		List<PortalShipmentDtos.AllocationRequest> requested
	) {
		Map<UUID, Integer> allocated = activeAllocatedByItem(aggregate.shipments(), aggregate.allocations());
		Map<UUID, OrderItem> itemsById = aggregate.items().stream()
			.collect(Collectors.toMap(OrderItem::getId, Function.identity()));
		boolean omitted = requested == null || requested.isEmpty();
		if (omitted && !aggregate.shipments().isEmpty()) {
			throw validation("Additional shipments require explicit allocations");
		}
		List<ResolvedAllocation> resolved = new ArrayList<>();
		if (omitted) {
			for (OrderItem item : aggregate.items()) {
				if (!isOwnedPortalItem(aggregate.order(), item)) {
					throw validation("Allocation item does not belong to this portal order");
				}
				int remaining = item.getQuantity() - allocated.getOrDefault(item.getId(), 0);
				if (remaining > 0) resolved.add(new ResolvedAllocation(item, remaining));
			}
		} else {
			Set<UUID> seen = new HashSet<>();
			for (PortalShipmentDtos.AllocationRequest allocation : requested) {
				if (!seen.add(allocation.orderItemId())) {
					throw validation("Duplicate order item allocation");
				}
				OrderItem item = itemsById.get(allocation.orderItemId());
				if (item == null || !isOwnedPortalItem(aggregate.order(), item)) {
					throw validation("Allocation item does not belong to this portal order");
				}
				int remaining = item.getQuantity() - allocated.getOrDefault(item.getId(), 0);
				if (allocation.quantity() <= 0 || allocation.quantity() > remaining) {
					throw conflict("Shipment allocation exceeds the remaining quantity");
				}
				resolved.add(new ResolvedAllocation(item, allocation.quantity()));
			}
		}
		if (resolved.isEmpty()) throw conflict("There is no remaining quantity to allocate");
		return resolved.stream().sorted(Comparator.comparing(allocation -> allocation.item().getId())).toList();
	}

	private boolean isOwnedPortalItem(CustomerOrder order, OrderItem item) {
		return order.getSupplier().getId().equals(item.getSupplier().getId())
			&& item.getManagementChannelSnapshot() == ProductManagementChannel.SUPPLIER_PORTAL;
	}

	private LockedAggregate lockShipmentAggregate(UUID shipmentId) {
		UUID orderId = shipmentRepository.findOrderIdByShipmentId(shipmentId).orElseThrow(this::notFound);
		return lockAggregate(orderId);
	}

	private LockedAggregate lockAggregate(UUID orderId) {
		CustomerOrder order = orderRepository.findByIdForUpdate(orderId).orElseThrow(this::notFound);
		Fulfillment fulfillment = fulfillmentRepository.findByOrderIdForUpdate(orderId).orElseThrow(this::notFound);
		List<Shipment> shipments = shipmentRepository.findAllByOrderIdForUpdate(orderId);
		List<ShipmentItem> allocations = shipmentItemRepository.findAllByOrderIdForUpdate(orderId);
		List<OrderItem> items = orderItemRepository.findAllByOrderIdForUpdate(orderId);
		return new LockedAggregate(order, fulfillment, shipments, allocations, items);
	}

	private void verifySupplierScope(LockedAggregate aggregate, Supplier supplier, String orderNumber) {
		verifyPortalAggregate(aggregate);
		if (!supplier.getId().equals(aggregate.order().getSupplier().getId())
			|| !supplier.getId().equals(aggregate.fulfillment().getSupplier().getId())
			|| !orderNumber.equals(aggregate.order().getOrderNumber())) {
			throw notFound();
		}
	}

	private void verifyPortalAggregate(LockedAggregate aggregate) {
		if (aggregate.fulfillment().getChannel() != FulfillmentChannel.SUPPLIER_PORTAL
			|| !aggregate.order().getSupplier().getId().equals(aggregate.fulfillment().getSupplier().getId())) {
			throw notFound();
		}
	}

	private void requireSupplierMutationState(LockedAggregate aggregate) {
		if (aggregate.fulfillment().getOperationalOwner() != FulfillmentOperationalOwner.SUPPLIER
			|| !PORTAL_ACTION_STATUSES.contains(aggregate.order().getStatus())) {
			throw conflict("Supplier shipment mutation is unavailable");
		}
	}

	private Supplier requireActiveTenant(UUID actorUserId, boolean locked) {
		Supplier supplier = (locked
			? supplierRepository.findByManagerUserIdForUpdate(actorUserId)
			: supplierRepository.findByManagerUserId(actorUserId))
			.orElseThrow(this::forbidden);
		validateActiveTenant(supplier, actorUserId, Instant.now());
		return supplier;
	}

	private void validateActiveTenant(Supplier supplier, UUID actorUserId, Instant now) {
		if (supplier.getPortalStatus() != SupplierPortalStatus.ACTIVE
			|| supplier.getManagerUserId() == null
			|| !supplier.getManagerUserId().equals(actorUserId)
			|| userAccountRepository.findByIdAndStatus(actorUserId, UserStatus.ACTIVE).isEmpty()
			|| !supplier.hasTimeValidContract(now)) {
			throw forbidden();
		}
	}

	private boolean isCutoffReached(Fulfillment fulfillment, Instant now) {
		return fulfillment.getPiiAccessCutoffAt() == null || !now.isBefore(fulfillment.getPiiAccessCutoffAt());
	}

	private Shipment creationReplay(List<Shipment> shipments, String key) {
		return shipments.stream().filter(shipment -> key.equals(shipment.getIdempotencyKey())).findFirst().orElse(null);
	}

	private <T> T readCreationReplay(Shipment shipment, String hash, Class<T> type) {
		if (!shipment.matchesCreationReplay(shipment.getIdempotencyKey(), hash)
			|| shipment.getCreationResultSnapshot() == null) {
			throw idempotencyConflict();
		}
		return readJson(shipment.getCreationResultSnapshot(), type);
	}

	private <T> T replayHistory(Shipment shipment, String key, String hash, Class<T> type) {
		ShipmentChangeHistory history = historyRepository
			.findByShipment_IdAndIdempotencyKey(shipment.getId(), key).orElse(null);
		if (history == null) return null;
		if (!history.matchesReplay(key, hash) || history.getResultSnapshot() == null) {
			throw idempotencyConflict();
		}
		return readJson(history.getResultSnapshot(), type);
	}

	private void appendHistory(
		Shipment shipment,
		UUID actorUserId,
		ShipmentActorType actorType,
		ShipmentChangeAction action,
		String before,
		String after,
		String reason,
		Instant evidenceObservedAt,
		String hash,
		String key,
		Object response
	) {
		ShipmentChangeHistory history = ShipmentChangeHistory.command(
			shipment, actorUserId, actorType, action, before, after, reason,
			evidenceObservedAt, hash, key
		);
		history.storeResult(json(response));
		historyRepository.save(history);
	}

	private Shipment requireTarget(LockedAggregate aggregate, UUID shipmentId) {
		return aggregate.shipments().stream().filter(shipment -> shipment.getId().equals(shipmentId))
			.findFirst().orElseThrow(this::notFound);
	}

	private Shipment requirePortalTarget(LockedAggregate aggregate, UUID shipmentId) {
		verifyPortalAggregate(aggregate);
		Shipment shipment = requireTarget(aggregate, shipmentId);
		if (!shipment.isPortal()) throw notFound();
		return shipment;
	}

	private void requireVersion(Shipment shipment, long expectedVersion) {
		if (shipment.getVersion() != expectedVersion) {
			throw conflict("Shipment version changed");
		}
	}

	private boolean recalculateOrder(LockedAggregate aggregate) {
		List<Shipment> active = aggregate.shipments().stream().filter(shipment -> !shipment.isVoided()).toList();
		boolean complete = allocationComplete(aggregate.items(), aggregate.shipments(), aggregate.allocations());
		boolean delivered = complete && !active.isEmpty()
			&& active.stream().allMatch(Shipment::isPortalDeliveryEvidence);
		OrderStatus before = aggregate.order().getStatus();
		if (delivered) {
			if (before != OrderStatus.DELIVERED) applyDomain(aggregate.order()::markDeliveredByPortalAggregate);
		} else if (active.isEmpty()) {
			if (before == OrderStatus.TRACKING_REGISTERED) {
				applyDomain(aggregate.order()::returnToSupplierOrderPendingAfterShipmentVoid);
			} else if (before == OrderStatus.DELIVERED) {
				applyDomain(aggregate.order()::reopenPortalDelivery);
				applyDomain(aggregate.order()::returnToSupplierOrderPendingAfterShipmentVoid);
			}
		} else if (before == OrderStatus.DELIVERED) {
			applyDomain(aggregate.order()::reopenPortalDelivery);
		} else if (before == OrderStatus.SUPPLIER_ORDER_PENDING) {
			applyDomain(aggregate.order()::markTrackingRegistered);
		}
		return before != OrderStatus.DELIVERED && aggregate.order().getStatus() == OrderStatus.DELIVERED;
	}

	private void recordOrderStatusChange(
		CustomerOrder order,
		UUID actorUserId,
		String actionType,
		OrderStatus beforeStatus,
		String sideEffectSummary,
		String reason
	) {
		if (beforeStatus == order.getStatus()) {
			return;
		}
		orderStatusHistoryRepository.save(new OrderStatusHistory(
			order, actorUserId, actionType, beforeStatus, order.getStatus(), "ALLOWED",
			sideEffectSummary, reason
		));
	}

	private ReadAggregate readAggregate(CustomerOrder order) {
		List<Shipment> shipments = shipmentRepository.findAllByOrder_IdOrderByRegisteredAtAscIdAsc(order.getId());
		List<ShipmentItem> allocations = shipmentItemRepository.findAllByOrder_IdOrderByOrderItem_IdAsc(order.getId());
		List<OrderItem> items = orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(order.getId());
		Map<UUID, List<ShipmentItem>> byShipment = allocations.stream()
			.collect(Collectors.groupingBy(allocation -> allocation.getShipment().getId()));
		return new ReadAggregate(order, shipments, allocations, items, byShipment);
	}

	private Map<UUID, Integer> activeAllocatedByItem(List<Shipment> shipments, List<ShipmentItem> allocations) {
		Set<UUID> activeIds = shipments.stream().filter(shipment -> !shipment.isVoided())
			.map(Shipment::getId).collect(Collectors.toSet());
		Map<UUID, Integer> allocated = new HashMap<>();
		for (ShipmentItem allocation : allocations) {
			if (activeIds.contains(allocation.getShipment().getId())) {
				allocated.merge(allocation.getOrderItem().getId(), allocation.getQuantity(), Integer::sum);
			}
		}
		return allocated;
	}

	private boolean allocationComplete(
		List<OrderItem> items,
		List<Shipment> shipments,
		List<ShipmentItem> allocations
	) {
		if (items.isEmpty() || shipments.stream().noneMatch(shipment -> !shipment.isVoided())) return false;
		Map<UUID, Integer> allocated = activeAllocatedByItem(shipments, allocations);
		return items.stream().allMatch(item -> allocated.getOrDefault(item.getId(), 0) == item.getQuantity());
	}

	private List<PortalShipmentDtos.UnallocatedItemResponse> unallocatedItems(
		List<OrderItem> items,
		List<Shipment> shipments,
		List<ShipmentItem> allocations
	) {
		Map<UUID, Integer> allocated = activeAllocatedByItem(shipments, allocations);
		return items.stream().map(item -> {
			int allocatedQuantity = allocated.getOrDefault(item.getId(), 0);
			return new PortalShipmentDtos.UnallocatedItemResponse(
				item.getId(), item.getProductName(), item.getOptionName(), item.getQuantity(),
				allocatedQuantity, Math.max(0, item.getQuantity() - allocatedQuantity)
			);
		}).filter(item -> item.remainingQuantity() > 0).toList();
	}

	private List<ShipmentItem> allocationsFor(List<ShipmentItem> allocations, UUID shipmentId) {
		return allocations.stream().filter(allocation -> allocation.getShipment().getId().equals(shipmentId)).toList();
	}

	private PortalShipmentDtos.CustomerShipmentResponse toCustomer(
		Shipment shipment,
		List<ShipmentItem> allocations
	) {
		return new PortalShipmentDtos.CustomerShipmentResponse(
			shipment.getId(), shipment.getCarrierCode(), shipment.getCarrier(), shipment.getTrackingNumber(),
			officialUrl(shipment), shipment.getStatus().name(), shipment.getRegisteredAt(),
			shipment.getDeliveredAt(), toAllocations(allocations)
		);
	}

	private PortalShipmentDtos.SupplierShipmentResponse toSupplier(
		Shipment shipment,
		List<ShipmentItem> allocations,
		boolean mutationAvailable
	) {
		return new PortalShipmentDtos.SupplierShipmentResponse(
			shipment.getId(), shipment.getVersion(), shipment.getStatus().name(), shipment.getCarrierCode(),
			shipment.getCarrier(), shipment.getTrackingNumber(), officialUrl(shipment),
			mutationAvailable && shipment.getStatus() == ShipmentStatus.TRACKING_REGISTERED,
			shipment.countsTowardAllocation(), shipment.getRegisteredAt(), shipment.getDeliveredAt(),
			toAllocations(allocations)
		);
	}

	private PortalShipmentDtos.AdminShipmentResponse toAdmin(
		Shipment shipment,
		List<ShipmentItem> allocations,
		boolean includeHistories
	) {
		List<PortalShipmentDtos.ChangeHistoryResponse> histories = includeHistories
			? historyRepository.findAllByShipment_IdOrderByCreatedAtAscIdAsc(shipment.getId()).stream()
				.map(history -> new PortalShipmentDtos.ChangeHistoryResponse(
					history.getId(), history.getActorType().name(), history.getAction().name(),
					history.getBeforeSnapshot(), history.getAfterSnapshot(), history.getReason(),
					history.getEvidenceObservedAt(), history.getCreatedAt()
				)).toList()
			: List.of();
		return new PortalShipmentDtos.AdminShipmentResponse(
			shipment.getId(), shipment.getVersion(), shipment.getStatus().name(), shipment.getCarrierCode(),
			shipment.getCarrier(), shipment.getTrackingNumber(), officialUrl(shipment),
			shipment.countsTowardAllocation(), shipment.getRegisteredAt(), shipment.getDeliveredAt(),
			shipment.getDeliveryEvidenceObservedAt(), toAllocations(allocations), histories
		);
	}

	private List<PortalShipmentDtos.AllocationResponse> toAllocations(List<ShipmentItem> allocations) {
		return allocations.stream()
			.sorted(Comparator.comparing(allocation -> allocation.getOrderItem().getId()))
			.map(allocation -> new PortalShipmentDtos.AllocationResponse(
				allocation.getOrderItem().getId(), allocation.getQuantity()))
			.toList();
	}

	private String officialUrl(Shipment shipment) {
		if (shipment.getCarrierCode() == null) return null;
		return carrierRegistry.find(shipment.getCarrierCode())
			.map(carrier -> carrierRegistry.officialTrackingUrl(carrier.carrierCode(), shipment.getTrackingNumber()))
			.orElse(null);
	}

	private NormalizedCreate normalizeCreate(PortalShipmentDtos.ShipmentCreateRequest request) {
		CarrierRegistry.Carrier carrier = requireCarrier(request.carrierCode());
		String tracking = requireTrackingNumber(request.trackingNumber());
		List<PortalShipmentDtos.AllocationRequest> allocations = request.allocations() == null
			? null
			: request.allocations().stream()
				.sorted(Comparator.comparing(PortalShipmentDtos.AllocationRequest::orderItemId))
				.toList();
		return new NormalizedCreate(carrier, tracking, allocations);
	}

	private CarrierRegistry.Carrier requireCarrier(String carrierCode) {
		try {
			return carrierRegistry.require(carrierCode);
		} catch (IllegalArgumentException exception) {
			throw validation("Unsupported carrier code");
		}
	}

	private String requireTrackingNumber(String value) {
		String tracking = hasher.normalizeText(value);
		if (tracking == null || tracking.isBlank() || tracking.length() > 100) {
			throw validation("Tracking number is required and must be at most 100 characters");
		}
		return tracking;
	}

	private String creationHash(
		String action,
		ShipmentActorType actorType,
		UUID actorUserId,
		NormalizedCreate request
	) {
		String allocations = request.allocations() == null ? "<omitted>" : request.allocations().stream()
			.map(allocation -> allocation.orderItemId() + ":" + allocation.quantity())
			.collect(Collectors.joining(","));
		return hasher.hmac("portal-shipment-create", action, actorType.name(), actorUserId.toString(),
			request.carrier().carrierCode(), request.trackingNumber(), allocations);
	}

	private String actionHash(String action, ShipmentActorType actorType, UUID actorUserId, String... body) {
		String[] values = new String[body.length + 3];
		values[0] = action;
		values[1] = actorType.name();
		values[2] = actorUserId.toString();
		System.arraycopy(body, 0, values, 3, body.length);
		return hasher.hmac("portal-shipment-action", values);
	}

	private void validateDeliveryCorrectionRequest(PortalShipmentDtos.DeliveryCorrectionRequest request) {
		if (request.correctionType() == PortalShipmentDtos.DeliveryCorrectionType.REOPEN_TRACKING) {
			if (request.correctedDeliveredAt() != null || request.evidenceObservedAt() != null) {
				throw validation("REOPEN_TRACKING forbids corrected delivery timestamps");
			}
		} else if (request.correctedDeliveredAt() == null || request.evidenceObservedAt() == null) {
			throw validation("CORRECT_DELIVERED_AT requires correctedDeliveredAt and evidenceObservedAt");
		}
	}

	private String snapshot(Shipment shipment) {
		return json(new ShipmentSnapshot(
			shipment.getVersion(), shipment.getStatus().name(), shipment.getCarrierCode(),
			shipment.getCarrier(), shipment.getTrackingNumber(), shipment.getRegisteredAt(),
			shipment.getDeliveredAt(), shipment.getDeliveryEvidenceObservedAt()
		));
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize portal shipment result");
		}
	}

	private <T> T readJson(String value, Class<T> type) {
		try {
			return objectMapper.readValue(value, type);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to read portal shipment result");
		}
	}

	private void applyDomain(Runnable action) {
		try {
			action.run();
		} catch (IllegalStateException | IllegalArgumentException exception) {
			throw conflict(exception.getMessage());
		}
	}

	private <T> T unwrap(CommandOutcome<T> outcome) {
		if (outcome.cutoffRejected()) {
			throw conflict("Supplier shipment window has expired and work was handed to Coreable");
		}
		return Objects.requireNonNull(outcome.value());
	}

	private <T> CommandOutcome<T> requireOutcome(CommandOutcome<T> outcome) {
		return Objects.requireNonNull(outcome, "Portal shipment transaction returned no result");
	}

	private ApiErrorException notFound() {
		return new ApiErrorException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
	}

	private ApiErrorException forbidden() {
		return new ApiErrorException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN,
			"Supplier access is unavailable");
	}

	private ApiErrorException validation(String message) {
		return new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, message);
	}

	private ApiErrorException conflict(String message) {
		return new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, message);
	}

	private ApiErrorException idempotencyConflict() {
		return new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_CONFLICT,
			"Idempotency key conflict");
	}

	private record NormalizedCreate(
		CarrierRegistry.Carrier carrier,
		String trackingNumber,
		List<PortalShipmentDtos.AllocationRequest> allocations
	) {
	}

	private record ResolvedAllocation(OrderItem item, int quantity) {
	}

	private record CreatedShipment(Shipment shipment, List<ShipmentItem> allocations) {
	}

	private record LockedAggregate(
		CustomerOrder order,
		Fulfillment fulfillment,
		List<Shipment> shipments,
		List<ShipmentItem> allocations,
		List<OrderItem> items
	) {
	}

	private record ReadAggregate(
		CustomerOrder order,
		List<Shipment> shipments,
		List<ShipmentItem> allocations,
		List<OrderItem> items,
		Map<UUID, List<ShipmentItem>> allocationsByShipment
	) {
	}

	private record CommandOutcome<T>(T value, boolean cutoffRejected) {
		static <T> CommandOutcome<T> success(T value) { return new CommandOutcome<>(value, false); }
		static <T> CommandOutcome<T> cutoff() { return new CommandOutcome<>(null, true); }
	}

	private record ShipmentSnapshot(
		long version,
		String status,
		String carrierCode,
		String carrierName,
		String trackingNumber,
		Instant registeredAt,
		Instant deliveredAt,
		Instant evidenceObservedAt
	) {
	}
}
