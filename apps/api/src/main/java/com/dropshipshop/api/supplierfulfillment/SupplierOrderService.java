package com.dropshipshop.api.supplierfulfillment;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.fulfillment.SupplierFulfillmentHandoverService;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.FulfillmentHandoverReasonCode;
import com.dropshipshop.api.fulfillment.domain.FulfillmentOperationalOwner;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.supplierportal.domain.FulfillmentHandoverHistory;
import com.dropshipshop.api.supplierportal.repository.FulfillmentHandoverHistoryRepository;
import com.dropshipshop.api.shipment.domain.ShipmentItem;
import com.dropshipshop.api.shipment.repository.ShipmentItemRepository;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@Service
public class SupplierOrderService {

	private static final Set<OrderStatus> TERMINAL_MASK_STATUSES = EnumSet.of(
		OrderStatus.OUT_OF_STOCK,
		OrderStatus.CANCELLED,
		OrderStatus.REFUND_REQUESTED,
		OrderStatus.REFUNDED
	);
	private static final Set<ClaimStatus> GRANT_STATUSES = EnumSet.of(
		ClaimStatus.APPROVED,
		ClaimStatus.RETURN_WAITING,
		ClaimStatus.RETURN_RECEIVED,
		ClaimStatus.REFUND_PROCESSING,
		ClaimStatus.EXCHANGE_SHIPPING
	);

	private final SupplierRepository supplierRepository;
	private final UserAccountRepository userAccountRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final FulfillmentHandoverHistoryRepository handoverHistoryRepository;
	private final SupplierPiiAccessGrantRepository grantRepository;
	private final SupplierPiiAccessLogRepository accessLogRepository;
	private final SupplierFulfillmentHandoverService handoverService;
	private final ShipmentItemRepository shipmentItemRepository;

	SupplierOrderService(
		SupplierRepository supplierRepository,
		UserAccountRepository userAccountRepository,
		FulfillmentRepository fulfillmentRepository,
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		FulfillmentHandoverHistoryRepository handoverHistoryRepository,
		SupplierPiiAccessGrantRepository grantRepository,
		SupplierPiiAccessLogRepository accessLogRepository,
		SupplierFulfillmentHandoverService handoverService,
		ShipmentItemRepository shipmentItemRepository
	) {
		this.supplierRepository = supplierRepository;
		this.userAccountRepository = userAccountRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.handoverHistoryRepository = handoverHistoryRepository;
		this.grantRepository = grantRepository;
		this.accessLogRepository = accessLogRepository;
		this.handoverService = handoverService;
		this.shipmentItemRepository = shipmentItemRepository;
	}

	@Transactional(readOnly = true)
	SupplierOrderDtos.OrderListResponse list(UUID actorUserId) {
		Supplier supplier = requireActiveTenant(actorUserId, Instant.now());
		List<SupplierOrderDtos.OrderSummaryResponse> orders = fulfillmentRepository
			.findSupplierQueue(supplier.getId()).stream()
			.map(fulfillment -> new SupplierOrderDtos.OrderSummaryResponse(
				fulfillment.getOrder().getOrderNumber(),
				status(fulfillment.getOrder()),
				fulfillment.getRequestedAt(),
				items(fulfillment).stream().map(item -> new SupplierOrderDtos.ListItemResponse(
					item.getProductName(), item.getOptionName(), item.getQuantity()
				)).toList()
			))
			.toList();
		return new SupplierOrderDtos.OrderListResponse(orders);
	}

	@Transactional
	SupplierOrderDtos.OrderDetailResponse detail(UUID actorUserId, String orderNumber) {
		Instant now = Instant.now();
		Supplier supplier = requireLockedActiveTenant(actorUserId, now);
		UUID orderId = fulfillmentRepository.findSupplierDetailOrderId(supplier.getId(), orderNumber)
			.orElseThrow(this::notFound);
		CustomerOrder order = orderRepository.findByIdForUpdate(orderId).orElseThrow(this::notFound);
		Fulfillment fulfillment = fulfillmentRepository.findByOrderIdForUpdate(orderId).orElseThrow(this::notFound);
		if (!supplier.getId().equals(order.getSupplier().getId())
			|| !supplier.getId().equals(fulfillment.getSupplier().getId())
			|| !orderNumber.equals(order.getOrderNumber())
			|| fulfillment.getChannel() != com.dropshipshop.api.fulfillment.domain.FulfillmentChannel.SUPPLIER_PORTAL) {
			throw notFound();
		}
		if (TERMINAL_MASK_STATUSES.contains(order.getStatus()) && fulfillment.isPortalSupplierOwned()) {
			handoverService.takeOverTerminal(order, now);
		} else if (fulfillment.isOpenPortalSupplierOwned()
			&& fulfillment.getPiiAccessCutoffAt() != null
			&& !now.isBefore(fulfillment.getPiiAccessCutoffAt())) {
			handoverService.enforceCutoffLazy(fulfillment.getId(), now);
		}

		AccessDecision decision = accessDecision(fulfillment, order, now);
		UserAccount actor = userAccountRepository.findByIdAndStatus(actorUserId, UserStatus.ACTIVE)
			.orElseThrow(this::forbidden);
		accessLogRepository.save(new SupplierPiiAccessLog(actor, order, decision.accessReason(), now));
		List<OrderItem> orderItems = items(fulfillment);
		Map<UUID, Integer> allocatedByItem = shipmentItemRepository
			.findAllByOrder_IdOrderByOrderItem_IdAsc(order.getId()).stream()
			.filter(allocation -> !allocation.getShipment().isVoided())
			.collect(Collectors.toMap(
				allocation -> allocation.getOrderItem().getId(),
				ShipmentItem::getQuantity,
				Integer::sum
			));
		return new SupplierOrderDtos.OrderDetailResponse(
			order.getOrderNumber(),
			status(order),
			fulfillment.getRequestedAt(),
			decision.full() ? "FULL" : "MASKED",
			decision.basis(),
			decision.accessUntil(),
			recipient(order, decision.full()),
			orderItems.stream().map(item -> {
				int allocated = allocatedByItem.getOrDefault(item.getId(), 0);
				return new SupplierOrderDtos.DetailItemResponse(
					item.getId(), item.getProductName(), item.getOptionName(), item.getQuantity(), allocated,
					Math.max(0, item.getQuantity() - allocated)
				);
			}).toList()
		);
	}

	private Supplier requireActiveTenant(UUID actorUserId, Instant now) {
		Supplier supplier = supplierRepository.findByManagerUserId(actorUserId).orElseThrow(this::forbidden);
		validateActiveTenant(supplier, actorUserId, now);
		return supplier;
	}

	private Supplier requireLockedActiveTenant(UUID actorUserId, Instant now) {
		Supplier supplier = supplierRepository.findByManagerUserIdForUpdate(actorUserId).orElseThrow(this::forbidden);
		validateActiveTenant(supplier, actorUserId, now);
		return supplier;
	}

	private void validateActiveTenant(Supplier supplier, UUID actorUserId, Instant now) {
		if (!isActiveTenant(supplier, now)
			|| !supplier.getManagerUserId().equals(actorUserId)
		) {
			throw forbidden();
		}
	}

	private boolean isActiveTenant(Supplier supplier, Instant now) {
		return supplier.getPortalStatus() == SupplierPortalStatus.ACTIVE
			&& supplier.getManagerUserId() != null
			&& userAccountRepository.findByIdAndStatus(supplier.getManagerUserId(), UserStatus.ACTIVE).isPresent()
			&& supplier.hasTimeValidContract(now);
	}

	private AccessDecision accessDecision(Fulfillment fulfillment, CustomerOrder order, Instant now) {
		if (fulfillment.getOperationalOwner() == FulfillmentOperationalOwner.SUPPLIER) {
			if (TERMINAL_MASK_STATUSES.contains(order.getStatus())) {
				return masked(SupplierPiiAccessReason.TERMINAL_MASKED, "TERMINAL_STATE", fulfillment);
			}
			if (fulfillment.getPiiAccessCutoffAt() != null && now.isBefore(fulfillment.getPiiAccessCutoffAt())) {
				return new AccessDecision(true, "NORMAL_WINDOW", fulfillment.getPiiAccessCutoffAt(),
					SupplierPiiAccessReason.NORMAL_FULL);
			}
			return masked(SupplierPiiAccessReason.EXPIRED_MASKED, "PII_CUTOFF", fulfillment);
		}

		FulfillmentHandoverHistory latest = handoverHistoryRepository
			.findFirstByFulfillment_IdOrderByCreatedAtDescIdDesc(fulfillment.getId())
			.orElseThrow(this::notFound);
		if (latest.getReasonCode() != FulfillmentHandoverReasonCode.PII_CUTOFF_REACHED
			&& latest.getReasonCode() != FulfillmentHandoverReasonCode.TERMINAL_STATE) {
			throw notFound();
		}
		SupplierPiiAccessGrant grant = activeGrant(order.getId(), now);
		if (grant != null) {
			return new AccessDecision(true, "CLAIM_GRANT", grant.getAccessUntil(), SupplierPiiAccessReason.CLAIM_FULL);
		}
		if (TERMINAL_MASK_STATUSES.contains(order.getStatus())) {
			return masked(SupplierPiiAccessReason.TERMINAL_MASKED, "TERMINAL_STATE", fulfillment);
		}
		return latest.getReasonCode() == FulfillmentHandoverReasonCode.TERMINAL_STATE
			? masked(SupplierPiiAccessReason.TERMINAL_MASKED, "TERMINAL_STATE", fulfillment)
			: masked(SupplierPiiAccessReason.EXPIRED_MASKED, "PII_CUTOFF", fulfillment);
	}

	@Transactional(readOnly = true)
	public boolean isOrderDetailAvailable(UUID supplierId, UUID orderId, Instant now) {
		return findOrderDetailAvailableOrderIds(supplierId, List.of(orderId), now).contains(orderId);
	}

	@Transactional(readOnly = true)
	public Set<UUID> findOrderDetailAvailableOrderIds(
		UUID supplierId,
		Collection<UUID> orderIds,
		Instant now
	) {
		if (orderIds.isEmpty()) return Set.of();
		List<Fulfillment> candidates = fulfillmentRepository
			.findSupplierDetailCandidates(supplierId, orderIds);
		if (candidates.isEmpty() || !isActiveTenant(candidates.get(0).getSupplier(), now)) return Set.of();

		Set<UUID> availableOrderIds = new HashSet<>();
		List<UUID> coreableFulfillmentIds = candidates.stream()
			.filter(fulfillment -> {
				if (fulfillment.getOperationalOwner() == FulfillmentOperationalOwner.SUPPLIER) {
					availableOrderIds.add(fulfillment.getOrder().getId());
					return false;
				}
				return fulfillment.getOperationalOwner() == FulfillmentOperationalOwner.COREABLE;
			})
			.map(Fulfillment::getId)
			.toList();
		if (!coreableFulfillmentIds.isEmpty()) {
			Set<UUID> handledFulfillmentIds = new HashSet<>();
			for (FulfillmentHandoverHistory history
				: handoverHistoryRepository.findAllLatestFirst(coreableFulfillmentIds)) {
				UUID fulfillmentId = history.getFulfillment().getId();
				if (!handledFulfillmentIds.add(fulfillmentId)) continue;
				if (history.getReasonCode() == FulfillmentHandoverReasonCode.PII_CUTOFF_REACHED
					|| history.getReasonCode() == FulfillmentHandoverReasonCode.TERMINAL_STATE) {
					availableOrderIds.add(history.getFulfillment().getOrder().getId());
				}
			}
		}
		return Set.copyOf(availableOrderIds);
	}

	private SupplierPiiAccessGrant activeGrant(UUID orderId, Instant now) {
		return grantRepository.findLatestStreamsByOrderId(orderId).stream()
			.filter(grant -> GRANT_STATUSES.contains(grant.getClaim().getStatus()))
			.filter(grant -> grant.isActiveAt(now))
			.findFirst()
			.orElse(null);
	}

	private AccessDecision masked(
		SupplierPiiAccessReason accessReason,
		String basis,
		Fulfillment fulfillment
	) {
		return new AccessDecision(false, basis, fulfillment.getPiiAccessCutoffAt(), accessReason);
	}

	private SupplierOrderDtos.RecipientResponse recipient(CustomerOrder order, boolean full) {
		if (full) {
			return new SupplierOrderDtos.RecipientResponse(
				order.getRecipientName(), digits(order.getRecipientPhone()), order.getPostalCode(),
				order.getAddress1(), order.getAddress2(), order.getDeliveryMemo()
			);
		}
		return new SupplierOrderDtos.RecipientResponse(
			maskName(order.getRecipientName()), maskPhone(order.getRecipientPhone()), null, null, null, null
		);
	}

	private List<OrderItem> items(Fulfillment fulfillment) {
		UUID supplierId = fulfillment.getSupplier().getId();
		return orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(fulfillment.getOrder().getId()).stream()
			.filter(item -> supplierId.equals(item.getSupplier().getId()))
			.filter(item -> item.getManagementChannelSnapshot()
				== com.dropshipshop.api.catalog.domain.ProductManagementChannel.SUPPLIER_PORTAL)
			.toList();
	}

	private String status(CustomerOrder order) {
		return switch (order.getStatus()) {
			case SUPPLIER_ORDER_PENDING, SUPPLIER_ORDERED -> "FULFILLMENT_REQUESTED";
			case TRACKING_REGISTERED -> "TRACKING_REGISTERED";
			case SHIPPED -> "TRACKING_REGISTERED";
			case DELIVERED -> "DELIVERED";
			default -> "CLOSED";
		};
	}

	private String maskName(String value) {
		if (value == null || value.isBlank()) return "*";
		int count = value.codePointCount(0, value.length());
		if (count <= 1) return "*";
		int firstEnd = value.offsetByCodePoints(0, 1);
		return value.substring(0, firstEnd) + "**";
	}

	private String maskPhone(String value) {
		String digits = digits(value);
		if (digits.length() <= 4) return "*".repeat(digits.length());
		return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
	}

	private String digits(String value) {
		return value == null ? "" : value.replaceAll("[^0-9]", "");
	}

	private ApiErrorException notFound() {
		return new ApiErrorException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
	}

	private ApiErrorException forbidden() {
		return new ApiErrorException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, "Supplier access is unavailable");
	}

	private record AccessDecision(
		boolean full,
		String basis,
		Instant accessUntil,
		SupplierPiiAccessReason accessReason
	) {
	}
}
