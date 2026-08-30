package com.dropshipshop.api.supplierclaim;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimFact;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimRequestedType;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTask;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTaskCloseReasonCode;
import com.dropshipshop.api.supplierclaim.domain.SupplierClaimTaskStatus;
import com.dropshipshop.api.supplierclaim.repository.SupplierClaimFactRepository;
import com.dropshipshop.api.supplierclaim.repository.SupplierClaimTaskRepository;
import com.dropshipshop.api.supplierclaim.repository.SupplierClaimTaskRepository.TaskScope;
import com.dropshipshop.api.supplierfulfillment.SupplierOrderService;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.SupplierPortalInputPolicy;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SupplierClaimTaskService {

	private static final Set<ClaimStatus> INPUT_CLAIM_STATUSES = EnumSet.of(
		ClaimStatus.REQUESTED,
		ClaimStatus.UNDER_REVIEW,
		ClaimStatus.EVIDENCE_REQUESTED,
		ClaimStatus.APPROVED,
		ClaimStatus.RETURN_WAITING,
		ClaimStatus.RETURN_RECEIVED,
		ClaimStatus.REFUND_PROCESSING,
		ClaimStatus.EXCHANGE_SHIPPING
	);
	private static final Set<ClaimStatus> TERMINAL_CLAIM_STATUSES = EnumSet.of(
		ClaimStatus.REJECTED,
		ClaimStatus.COMPLETED,
		ClaimStatus.WITHDRAWN
	);
	private static final Set<SupplierClaimTaskStatus> OPEN_TASK_STATUSES = EnumSet.of(
		SupplierClaimTaskStatus.OPEN,
		SupplierClaimTaskStatus.ANSWERED
	);

	private final SupplierRepository supplierRepository;
	private final UserAccountRepository userAccountRepository;
	private final CustomerOrderRepository orderRepository;
	private final ClaimRepository claimRepository;
	private final SupplierClaimTaskRepository taskRepository;
	private final SupplierClaimFactRepository factRepository;
	private final OrderItemRepository orderItemRepository;
	private final SupplierOrderService supplierOrderService;
	private final SupplierClaimFactPayloadPolicy payloadPolicy;
	private final SupplierPortalInputPolicy inputPolicy;
	private final SupplierPortalHasher hasher;
	private final SupplierPortalFeatureGate featureGate;
	private final NotificationService notificationService;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate writeTransaction;

	SupplierClaimTaskService(
		SupplierRepository supplierRepository,
		UserAccountRepository userAccountRepository,
		CustomerOrderRepository orderRepository,
		ClaimRepository claimRepository,
		SupplierClaimTaskRepository taskRepository,
		SupplierClaimFactRepository factRepository,
		OrderItemRepository orderItemRepository,
		SupplierOrderService supplierOrderService,
		SupplierClaimFactPayloadPolicy payloadPolicy,
		SupplierPortalInputPolicy inputPolicy,
		SupplierPortalHasher hasher,
		SupplierPortalFeatureGate featureGate,
		NotificationService notificationService,
		ObjectMapper objectMapper,
		PlatformTransactionManager transactionManager
	) {
		this.supplierRepository = supplierRepository;
		this.userAccountRepository = userAccountRepository;
		this.orderRepository = orderRepository;
		this.claimRepository = claimRepository;
		this.taskRepository = taskRepository;
		this.factRepository = factRepository;
		this.orderItemRepository = orderItemRepository;
		this.supplierOrderService = supplierOrderService;
		this.payloadPolicy = payloadPolicy;
		this.inputPolicy = inputPolicy;
		this.hasher = hasher;
		this.featureGate = featureGate;
		this.notificationService = notificationService;
		this.objectMapper = objectMapper;
		this.writeTransaction = new TransactionTemplate(transactionManager);
	}

	public SupplierClaimDtos.AdminTaskResponse create(
		UUID actorUserId,
		UUID claimId,
		String idempotencyKey,
		SupplierClaimDtos.TaskCreateRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String hash = hasher.hmac("supplier-claim-task-create", claimId.toString(), actorUserId.toString(),
			request.requestedType().name(), request.instructionCode().name(), request.dueAt().toString());
		return requireOutcome(writeTransaction.execute(status -> TaskOutcome.success(
			createLocked(actorUserId, claimId, key, hash, request)
		))).value();
	}

	@Transactional(readOnly = true)
	public SupplierClaimDtos.SupplierTaskListResponse listSupplier(
		UUID actorUserId,
		SupplierClaimTaskStatus status
	) {
		Supplier supplier = requireActiveTenant(actorUserId, false);
		List<SupplierClaimTask> tasks = taskRepository.findSupplierList(supplier.getId(), status);
		Map<UUID, List<SupplierClaimDtos.TaskItemResponse>> itemsByTask = supplierListItems(
			tasks, supplier.getId()
		);
		Set<UUID> availableOrderIds = featureGate.isEnabled()
			? supplierOrderService.findOrderDetailAvailableOrderIds(
				supplier.getId(), tasks.stream().map(task -> task.getOrder().getId()).toList(), Instant.now()
			)
			: Set.of();
		return new SupplierClaimDtos.SupplierTaskListResponse(
			tasks.stream()
				.map(task -> toSupplierSummaryResponse(
					task,
					itemsByTask.getOrDefault(task.getId(), List.of()),
					availableOrderIds.contains(task.getOrder().getId())
				))
				.toList()
		);
	}

	@Transactional(readOnly = true)
	public SupplierClaimDtos.SupplierTaskResponse detailSupplier(UUID actorUserId, UUID taskId) {
		Supplier supplier = requireActiveTenant(actorUserId, false);
		SupplierClaimTask task = taskRepository.findByIdAndSupplier_Id(taskId, supplier.getId())
			.orElseThrow(this::notFound);
		return toSupplierResponse(task, facts(taskId));
	}

	@Transactional(readOnly = true)
	public SupplierClaimDtos.AdminTaskListResponse listAdmin(
		SupplierClaimTaskStatus status,
		UUID claimId,
		UUID orderId
	) {
		List<SupplierClaimTask> tasks = taskRepository.findAdminList(status, claimId, orderId);
		Map<UUID, List<SupplierClaimDtos.TaskItemResponse>> itemsByTask = adminListItems(tasks);
		return new SupplierClaimDtos.AdminTaskListResponse(
			tasks.stream()
				.map(task -> toAdminSummaryResponse(
					task, itemsByTask.getOrDefault(task.getId(), List.of())
				))
				.toList()
		);
	}

	@Transactional(readOnly = true)
	public SupplierClaimDtos.AdminTaskResponse detailAdmin(UUID taskId) {
		SupplierClaimTask task = taskRepository.findById(taskId).orElseThrow(this::notFound);
		return toAdminResponse(task, facts(taskId));
	}

	public SupplierClaimDtos.SupplierTaskResponse addFact(
		UUID actorUserId,
		UUID taskId,
		String idempotencyKey,
		SupplierClaimDtos.FactCreateRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String hash = hasher.hmac("supplier-claim-fact", taskId.toString(), actorUserId.toString(),
			request.type().name(), canonicalRaw(request.payload()),
			request.correctsFactId() == null ? null : request.correctsFactId().toString());
		TaskOutcome<SupplierClaimDtos.SupplierTaskResponse> outcome = requireOutcome(
			writeTransaction.execute(status -> addFactLocked(actorUserId, taskId, key, hash, request))
		);
		if (outcome.rejection() == Rejection.DEADLINE) {
			throw conflict("Supplier claim task deadline has expired");
		}
		if (outcome.rejection() == Rejection.CLAIM_TERMINAL) {
			throw conflict("Claim is terminal");
		}
		return outcome.value();
	}

	public SupplierClaimDtos.AdminTaskResponse close(
		UUID actorUserId,
		UUID taskId,
		String idempotencyKey,
		SupplierClaimDtos.TaskCloseRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String hash = hasher.hmac("supplier-claim-task-close", taskId.toString(), actorUserId.toString(),
			request.expectedStatus().name(), request.closeReasonCode().name());
		TaskOutcome<SupplierClaimDtos.AdminTaskResponse> outcome = requireOutcome(
			writeTransaction.execute(status -> closeLocked(actorUserId, taskId, key, hash, request))
		);
		if (outcome.rejection() == Rejection.DEADLINE) {
			throw conflict("Supplier claim task deadline has expired");
		}
		if (outcome.rejection() == Rejection.CLAIM_TERMINAL) {
			throw conflict("Claim is terminal");
		}
		return outcome.value();
	}

	@Transactional(readOnly = true)
	public List<UUID> deadlineCandidateIds(Instant now) {
		return taskRepository.findExpiredCandidateIds(OPEN_TASK_STATUSES, now, PageRequest.of(0, 100));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean expire(UUID taskId, Instant now) {
		TaskScope scope = taskRepository.findScopeById(taskId).orElse(null);
		if (scope == null) return false;
		LockedClaimAggregate aggregate = lockAggregate(scope.getOrderId(), scope.getClaimId());
		SupplierClaimTask task = findTask(aggregate.tasks(), taskId);
		if (!task.acceptsInput()) return false;
		if (TERMINAL_CLAIM_STATUSES.contains(aggregate.claim().getStatus())) {
			return task.closeBySystem(SupplierClaimTaskCloseReasonCode.CLAIM_TERMINAL, now);
		}
		if (now.isBefore(task.getDueAt())) return false;
		return task.closeBySystem(SupplierClaimTaskCloseReasonCode.DUE_AT_EXPIRED, now);
	}

	@Transactional
	public void lockForClaim(UUID claimId) {
		List<SupplierClaimTask> tasks = taskRepository.findAllByClaimIdForUpdate(claimId);
		for (SupplierClaimTask task : tasks) {
			factRepository.findAllByTaskIdForUpdate(task.getId());
		}
	}

	@Transactional
	public void closeForTerminalClaim(Claim claim, Instant now) {
		if (!TERMINAL_CLAIM_STATUSES.contains(claim.getStatus())) {
			throw new IllegalStateException("Claim is not terminal");
		}
		List<SupplierClaimTask> tasks = taskRepository.findAllByClaimIdForUpdate(claim.getId());
		for (SupplierClaimTask task : tasks) {
			factRepository.findAllByTaskIdForUpdate(task.getId());
			task.closeBySystem(SupplierClaimTaskCloseReasonCode.CLAIM_TERMINAL, now);
		}
	}

	private SupplierClaimDtos.AdminTaskResponse createLocked(
		UUID actorUserId,
		UUID claimId,
		String key,
		String hash,
		SupplierClaimDtos.TaskCreateRequest request
	) {
		UUID orderId = claimRepository.findOrderIdById(claimId).orElseThrow(this::notFound);
		LockedClaimAggregate aggregate = lockAggregate(orderId, claimId);
		SupplierClaimTask replay = aggregate.tasks().stream()
			.filter(task -> key.equals(task.getCreationIdempotencyKey()))
			.findFirst().orElse(null);
		if (replay != null) return readCreationReplay(replay, hash);
		featureGate.requireOperationalMutationReleased();
		if (!INPUT_CLAIM_STATUSES.contains(aggregate.claim().getStatus())) {
			throw conflict("Claim status does not allow a supplier task");
		}
		if (request.instructionCode().requestedType() != request.requestedType()) {
			throw validation("Requested type and instruction code do not match");
		}
		Instant now = Instant.now();
		if (!request.dueAt().isAfter(now) || request.dueAt().isAfter(now.plus(30, ChronoUnit.DAYS))) {
			throw validation("dueAt must be strictly future and at most 30 days from requestedAt");
		}
		Supplier supplier = aggregate.order().getSupplier();
		validateActiveSupplier(supplier, now);
		SupplierClaimTask task = new SupplierClaimTask(
			aggregate.claim(), supplier, request.requestedType(), request.instructionCode(), actorUserId,
			hash, key, now, request.dueAt()
		);
		SupplierClaimDtos.AdminTaskResponse response = toAdminResponse(task, List.of());
		task.initializeCreationResult(json(response));
		taskRepository.save(task);
		notificationService.supplierClaimWorkRequested(supplier, aggregate.order(), aggregate.claim());
		return response;
	}

	private TaskOutcome<SupplierClaimDtos.SupplierTaskResponse> addFactLocked(
		UUID actorUserId,
		UUID taskId,
		String key,
		String hash,
		SupplierClaimDtos.FactCreateRequest request
	) {
		Supplier supplier = requireActiveTenant(actorUserId, true);
		TaskScope scope = taskRepository.findScopeByIdAndSupplierId(taskId, supplier.getId())
			.orElseThrow(this::notFound);
		SupplierClaimFact earlyReplay = factRepository
			.findByTask_IdAndTask_Supplier_IdAndIdempotencyKey(taskId, supplier.getId(), key)
			.orElse(null);
		if (earlyReplay != null) return TaskOutcome.success(readFactReplay(earlyReplay, hash));

		LockedClaimAggregate aggregate = lockAggregate(scope.getOrderId(), scope.getClaimId());
		SupplierClaimTask task = findTask(aggregate.tasks(), taskId);
		if (!supplier.getId().equals(task.getSupplier().getId())) throw notFound();
		List<SupplierClaimFact> facts = aggregate.factsByTask().getOrDefault(taskId, List.of());
		SupplierClaimFact replay = facts.stream()
			.filter(fact -> key.equals(fact.getIdempotencyKey()))
			.findFirst().orElse(null);
		if (replay != null) return TaskOutcome.success(readFactReplay(replay, hash));
		Instant now = Instant.now();
		if (TERMINAL_CLAIM_STATUSES.contains(aggregate.claim().getStatus())) {
			task.closeBySystem(SupplierClaimTaskCloseReasonCode.CLAIM_TERMINAL, now);
			return TaskOutcome.rejected(Rejection.CLAIM_TERMINAL);
		}
		if (!INPUT_CLAIM_STATUSES.contains(aggregate.claim().getStatus())) {
			throw conflict("Claim status does not allow supplier facts");
		}
		if (!task.acceptsInput()) throw conflict("Supplier claim task is closed");
		if (!now.isBefore(task.getDueAt())) {
			task.closeBySystem(SupplierClaimTaskCloseReasonCode.DUE_AT_EXPIRED, now);
			return TaskOutcome.rejected(Rejection.DEADLINE);
		}
		if (request.type() != task.getRequestedType()) {
			throw validation("Fact type must match the requested task type");
		}
		String normalizedPayload = payloadPolicy.normalize(request.type(), request.payload(), task.getRequestedAt(), now);
		SupplierClaimFact correction = validateCorrection(task, facts, request.correctsFactId());
		SupplierClaimFact fact = new SupplierClaimFact(
			task, actorUserId, normalizedPayload, correction, hash, key, now
		);
		if (task.getStatus() == SupplierClaimTaskStatus.OPEN) task.answer(now);
		List<SupplierClaimFact> withNewFact = new ArrayList<>(facts);
		withNewFact.add(fact);
		SupplierClaimDtos.SupplierTaskResponse response = toSupplierResponse(task, chain(withNewFact));
		fact.initializeResult(json(response));
		factRepository.save(fact);
		return TaskOutcome.success(response);
	}

	private TaskOutcome<SupplierClaimDtos.AdminTaskResponse> closeLocked(
		UUID actorUserId,
		UUID taskId,
		String key,
		String hash,
		SupplierClaimDtos.TaskCloseRequest request
	) {
		TaskScope scope = taskRepository.findScopeById(taskId).orElseThrow(this::notFound);
		LockedClaimAggregate aggregate = lockAggregate(scope.getOrderId(), scope.getClaimId());
		SupplierClaimTask task = findTask(aggregate.tasks(), taskId);
		if (key.equals(task.getCloseIdempotencyKey())) {
			if (!task.matchesCloseReplay(key, hash)) throw idempotencyConflict();
			SupplierClaimDtos.AdminTaskResponse stored = readJson(
				task.getCloseResultSnapshot(), SupplierClaimDtos.AdminTaskResponse.class
			);
			return TaskOutcome.success(stored);
		}
		if (!request.closeReasonCode().isAdminReason()) {
			throw validation("Only an allowlisted admin close reason is accepted");
		}
		Instant now = Instant.now();
		if (TERMINAL_CLAIM_STATUSES.contains(aggregate.claim().getStatus())) {
			task.closeBySystem(SupplierClaimTaskCloseReasonCode.CLAIM_TERMINAL, now);
			return TaskOutcome.rejected(Rejection.CLAIM_TERMINAL);
		}
		if (!INPUT_CLAIM_STATUSES.contains(aggregate.claim().getStatus())) {
			throw conflict("Claim status does not allow task close");
		}
		if (!task.acceptsInput() || request.expectedStatus() != task.getStatus()) {
			throw conflict("Supplier claim task status changed");
		}
		if (!now.isBefore(task.getDueAt())) {
			task.closeBySystem(SupplierClaimTaskCloseReasonCode.DUE_AT_EXPIRED, now);
			return TaskOutcome.rejected(Rejection.DEADLINE);
		}
		List<SupplierClaimFact> facts = chain(aggregate.factsByTask().getOrDefault(taskId, List.of()));
		List<SupplierClaimDtos.TaskItemResponse> itemResponses = items(task);
		task.closeByAdmin(actorUserId, request.closeReasonCode(), hash, key, now);
		SupplierClaimDtos.AdminTaskResponse response = toAdminResponse(task, facts, itemResponses);
		task.initializeCloseResult(json(response));
		return TaskOutcome.success(response);
	}

	private LockedClaimAggregate lockAggregate(UUID orderId, UUID claimId) {
		CustomerOrder order = orderRepository.findByIdForUpdate(orderId).orElseThrow(this::notFound);
		Claim claim = claimRepository.findByIdForUpdate(claimId).orElseThrow(this::notFound);
		if (!order.getId().equals(claim.getOrder().getId())) throw notFound();
		List<SupplierClaimTask> tasks = taskRepository.findAllByClaimIdForUpdate(claimId);
		Map<UUID, List<SupplierClaimFact>> factsByTask = new LinkedHashMap<>();
		for (SupplierClaimTask task : tasks) {
			factsByTask.put(task.getId(), factRepository.findAllByTaskIdForUpdate(task.getId()));
		}
		return new LockedClaimAggregate(order, claim, tasks, factsByTask);
	}

	private SupplierClaimFact validateCorrection(
		SupplierClaimTask task,
		List<SupplierClaimFact> facts,
		UUID correctsFactId
	) {
		if (task.getStatus() == SupplierClaimTaskStatus.OPEN) {
			if (!facts.isEmpty() || correctsFactId != null) {
				throw conflict("The first fact cannot be a correction");
			}
			return null;
		}
		if (correctsFactId == null) throw validation("ANSWERED task facts must correct the latest fact");
		List<SupplierClaimFact> ordered = chain(facts);
		SupplierClaimFact head = ordered.isEmpty() ? null : ordered.get(ordered.size() - 1);
		if (head == null || !head.getId().equals(correctsFactId)) {
			throw conflict("Correction must reference the latest effective fact");
		}
		return head;
	}

	private List<SupplierClaimFact> facts(UUID taskId) {
		return chain(factRepository.findAllByTask_IdOrderByCreatedAtAscIdAsc(taskId));
	}

	private List<SupplierClaimFact> chain(List<SupplierClaimFact> facts) {
		if (facts.isEmpty()) return List.of();
		Map<UUID, SupplierClaimFact> byId = new HashMap<>();
		Map<UUID, SupplierClaimFact> childByParent = new HashMap<>();
		List<SupplierClaimFact> roots = new ArrayList<>();
		Set<UUID> referenced = new HashSet<>();
		for (SupplierClaimFact fact : facts) {
			if (byId.put(fact.getId(), fact) != null) throw invalidFactChain();
			if (fact.getCorrectsFact() == null) {
				roots.add(fact);
			} else {
				UUID parentId = fact.getCorrectsFact().getId();
				if (childByParent.put(parentId, fact) != null) throw invalidFactChain();
				referenced.add(parentId);
			}
		}
		List<SupplierClaimFact> heads = facts.stream()
			.filter(fact -> !referenced.contains(fact.getId()))
			.toList();
		if (roots.size() != 1 || heads.size() != 1) throw invalidFactChain();
		List<SupplierClaimFact> ordered = new ArrayList<>();
		SupplierClaimFact current = roots.get(0);
		while (current != null && ordered.size() <= facts.size()) {
			ordered.add(current);
			current = childByParent.get(current.getId());
		}
		if (ordered.size() != facts.size()
			|| !ordered.get(ordered.size() - 1).getId().equals(heads.get(0).getId())) {
			throw invalidFactChain();
		}
		return List.copyOf(ordered);
	}

	private SupplierClaimDtos.SupplierTaskResponse toSupplierResponse(
		SupplierClaimTask task,
		List<SupplierClaimFact> facts
	) {
		CustomerOrder order = task.getOrder();
		return new SupplierClaimDtos.SupplierTaskResponse(
			task.getId(), order.getOrderNumber(), orderDetailAvailable(task), items(task),
			task.getRequestedType(), task.getStatus(), task.getInstructionCode(), task.getInstructions(),
			task.getDueAt(), task.getRequestedAt(), task.getAnsweredAt(), task.getClosedAt(),
			task.getCloseReasonCode(), facts.stream().map(this::toFactResponse).toList()
		);
	}

	private SupplierClaimDtos.SupplierTaskSummaryResponse toSupplierSummaryResponse(
		SupplierClaimTask task,
		List<SupplierClaimDtos.TaskItemResponse> itemResponses,
		boolean orderDetailAvailable
	) {
		CustomerOrder order = task.getOrder();
		return new SupplierClaimDtos.SupplierTaskSummaryResponse(
			task.getId(), order.getOrderNumber(), orderDetailAvailable, itemResponses,
			task.getRequestedType(), task.getStatus(), task.getInstructionCode(), task.getInstructions(),
			task.getDueAt(), task.getRequestedAt(), task.getAnsweredAt(), task.getClosedAt(),
			task.getCloseReasonCode()
		);
	}

	private SupplierClaimDtos.AdminTaskResponse toAdminResponse(
		SupplierClaimTask task,
		List<SupplierClaimFact> facts
	) {
		return toAdminResponse(task, facts, items(task));
	}

	private SupplierClaimDtos.AdminTaskResponse toAdminResponse(
		SupplierClaimTask task,
		List<SupplierClaimFact> facts,
		List<SupplierClaimDtos.TaskItemResponse> itemResponses
	) {
		CustomerOrder order = task.getOrder();
		return new SupplierClaimDtos.AdminTaskResponse(
			task.getId(), task.getClaim().getId(), order.getId(), order.getOrderNumber(),
			task.getSupplier().getId(), task.getSupplier().getName(), itemResponses,
			task.getRequestedType(), task.getStatus(), task.getInstructionCode(), task.getInstructions(),
			task.getDueAt(), task.getRequestedAt(), task.getAnsweredAt(), task.getClosedAt(),
			task.getCloseReasonCode(), task.getRequestedByAdminId(), task.getClosedByAdminId(),
			facts.stream().map(this::toFactResponse).toList()
		);
	}

	private SupplierClaimDtos.AdminTaskSummaryResponse toAdminSummaryResponse(
		SupplierClaimTask task,
		List<SupplierClaimDtos.TaskItemResponse> itemResponses
	) {
		CustomerOrder order = task.getOrder();
		return new SupplierClaimDtos.AdminTaskSummaryResponse(
			task.getId(), task.getClaim().getId(), order.getId(), order.getOrderNumber(),
			task.getSupplier().getId(), task.getSupplier().getName(), itemResponses,
			task.getRequestedType(), task.getStatus(), task.getInstructionCode(), task.getInstructions(),
			task.getDueAt(), task.getRequestedAt(), task.getAnsweredAt(), task.getClosedAt(),
			task.getCloseReasonCode(), task.getRequestedByAdminId(), task.getClosedByAdminId()
		);
	}

	private SupplierClaimDtos.FactResponse toFactResponse(SupplierClaimFact fact) {
		return new SupplierClaimDtos.FactResponse(
			fact.getId(), fact.getType(), readTree(fact.getPayload()),
			fact.getCorrectsFact() == null ? null : fact.getCorrectsFact().getId(), fact.getCreatedAt()
		);
	}

	private List<SupplierClaimDtos.TaskItemResponse> items(SupplierClaimTask task) {
		UUID supplierId = task.getSupplier().getId();
		return orderItemRepository.findAllByOrderIdAndSupplierId(task.getOrder().getId(), supplierId)
			.stream()
			.map(item -> new SupplierClaimDtos.TaskItemResponse(
				item.getProductName(), item.getOptionName(), item.getQuantity()
			))
			.toList();
	}

	private Map<UUID, List<SupplierClaimDtos.TaskItemResponse>> supplierListItems(
		List<SupplierClaimTask> tasks,
		UUID supplierId
	) {
		if (tasks.isEmpty()) return Map.of();
		return groupListItems(taskRepository.findSupplierListItems(
			tasks.stream().map(SupplierClaimTask::getId).toList(), supplierId
		));
	}

	private Map<UUID, List<SupplierClaimDtos.TaskItemResponse>> adminListItems(
		List<SupplierClaimTask> tasks
	) {
		if (tasks.isEmpty()) return Map.of();
		return groupListItems(taskRepository.findAdminListItems(
			tasks.stream().map(SupplierClaimTask::getId).toList()
		));
	}

	private Map<UUID, List<SupplierClaimDtos.TaskItemResponse>> groupListItems(
		List<SupplierClaimTaskRepository.TaskItemRow> rows
	) {
		Map<UUID, List<SupplierClaimDtos.TaskItemResponse>> itemsByTask = new LinkedHashMap<>();
		for (SupplierClaimTaskRepository.TaskItemRow row : rows) {
			itemsByTask.computeIfAbsent(row.getTaskId(), ignored -> new ArrayList<>())
				.add(new SupplierClaimDtos.TaskItemResponse(
					row.getProductName(), row.getOptionName(), row.getQuantity()
				));
		}
		return itemsByTask;
	}

	private boolean orderDetailAvailable(SupplierClaimTask task) {
		return featureGate.isEnabled() && supplierOrderService.isOrderDetailAvailable(
			task.getSupplier().getId(), task.getOrder().getId(), Instant.now()
		);
	}

	private SupplierClaimTask findTask(List<SupplierClaimTask> tasks, UUID taskId) {
		return tasks.stream().filter(task -> task.getId().equals(taskId))
			.findFirst().orElseThrow(this::notFound);
	}

	private Supplier requireActiveTenant(UUID actorUserId, boolean locked) {
		Supplier supplier = (locked
			? supplierRepository.findByManagerUserIdForUpdate(actorUserId)
			: supplierRepository.findByManagerUserId(actorUserId))
			.orElseThrow(this::forbidden);
		validateActiveSupplier(supplier, Instant.now());
		if (!actorUserId.equals(supplier.getManagerUserId())) throw forbidden();
		return supplier;
	}

	private void validateActiveSupplier(Supplier supplier, Instant now) {
		if (supplier.getPortalStatus() != SupplierPortalStatus.ACTIVE
			|| supplier.getManagerUserId() == null
			|| userAccountRepository.findByIdAndStatus(supplier.getManagerUserId(), UserStatus.ACTIVE).isEmpty()
			|| !supplier.hasTimeValidContract(now)) {
			throw forbidden();
		}
	}

	private SupplierClaimDtos.AdminTaskResponse readCreationReplay(SupplierClaimTask task, String hash) {
		if (!task.matchesCreationReplay(hash)) throw idempotencyConflict();
		return readJson(
			task.getCreationResultSnapshot(), SupplierClaimDtos.AdminTaskResponse.class
		);
	}

	private SupplierClaimDtos.SupplierTaskResponse readFactReplay(SupplierClaimFact fact, String hash) {
		if (!fact.matchesReplay(hash)) throw idempotencyConflict();
		SupplierClaimDtos.SupplierTaskResponse stored = readJson(
			fact.getResultSnapshot(), SupplierClaimDtos.SupplierTaskResponse.class
		);
		return withOrderDetailAvailability(stored, orderDetailAvailable(fact.getTask()));
	}

	private SupplierClaimDtos.SupplierTaskResponse withOrderDetailAvailability(
		SupplierClaimDtos.SupplierTaskResponse response,
		boolean available
	) {
		return new SupplierClaimDtos.SupplierTaskResponse(
			response.taskId(), response.orderNumber(), available, response.items(),
			response.requestedType(), response.status(), response.instructionCode(), response.instructions(),
			response.dueAt(), response.requestedAt(), response.answeredAt(), response.closedAt(),
			response.closeReasonCode(), response.facts()
		);
	}

	private String canonicalRaw(JsonNode node) {
		try {
			return objectMapper.writeValueAsString(canonicalValue(node));
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to canonicalize supplier fact request");
		}
	}

	private Object canonicalValue(JsonNode node) {
		if (node == null || node.isNull()) return null;
		if (node.isObject()) {
			Map<String, Object> result = new TreeMap<>();
			for (Map.Entry<String, JsonNode> entry : node.properties()) {
				result.put(entry.getKey(), canonicalValue(entry.getValue()));
			}
			return result;
		}
		if (node.isArray()) {
			List<Object> result = new ArrayList<>();
			for (JsonNode element : node) result.add(canonicalValue(element));
			return result;
		}
		if (node.isBoolean()) return node.asBoolean();
		if (node.isNumber()) return node.numberValue();
		return node.asText();
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize supplier claim result");
		}
	}

	private JsonNode readTree(String value) {
		try {
			return objectMapper.readTree(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to read supplier fact payload");
		}
	}

	private <T> T readJson(String value, Class<T> type) {
		try {
			return objectMapper.readValue(value, type);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to read supplier claim result");
		}
	}

	private <T> TaskOutcome<T> requireOutcome(TaskOutcome<T> outcome) {
		return Objects.requireNonNull(outcome, "Supplier claim transaction returned no result");
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

	private IllegalStateException invalidFactChain() {
		return new IllegalStateException("Supplier claim fact correction chain is invalid");
	}

	private record LockedClaimAggregate(
		CustomerOrder order,
		Claim claim,
		List<SupplierClaimTask> tasks,
		Map<UUID, List<SupplierClaimFact>> factsByTask
	) {
	}

	private enum Rejection { NONE, DEADLINE, CLAIM_TERMINAL }

	private record TaskOutcome<T>(T value, Rejection rejection) {
		static <T> TaskOutcome<T> success(T value) { return new TaskOutcome<>(value, Rejection.NONE); }
		static <T> TaskOutcome<T> rejected(Rejection rejection) { return new TaskOutcome<>(null, rejection); }
	}
}
