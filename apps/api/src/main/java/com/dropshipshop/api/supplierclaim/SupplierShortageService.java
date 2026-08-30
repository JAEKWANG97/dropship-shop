package com.dropshipshop.api.supplierclaim;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.fulfillment.SupplierFulfillmentHandoverService;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentChannel;
import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.AdminOrderFulfillmentService;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.domain.ShipmentItem;
import com.dropshipshop.api.shipment.repository.ShipmentItemRepository;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;
import com.dropshipshop.api.supplierclaim.domain.SupplierShortageReport;
import com.dropshipshop.api.supplierclaim.domain.SupplierShortageReviewReasonCode;
import com.dropshipshop.api.supplierclaim.domain.SupplierShortageStatus;
import com.dropshipshop.api.supplierclaim.repository.SupplierShortageReportRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.SupplierPortalInputPolicy;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class SupplierShortageService {

	private final SupplierRepository supplierRepository;
	private final UserAccountRepository userAccountRepository;
	private final CustomerOrderRepository orderRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final SupplierShortageReportRepository reportRepository;
	private final ShipmentRepository shipmentRepository;
	private final ShipmentItemRepository shipmentItemRepository;
	private final OrderItemRepository orderItemRepository;
	private final SupplierFulfillmentHandoverService handoverService;
	private final AdminOrderFulfillmentService orderFulfillmentService;
	private final SupplierPortalInputPolicy inputPolicy;
	private final SupplierPortalHasher hasher;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate writeTransaction;

	SupplierShortageService(
		SupplierRepository supplierRepository,
		UserAccountRepository userAccountRepository,
		CustomerOrderRepository orderRepository,
		FulfillmentRepository fulfillmentRepository,
		SupplierShortageReportRepository reportRepository,
		ShipmentRepository shipmentRepository,
		ShipmentItemRepository shipmentItemRepository,
		OrderItemRepository orderItemRepository,
		SupplierFulfillmentHandoverService handoverService,
		AdminOrderFulfillmentService orderFulfillmentService,
		SupplierPortalInputPolicy inputPolicy,
		SupplierPortalHasher hasher,
		ObjectMapper objectMapper,
		PlatformTransactionManager transactionManager
	) {
		this.supplierRepository = supplierRepository;
		this.userAccountRepository = userAccountRepository;
		this.orderRepository = orderRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.reportRepository = reportRepository;
		this.shipmentRepository = shipmentRepository;
		this.shipmentItemRepository = shipmentItemRepository;
		this.orderItemRepository = orderItemRepository;
		this.handoverService = handoverService;
		this.orderFulfillmentService = orderFulfillmentService;
		this.inputPolicy = inputPolicy;
		this.hasher = hasher;
		this.objectMapper = objectMapper;
		this.writeTransaction = new TransactionTemplate(transactionManager);
	}

	public SupplierClaimDtos.SupplierShortageResponse submit(
		UUID actorUserId,
		String orderNumber,
		String idempotencyKey,
		SupplierClaimDtos.ShortageSubmitRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		CommandOutcome<SupplierClaimDtos.SupplierShortageResponse> outcome = requireOutcome(
			writeTransaction.execute(status -> submitLocked(actorUserId, orderNumber, key, request))
		);
		if (outcome.cutoffReached()) {
			throw conflict("Supplier fulfillment cutoff has been reached");
		}
		return outcome.value();
	}

	@Transactional(readOnly = true)
	public SupplierClaimDtos.SupplierShortageListResponse listSupplier(
		UUID actorUserId,
		SupplierShortageStatus status
	) {
		Supplier supplier = requireActiveTenant(actorUserId, false);
		return new SupplierClaimDtos.SupplierShortageListResponse(
			reportRepository.findSupplierList(supplier.getId(), status).stream()
				.map(this::toSupplierResponse)
				.toList()
		);
	}

	@Transactional(readOnly = true)
	public SupplierClaimDtos.SupplierShortageResponse detailSupplier(UUID actorUserId, UUID reportId) {
		Supplier supplier = requireActiveTenant(actorUserId, false);
		return reportRepository.findByIdAndSupplier_Id(reportId, supplier.getId())
			.map(this::toSupplierResponse)
			.orElseThrow(this::notFound);
	}

	@Transactional(readOnly = true)
	public SupplierClaimDtos.AdminShortageListResponse listAdmin(
		SupplierShortageStatus status,
		UUID orderId
	) {
		return new SupplierClaimDtos.AdminShortageListResponse(
			reportRepository.findAdminList(status, orderId).stream()
				.map(this::toAdminResponse)
				.toList()
		);
	}

	@Transactional(readOnly = true)
	public SupplierClaimDtos.AdminShortageResponse detailAdmin(UUID reportId) {
		return reportRepository.findById(reportId).map(this::toAdminResponse).orElseThrow(this::notFound);
	}

	public SupplierClaimDtos.AdminShortageResponse approve(
		UUID actorUserId,
		UUID reportId,
		String idempotencyKey,
		SupplierClaimDtos.ShortageReviewRequest request
	) {
		return review(actorUserId, reportId, idempotencyKey, request, true);
	}

	public SupplierClaimDtos.AdminShortageResponse reject(
		UUID actorUserId,
		UUID reportId,
		String idempotencyKey,
		SupplierClaimDtos.ShortageReviewRequest request
	) {
		return review(actorUserId, reportId, idempotencyKey, request, false);
	}

	private CommandOutcome<SupplierClaimDtos.SupplierShortageResponse> submitLocked(
		UUID actorUserId,
		String orderNumber,
		String key,
		SupplierClaimDtos.ShortageSubmitRequest request
	) {
		Supplier supplier = requireActiveTenant(actorUserId, true);
		String hash = hasher.hmac("supplier-shortage-submit", supplier.getId().toString(),
			orderNumber, request.reasonCode().name());
		SupplierShortageReport replay = reportRepository
			.findBySupplier_IdAndIdempotencyKey(supplier.getId(), key).orElse(null);
		if (replay != null) return CommandOutcome.success(readSubmitReplay(replay, hash));

		UUID orderId = fulfillmentRepository.findSupplierDetailOrderId(supplier.getId(), orderNumber)
			.orElseThrow(this::notFound);
		LockedShortageAggregate aggregate = lockAggregate(orderId);
		verifySupplierScope(aggregate, supplier, orderNumber);
		replay = reportRepository.findBySupplier_IdAndIdempotencyKey(supplier.getId(), key).orElse(null);
		if (replay != null) return CommandOutcome.success(readSubmitReplay(replay, hash));
		if (aggregate.report() != null) throw shortageAlreadyReported();

		Instant now = Instant.now();
		if (aggregate.fulfillment().getPiiAccessCutoffAt() == null
			|| !now.isBefore(aggregate.fulfillment().getPiiAccessCutoffAt())) {
			handoverService.enforceCutoffLazy(aggregate.fulfillment().getId(), now);
			return CommandOutcome.cutoff();
		}
		if (!aggregate.fulfillment().isOpenPortalSupplierOwned()
			|| aggregate.order().getStatus() != OrderStatus.SUPPLIER_ORDER_PENDING) {
			throw conflict("Supplier shortage reporting is unavailable for this order");
		}
		if (!aggregate.shipments().isEmpty()) {
			throw conflict("A shipment has already been registered for this order");
		}

		SupplierShortageReport report = new SupplierShortageReport(
			aggregate.order(), supplier, actorUserId, request.reasonCode(), hash, key, now
		);
		SupplierClaimDtos.SupplierShortageResponse response = toSupplierResponse(report);
		report.initializeSubmitResult(json(response));
		reportRepository.save(report);
		if (!handoverService.takeOverSupplierShortage(aggregate.fulfillment(), now)) {
			throw conflict("Supplier fulfillment could not be handed over");
		}
		return CommandOutcome.success(response);
	}

	private SupplierClaimDtos.AdminShortageResponse review(
		UUID actorUserId,
		UUID reportId,
		String idempotencyKey,
		SupplierClaimDtos.ShortageReviewRequest request,
		boolean approve
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String hash = hasher.hmac("supplier-shortage-review", approve ? "APPROVE" : "REJECT",
			reportId.toString(), actorUserId.toString(), request.expectedStatus().name(),
			request.reviewReasonCode().name());
		SupplierShortageReport visible = reportRepository.findById(reportId).orElseThrow(this::notFound);
		SupplierClaimDtos.AdminShortageResponse replay = readReviewReplayIfPresent(visible, key, hash);
		if (replay != null) return replay;
		return requireOutcome(writeTransaction.execute(status -> CommandOutcome.success(
			reviewLocked(actorUserId, reportId, key, hash, request, approve)
		))).value();
	}

	private SupplierClaimDtos.AdminShortageResponse reviewLocked(
		UUID actorUserId,
		UUID reportId,
		String key,
		String hash,
		SupplierClaimDtos.ShortageReviewRequest request,
		boolean approve
	) {
		UUID orderId = reportRepository.findOrderIdById(reportId).orElseThrow(this::notFound);
		LockedShortageAggregate aggregate = lockAggregate(orderId);
		SupplierShortageReport report = aggregate.report();
		if (report == null || !report.getId().equals(reportId)) throw notFound();
		SupplierClaimDtos.AdminShortageResponse replay = readReviewReplayIfPresent(report, key, hash);
		if (replay != null) return replay;
		validateReview(request, approve);
		if (request.expectedStatus() != SupplierShortageStatus.REPORTED
			|| report.getStatus() != request.expectedStatus()) {
			throw conflict("Shortage report status changed");
		}
		if (aggregate.fulfillment().getChannel() != FulfillmentChannel.SUPPLIER_PORTAL
			|| aggregate.fulfillment().getOperationalOwner() != FulfillmentOperationalOwner.COREABLE) {
			throw conflict("Shortage report is not owned by Coreable");
		}
		if (!aggregate.shipments().isEmpty()) {
			throw conflict("A shipment has already been registered for this order");
		}
		Instant now = Instant.now();
		SupplierShortageStatus target = approve
			? SupplierShortageStatus.APPROVED
			: SupplierShortageStatus.REJECTED;
		if (approve) {
			orderFulfillmentService.applyApprovedSupplierShortage(
				aggregate.order(), aggregate.fulfillment(), actorUserId
			);
		}
		report.review(target, actorUserId, request.reviewReasonCode(), hash, key, now);
		SupplierClaimDtos.AdminShortageResponse response = toAdminResponse(report);
		report.initializeReviewResult(json(response));
		return response;
	}

	private LockedShortageAggregate lockAggregate(UUID orderId) {
		CustomerOrder order = orderRepository.findByIdForUpdate(orderId).orElseThrow(this::notFound);
		Fulfillment fulfillment = fulfillmentRepository.findByOrderIdForUpdate(orderId).orElseThrow(this::notFound);
		SupplierShortageReport report = reportRepository.findByOrderIdForUpdate(orderId).orElse(null);
		List<Shipment> shipments = shipmentRepository.findAllByOrderIdForUpdate(orderId);
		List<ShipmentItem> shipmentItems = shipmentItemRepository.findAllByOrderIdForUpdate(orderId);
		List<OrderItem> orderItems = orderItemRepository.findAllByOrderIdForUpdate(orderId);
		return new LockedShortageAggregate(order, fulfillment, report, shipments, shipmentItems, orderItems);
	}

	private void verifySupplierScope(
		LockedShortageAggregate aggregate,
		Supplier supplier,
		String orderNumber
	) {
		if (!supplier.getId().equals(aggregate.order().getSupplier().getId())
			|| !supplier.getId().equals(aggregate.fulfillment().getSupplier().getId())
			|| !orderNumber.equals(aggregate.order().getOrderNumber())
			|| aggregate.fulfillment().getChannel() != FulfillmentChannel.SUPPLIER_PORTAL) {
			throw notFound();
		}
	}

	private void validateReview(SupplierClaimDtos.ShortageReviewRequest request, boolean approve) {
		SupplierShortageReviewReasonCode reason = request.reviewReasonCode();
		boolean allowed = approve
			? reason == SupplierShortageReviewReasonCode.SHORTAGE_CONFIRMED
			: reason == SupplierShortageReviewReasonCode.INSUFFICIENT_EVIDENCE
				|| reason == SupplierShortageReviewReasonCode.FULFILLMENT_CAN_CONTINUE;
		if (!allowed) throw validation("Shortage review reason does not match the action");
	}

	private Supplier requireActiveTenant(UUID actorUserId, boolean locked) {
		Supplier supplier = (locked
			? supplierRepository.findByManagerUserIdForUpdate(actorUserId)
			: supplierRepository.findByManagerUserId(actorUserId))
			.orElseThrow(this::forbidden);
		Instant now = Instant.now();
		if (supplier.getPortalStatus() != SupplierPortalStatus.ACTIVE
			|| supplier.getManagerUserId() == null
			|| !supplier.getManagerUserId().equals(actorUserId)
			|| userAccountRepository.findByIdAndStatus(actorUserId, UserStatus.ACTIVE).isEmpty()
			|| !supplier.hasTimeValidContract(now)) {
			throw forbidden();
		}
		return supplier;
	}

	private SupplierClaimDtos.SupplierShortageResponse readSubmitReplay(
		SupplierShortageReport report,
		String hash
	) {
		if (!report.matchesSubmitReplay(hash)) throw idempotencyConflict();
		return readJson(report.getSubmitResultSnapshot(), SupplierClaimDtos.SupplierShortageResponse.class);
	}

	private SupplierClaimDtos.AdminShortageResponse readReviewReplayIfPresent(
		SupplierShortageReport report,
		String key,
		String hash
	) {
		if (report.getReviewIdempotencyKey() == null) return null;
		if (!report.getReviewIdempotencyKey().equals(key)) return null;
		if (!report.matchesReviewReplay(key, hash)) throw idempotencyConflict();
		return readJson(report.getReviewResultSnapshot(), SupplierClaimDtos.AdminShortageResponse.class);
	}

	private SupplierClaimDtos.SupplierShortageResponse toSupplierResponse(SupplierShortageReport report) {
		return new SupplierClaimDtos.SupplierShortageResponse(
			report.getId(), report.getOrder().getOrderNumber(), report.getReasonCode(), report.getStatus(),
			report.getCreatedAt(), report.getReviewedAt(), report.getReviewReasonCode(), nextAction(report)
		);
	}

	private SupplierClaimDtos.AdminShortageResponse toAdminResponse(SupplierShortageReport report) {
		return new SupplierClaimDtos.AdminShortageResponse(
			report.getId(), report.getOrder().getId(), report.getOrder().getOrderNumber(),
			report.getSupplier().getId(), report.getSupplier().getName(), report.getReasonCode(),
			report.getStatus(), report.getCreatedAt(), report.getReviewedAt(), report.getReviewReasonCode(),
			report.getReviewedByAdminId(), nextAction(report)
		);
	}

	private String nextAction(SupplierShortageReport report) {
		return switch (report.getStatus()) {
			case REPORTED -> "WAIT";
			case APPROVED -> "NONE";
			case REJECTED -> "CONTACT_COREABLE";
		};
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize supplier shortage result");
		}
	}

	private <T> T readJson(String value, Class<T> type) {
		try {
			return objectMapper.readValue(value, type);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to read supplier shortage result");
		}
	}

	private <T> CommandOutcome<T> requireOutcome(CommandOutcome<T> outcome) {
		if (outcome == null) throw new IllegalStateException("Shortage transaction returned no outcome");
		return outcome;
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

	private ApiErrorException shortageAlreadyReported() {
		return new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.SHORTAGE_ALREADY_REPORTED,
			"A shortage report already exists for this order");
	}

	private ApiErrorException idempotencyConflict() {
		return new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_CONFLICT,
			"Idempotency key conflict");
	}

	private record LockedShortageAggregate(
		CustomerOrder order,
		Fulfillment fulfillment,
		SupplierShortageReport report,
		List<Shipment> shipments,
		List<ShipmentItem> shipmentItems,
		List<OrderItem> orderItems
	) {
	}

	private record CommandOutcome<T>(T value, boolean cutoffReached) {
		static <T> CommandOutcome<T> success(T value) { return new CommandOutcome<>(value, false); }
		static <T> CommandOutcome<T> cutoff() { return new CommandOutcome<>(null, true); }
	}
}
