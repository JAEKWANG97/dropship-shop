package com.dropshipshop.api.procurement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.domain.SupplierPurchaseAttempt;
import com.dropshipshop.api.fulfillment.domain.SupplierPurchaseStatus;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.fulfillment.repository.SupplierPurchaseAttemptRepository;
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
import com.dropshipshop.api.shipment.repository.ShipmentRepository;

@Service
public class DomeggookPurchaseService {

	private final DomeggookProperties properties;
	private final DomeggookPurchaseClient client;
	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final SupplierPurchaseAttemptRepository attemptRepository;
	private final ShipmentRepository shipmentRepository;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;
	private final OrderStatusHistoryRepository statusHistoryRepository;
	private final NotificationService notificationService;
	private final TransactionTemplate transactionTemplate;

	DomeggookPurchaseService(
		DomeggookProperties properties,
		DomeggookPurchaseClient client,
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		FulfillmentRepository fulfillmentRepository,
		SupplierPurchaseAttemptRepository attemptRepository,
		ShipmentRepository shipmentRepository,
		AdminOrderActionHistoryRepository actionHistoryRepository,
		OrderStatusHistoryRepository statusHistoryRepository,
		NotificationService notificationService,
		TransactionTemplate transactionTemplate
	) {
		this.properties = properties;
		this.client = client;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.attemptRepository = attemptRepository;
		this.shipmentRepository = shipmentRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.notificationService = notificationService;
		this.transactionTemplate = transactionTemplate;
	}

	@Transactional
	public void queueAfterDeposit(CustomerOrder order, UUID adminUserId) {
		if (!properties.enabled()) return;
		List<OrderItem> items = orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(order.getId());
		if (!isDomeggookOrder(items)) return;
		Instant now = Instant.now();
		order.startSupplierOrderWork(adminUserId, now);
		Fulfillment fulfillment = fulfillmentRepository.findByOrder_Id(order.getId())
			.orElseGet(() -> new Fulfillment(order));
		fulfillment.startWork(now);
		fulfillment.queueDomeggookPurchase(snapshotAmount(items), fingerprint(order, items));
		fulfillmentRepository.save(fulfillment);
	}

	public ValidationResult validate(UUID orderId) {
		try {
			PurchaseContext context = readContext(orderId);
			ValidationResult result = validate(context);
			transactionTemplate.executeWithoutResult(status -> fulfillmentRepository.findByIdForUpdate(context.fulfillmentId())
				.orElseThrow()
				.updateExpectedSourceAmount(result.expectedAmount()));
			return result;
		} catch (DomeggookApiException exception) {
			throw new IllegalStateException(exception.getMessage(), exception);
		}
	}

	public void process(UUID fulfillmentId) {
		PurchaseContext context = claim(fulfillmentId);
		if (context == null) return;
		UUID attemptId = null;
		try {
			ValidationResult validation = validate(context);
			if (client.emoneyBalance() < validation.expectedAmount()) {
				throw new DomeggookApiException("EMONEY_INSUFFICIENT", "Domeggook e-money balance is insufficient", false);
			}
			attemptId = beginAttempt(context, "ORDER", validation.expectedAmount());
			DomeggookPurchaseClient.OrderResult result = client.placeOrder(orderRequest(context));
			completeOrder(context, attemptId, result);
		} catch (DomeggookApiException exception) {
			fail(context.fulfillmentId(), attemptId, exception);
		} catch (RuntimeException exception) {
			fail(context.fulfillmentId(), attemptId,
				new DomeggookApiException("INTERNAL_ERROR", "Internal supplier purchase error", attemptId != null));
		}
	}

	public void retry(UUID orderId) {
		transactionTemplate.executeWithoutResult(status -> {
			Fulfillment fulfillment = fulfillmentForOrder(orderId);
			fulfillment.retryPurchase();
		});
	}

	public void reconcile(UUID orderId) {
		try {
			PurchaseContext context = readContext(orderId);
			if (context.purchaseStatus() != SupplierPurchaseStatus.RECONCILIATION_REQUIRED
				&& context.purchaseStatus() != SupplierPurchaseStatus.PROCESSING) {
				throw new IllegalStateException("Only uncertain supplier purchases can be reconciled");
			}
			List<DomeggookPurchaseClient.PurchaseListItem> matches = client.recentOrders().stream()
				.filter(item -> context.recipientName().equals(item.recipientName()))
				.filter(item -> context.lines().stream().anyMatch(line -> line.itemNo().equals(item.itemNo())))
				.toList();
			Set<String> requiredItems = new HashSet<>();
			context.lines().forEach(line -> requiredItems.add(line.itemNo()));
			Set<String> matchedItems = new HashSet<>();
			matches.forEach(item -> matchedItems.add(item.itemNo()));
			if (!matchedItems.equals(requiredItems) || matches.size() != requiredItems.size()) {
				throw new IllegalStateException("Domeggook order reconciliation is ambiguous; verify it manually");
			}
			List<String> orderNumbers = matches.stream().map(DomeggookPurchaseClient.PurchaseListItem::orderNumber).toList();
			long actualAmount = orderNumbers.stream().map(client::orderView).mapToLong(DomeggookPurchaseClient.OrderView::paidAmount).sum();
			if (actualAmount <= 0 || actualAmount > context.expectedAmount()) {
				throw new IllegalStateException("Domeggook reconciled amount is outside the validated maximum");
			}
			UUID attemptId = beginAttempt(context, "RECONCILE", context.expectedAmount());
			completeOrder(context, attemptId, new DomeggookPurchaseClient.OrderResult(orderNumbers, actualAmount));
		} catch (DomeggookApiException exception) {
			throw new IllegalStateException(exception.getMessage(), exception);
		}
	}

	public void cancel(UUID orderId, String reason) {
		PurchaseContext context = readContext(orderId);
		if (context.purchaseStatus() != SupplierPurchaseStatus.ORDERED || context.supplierOrderNumbers().isEmpty()) {
			throw new IllegalStateException("Only completed supplier purchases can be cancelled");
		}
		UUID attemptId = beginAttempt(context, "CANCEL", context.actualAmount());
		try {
			List<String> results = context.supplierOrderNumbers().stream()
				.map(orderNumber -> client.cancel(orderNumber, reason))
				.toList();
			transactionTemplate.executeWithoutResult(status -> {
				Fulfillment fulfillment = fulfillmentRepository.findByIdForUpdate(context.fulfillmentId()).orElseThrow();
				Instant now = Instant.now();
				if (results.stream().allMatch("complete"::equals)) {
					fulfillment.markSupplierCancelled("complete", now);
				} else {
					fulfillment.markSupplierCancelRequested(String.join(",", results), now);
				}
				attemptRepository.findById(attemptId).orElseThrow()
					.succeed(String.join(",", context.supplierOrderNumbers()), context.actualAmount(), now);
			});
		} catch (DomeggookApiException exception) {
			failCancel(context.fulfillmentId(), attemptId, exception);
			throw new IllegalStateException(exception.getMessage(), exception);
		}
	}

	public void sync(UUID fulfillmentId) {
		if (!properties.enabled()) return;
		PurchaseContext context = readContextByFulfillment(fulfillmentId);
		if ((context.purchaseStatus() != SupplierPurchaseStatus.ORDERED
			&& context.purchaseStatus() != SupplierPurchaseStatus.CANCEL_REQUESTED)
			|| context.supplierOrderNumbers().isEmpty()) return;
		List<DomeggookPurchaseClient.OrderView> views = context.supplierOrderNumbers().stream().map(client::orderView).toList();
		Set<String> tracking = new HashSet<>();
		for (DomeggookPurchaseClient.OrderView view : views) {
			if (!view.trackingNumber().isBlank()) tracking.add(view.carrier() + "|" + view.trackingNumber());
		}
		transactionTemplate.executeWithoutResult(status -> {
			Fulfillment fulfillment = fulfillmentRepository.findByIdForUpdate(context.fulfillmentId()).orElseThrow();
			Instant now = Instant.now();
			fulfillment.markPurchaseSynced(now);
			if (context.purchaseStatus() == SupplierPurchaseStatus.CANCEL_REQUESTED
				&& views.stream().allMatch(view -> view.status().contains("구매취소"))) {
				fulfillment.updateActualSourceAmount(views.stream().mapToLong(DomeggookPurchaseClient.OrderView::paidAmount).sum());
				fulfillment.markSupplierCancelled("complete", now);
				return;
			}
			if (tracking.size() != 1) return;
			String[] carrierTracking = tracking.iterator().next().split("\\|", 2);
			CustomerOrder order = fulfillment.getOrder();
			Shipment shipment = shipmentRepository.findByOrder_Id(order.getId()).orElse(null);
			if (shipment == null) {
				OrderStatus before = order.getStatus();
				order.markShipped();
				shipment = shipmentRepository.save(new Shipment(order, carrierTracking[0], carrierTracking[1], Instant.now()));
				statusHistoryRepository.save(new OrderStatusHistory(
					order,
					order.getAddressLockedByAdminId(),
					AdminOrderActionType.SHIPMENT_STARTED.name(),
					before,
					order.getStatus(),
					"ALLOWED",
					"Domeggook tracking synchronized",
					"Domeggook tracking synchronized"
				));
				notificationService.transactionalSms(order.getUser(), order, order.getPaymentGroup(), null, null, NotificationType.SHIPMENT_STARTED);
			}
			boolean delivered = views.stream().allMatch(view -> view.status().contains("배송완료"));
			if (delivered && shipment.markDeliveredByTracking(Instant.now())) {
				order.markDeliveredByTracking();
			}
		});
	}

	List<UUID> readyFulfillmentIds() {
		return fulfillmentRepository.findTop20ByPurchaseStatusOrderByCreatedAtAsc(SupplierPurchaseStatus.READY)
			.stream().map(Fulfillment::getId).toList();
	}

	List<UUID> processingFulfillmentIds() {
		return fulfillmentRepository.findTop20ByPurchaseStatusOrderByCreatedAtAsc(SupplierPurchaseStatus.PROCESSING)
			.stream().map(Fulfillment::getId).toList();
	}

	List<UUID> orderedFulfillmentIds() {
		return fulfillmentRepository.findTop20ByPurchaseStatusOrderByCreatedAtAsc(SupplierPurchaseStatus.ORDERED)
			.stream().map(Fulfillment::getId).toList();
	}

	List<UUID> cancelRequestedFulfillmentIds() {
		return fulfillmentRepository.findTop20ByPurchaseStatusOrderByCreatedAtAsc(SupplierPurchaseStatus.CANCEL_REQUESTED)
			.stream().map(Fulfillment::getId).toList();
	}

	UUID orderId(UUID fulfillmentId) {
		return fulfillmentRepository.findById(fulfillmentId)
			.orElseThrow(() -> new IllegalStateException("Fulfillment not found"))
			.getOrder()
			.getId();
	}

	private PurchaseContext claim(UUID fulfillmentId) {
		return transactionTemplate.execute(status -> {
			Fulfillment fulfillment = fulfillmentRepository.findByIdForUpdate(fulfillmentId).orElse(null);
			if (fulfillment == null || fulfillment.getPurchaseStatus() != SupplierPurchaseStatus.READY) return null;
			fulfillment.markPurchaseProcessing();
			return context(fulfillment);
		});
	}

	private UUID beginAttempt(PurchaseContext context, String action, Long expectedAmount) {
		return transactionTemplate.execute(status -> {
			Fulfillment fulfillment = fulfillmentRepository.findByIdForUpdate(context.fulfillmentId()).orElseThrow();
			fulfillment.updateExpectedSourceAmount(expectedAmount == null ? 0 : expectedAmount);
			return attemptRepository.save(new SupplierPurchaseAttempt(
				fulfillment,
				action,
				context.fingerprint(),
				expectedAmount
			)).getId();
		});
	}

	private void completeOrder(PurchaseContext context, UUID attemptId, DomeggookPurchaseClient.OrderResult result) {
		transactionTemplate.executeWithoutResult(status -> {
			Fulfillment fulfillment = fulfillmentRepository.findByIdForUpdate(context.fulfillmentId()).orElseThrow();
			CustomerOrder order = fulfillment.getOrder();
			OrderStatus before = order.getStatus();
			Instant now = Instant.now();
			order.markSupplierOrdered();
			String orderNumbers = String.join(",", result.orderNumbers());
			fulfillment.markPurchaseOrdered(
				orderNumbers,
				result.actualAmount(),
				addressSnapshot(context),
				order.getAddressLockedByAdminId(),
				now
			);
			attemptRepository.findById(attemptId).orElseThrow().succeed(orderNumbers, result.actualAmount(), now);
			actionHistoryRepository.save(new AdminOrderActionHistory(
				order,
				order.getAddressLockedByAdminId(),
				AdminOrderActionType.SUPPLIER_ORDER_AUTOMATED,
				before,
				order.getStatus(),
				"Domeggook order " + orderNumbers
			));
			statusHistoryRepository.save(new OrderStatusHistory(
				order,
				order.getAddressLockedByAdminId(),
				AdminOrderActionType.SUPPLIER_ORDER_AUTOMATED.name(),
				before,
				order.getStatus(),
				"ALLOWED",
				"Domeggook order completed",
				"Domeggook order " + orderNumbers
			));
		});
	}

	private void fail(UUID fulfillmentId, UUID attemptId, DomeggookApiException exception) {
		transactionTemplate.executeWithoutResult(status -> {
			Fulfillment fulfillment = fulfillmentRepository.findByIdForUpdate(fulfillmentId).orElseThrow();
			if (exception.outcomeUnknown()) {
				fulfillment.markPurchaseReconciliationRequired(exception.getMessage());
			} else {
				fulfillment.markPurchaseFailed(exception.getMessage());
			}
			if (attemptId != null) {
				SupplierPurchaseAttempt attempt = attemptRepository.findById(attemptId).orElseThrow();
				if (exception.outcomeUnknown()) {
					attempt.markUnknown(exception.getMessage(), Instant.now());
				} else {
					attempt.fail(exception.code(), exception.getMessage(), Instant.now());
				}
			}
		});
	}

	private void failCancel(UUID fulfillmentId, UUID attemptId, DomeggookApiException exception) {
		transactionTemplate.executeWithoutResult(status -> {
			Fulfillment fulfillment = fulfillmentRepository.findByIdForUpdate(fulfillmentId).orElseThrow();
			fulfillment.markSupplierCancelFailed(exception.getMessage(), Instant.now());
			attemptRepository.findById(attemptId).orElseThrow()
				.fail(exception.code(), exception.getMessage(), Instant.now());
		});
	}

	private ValidationResult validate(PurchaseContext context) {
		long itemAmount = 0;
		long shippingAmount = 0;
		Set<String> shippingItems = new HashSet<>();
		for (PurchaseLine line : context.lines()) {
			DomeggookPurchaseClient.ProductQuote quote = client.quote(line.itemNo(), line.optionCode());
			if (!quote.onSale()) throw new DomeggookApiException("ITEM_NOT_ON_SALE", "Supplier item is not on sale", false);
			if (!quote.optionAvailable()) throw new DomeggookApiException("OPTION_UNAVAILABLE", "Supplier option is unavailable", false);
			if (!quote.acceptsOrderQuantity(line.quantity())) {
				throw new DomeggookApiException("INVALID_ORDER_QUANTITY", "Supplier order quantity is invalid", false);
			}
			if (!quote.hasStock(line.quantity())) {
				throw new DomeggookApiException("STOCK_INSUFFICIENT", "Supplier stock is insufficient", false);
			}
			if (quote.conditionalShipping()) {
				throw new DomeggookApiException("SHIPPING_CONDITIONAL", "Conditional supplier shipping is not supported", false);
			}
			if (quote.sourceUnitPrice() != line.sourceUnitPrice()) {
				throw new DomeggookApiException("PRICE_MISMATCH", "Supplier price changed after checkout", false);
			}
			itemAmount += quote.sourceUnitPrice() * line.quantity();
			if (shippingItems.add(line.itemNo())) shippingAmount += quote.shippingFee();
		}
		return new ValidationResult(itemAmount + shippingAmount, itemAmount, shippingAmount);
	}

	private PurchaseContext readContext(UUID orderId) {
		return transactionTemplate.execute(status -> context(fulfillmentForOrder(orderId)));
	}

	private PurchaseContext readContextByFulfillment(UUID fulfillmentId) {
		return transactionTemplate.execute(status -> context(fulfillmentRepository.findById(fulfillmentId)
			.orElseThrow(() -> new IllegalStateException("Fulfillment not found"))));
	}

	private Fulfillment fulfillmentForOrder(UUID orderId) {
		orderRepository.findById(orderId).orElseThrow(() -> new IllegalStateException("Order not found"));
		return fulfillmentRepository.findByOrder_Id(orderId)
			.orElseThrow(() -> new IllegalStateException("Supplier purchase is not queued for this order"));
	}

	private PurchaseContext context(Fulfillment fulfillment) {
		CustomerOrder order = fulfillment.getOrder();
		List<PurchaseLine> lines = orderItemRepository.findAllByOrder_IdOrderByCreatedAtAsc(order.getId())
			.stream()
			.map(item -> new PurchaseLine(
				item.getSourceItemNo(),
				item.getSourceOptionCode(),
				item.getSourceUnitPrice() == null ? 0 : item.getSourceUnitPrice(),
				item.getQuantity()
			))
			.toList();
		return new PurchaseContext(
			fulfillment.getId(),
			order.getId(),
			order.getOrderNumber(),
			order.getRecipientName(),
			order.getRecipientPhone(),
			order.getPostalCode(),
			order.getAddress1(),
			order.getAddress2(),
			order.getUser().getEmail(),
			fulfillment.getPurchaseStatus(),
			fulfillment.getExpectedSourceAmount() == null ? snapshotAmount(lines) : fulfillment.getExpectedSourceAmount(),
			fulfillment.getActualSourceAmount(),
			fulfillment.getRequestFingerprint(),
			supplierOrderNumbers(fulfillment.getSupplierOrderNumber()),
			lines
		);
	}

	private DomeggookPurchaseClient.OrderRequest orderRequest(PurchaseContext context) {
		return new DomeggookPurchaseClient.OrderRequest(
			context.orderNumber(),
			context.recipientName(),
			context.recipientPhone(),
			context.postalCode(),
			context.address1(),
			context.address2(),
			context.email(),
			context.lines().stream()
				.map(line -> new DomeggookPurchaseClient.OrderLine(line.itemNo(), line.optionCode(), line.quantity()))
				.toList()
		);
	}

	private boolean isDomeggookOrder(List<OrderItem> items) {
		return !items.isEmpty() && items.stream().allMatch(item ->
			item.getSourceItemNo() != null
				&& !item.getSourceItemNo().isBlank()
				&& item.getSourceUnitPrice() != null
				&& item.getSourceUnitPrice() > 0
		);
	}

	private long snapshotAmount(List<OrderItem> items) {
		return items.stream().mapToLong(item -> item.getSourceUnitPrice() * item.getQuantity()).sum();
	}

	private long snapshotAmount(Iterable<PurchaseLine> lines) {
		long total = 0;
		for (PurchaseLine line : lines) total += line.sourceUnitPrice() * line.quantity();
		return total;
	}

	private String fingerprint(CustomerOrder order, List<OrderItem> items) {
		List<String> values = new ArrayList<>();
		values.add(order.getId().toString());
		values.add(order.getRecipientName());
		values.add(order.getRecipientPhone());
		values.add(order.getPostalCode());
		values.add(order.getAddress1());
		values.add(order.getAddress2() == null ? "" : order.getAddress2());
		for (OrderItem item : items) {
			values.add("%s:%s:%s:%s".formatted(
				item.getSourceItemNo(),
				item.getSourceOptionCode(),
				item.getSourceUnitPrice(),
				item.getQuantity()
			));
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(String.join("|", values).getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to fingerprint supplier order");
		}
	}

	private List<String> supplierOrderNumbers(String value) {
		if (value == null || value.isBlank()) return List.of();
		return List.of(value.split(","));
	}

	private String addressSnapshot(PurchaseContext context) {
		return """
			recipientName: %s
			recipientPhone: %s
			postalCode: %s
			address1: %s
			address2: %s
			""".formatted(
			context.recipientName(),
			context.recipientPhone(),
			context.postalCode(),
			context.address1(),
			context.address2() == null ? "" : context.address2()
		);
	}

	public record ValidationResult(long expectedAmount, long itemAmount, long shippingAmount) {
	}

	private record PurchaseContext(
		UUID fulfillmentId,
		UUID orderId,
		String orderNumber,
		String recipientName,
		String recipientPhone,
		String postalCode,
		String address1,
		String address2,
		String email,
		SupplierPurchaseStatus purchaseStatus,
		long expectedAmount,
		Long actualAmount,
		String fingerprint,
		List<String> supplierOrderNumbers,
		List<PurchaseLine> lines
	) {
	}

	private record PurchaseLine(String itemNo, String optionCode, long sourceUnitPrice, int quantity) {
	}
}
