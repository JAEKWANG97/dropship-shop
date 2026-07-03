package com.dropshipshop.api.dev;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductNoticeStatus;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.order.domain.AdminOrderActionHistory;
import com.dropshipshop.api.order.domain.AdminOrderActionType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.OrderStatusHistory;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.AdminOrderActionHistoryRepository;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.order.repository.OrderStatusHistoryRepository;
import com.dropshipshop.api.payment.domain.Payment;
import com.dropshipshop.api.payment.domain.PaymentEvent;
import com.dropshipshop.api.payment.domain.PaymentEventType;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentEventRepository;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.payment.repository.PaymentRepository;
import com.dropshipshop.api.refund.RefundService;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@Order(2)
public class LocalOrderSeedData implements ApplicationRunner {

	private static final String PREFIX = "LOCAL-B003-";
	private static final String CUSTOMER_PROVIDER_ID = "local-b003-customer";
	private static final String ADMIN_PROVIDER_ID = "local-b003-admin";

	private final UserAccountRepository userAccountRepository;
	private final ProductRepository productRepository;
	private final ProductOptionRepository productOptionRepository;
	private final ProductNoticeRepository productNoticeRepository;
	private final PaymentGroupRepository paymentGroupRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentEventRepository paymentEventRepository;
	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final FulfillmentRepository fulfillmentRepository;
	private final ShipmentRepository shipmentRepository;
	private final AdminOrderActionHistoryRepository actionHistoryRepository;
	private final OrderStatusHistoryRepository statusHistoryRepository;
	private final RefundService refundService;

	public LocalOrderSeedData(
		UserAccountRepository userAccountRepository,
		ProductRepository productRepository,
		ProductOptionRepository productOptionRepository,
		ProductNoticeRepository productNoticeRepository,
		PaymentGroupRepository paymentGroupRepository,
		PaymentRepository paymentRepository,
		PaymentEventRepository paymentEventRepository,
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		FulfillmentRepository fulfillmentRepository,
		ShipmentRepository shipmentRepository,
		AdminOrderActionHistoryRepository actionHistoryRepository,
		OrderStatusHistoryRepository statusHistoryRepository,
		RefundService refundService
	) {
		this.userAccountRepository = userAccountRepository;
		this.productRepository = productRepository;
		this.productOptionRepository = productOptionRepository;
		this.productNoticeRepository = productNoticeRepository;
		this.paymentGroupRepository = paymentGroupRepository;
		this.paymentRepository = paymentRepository;
		this.paymentEventRepository = paymentEventRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.fulfillmentRepository = fulfillmentRepository;
		this.shipmentRepository = shipmentRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.refundService = refundService;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		UserAccount customer = ensureUser(CUSTOMER_PROVIDER_ID, "local-customer@coreable.local", "로컬 주문 고객", UserRole.CUSTOMER);
		UserAccount admin = ensureUser(ADMIN_PROVIDER_ID, "local-admin@coreable.local", "로컬 관리자", UserRole.ADMIN);
		Product product = activeProduct();
		ProductOption option = activeOption(product);

		seedPaymentPending(customer, product, option);
		seedSupplierOrderPending(customer, admin, product, option);
		seedSupplierOrdered(customer, admin, product, option);
		seedShipped(customer, admin, product, option);
		seedDelivered(customer, admin, product, option);
		seedOutOfStock(customer, admin, product, option);
	}

	private UserAccount ensureUser(String providerUserId, String email, String displayName, UserRole role) {
		return userAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, providerUserId)
			.orElseGet(() -> userAccountRepository.save(new UserAccount(
				SocialProvider.GOOGLE,
				providerUserId,
				email,
				displayName,
				role
			)));
	}

	private Product activeProduct() {
		List<Product> products = productRepository.findAllByStatus(ProductStatus.ACTIVE);
		if (products.isEmpty()) {
			throw new IllegalStateException("Local order seed requires at least one active product");
		}
		return products.getFirst();
	}

	private ProductOption activeOption(Product product) {
		return productOptionRepository.findAllByProduct_IdOrderByCreatedAtAsc(product.getId())
			.stream()
			.filter(option -> option.getStatus() == ProductOptionStatus.ACTIVE)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Local order seed requires an active product option"));
	}

	private void seedPaymentPending(UserAccount customer, Product product, ProductOption option) {
		createBaseOrder("PAYMENT-PENDING", customer, product, option);
	}

	private void seedSupplierOrderPending(UserAccount customer, UserAccount admin, Product product, ProductOption option) {
		CustomerOrder order = createBaseOrder("SUPPLIER-PENDING", customer, product, option);
		if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
			confirmDeposit(order, admin, "로컬 시드 입금 확인");
		}
	}

	private void seedSupplierOrdered(UserAccount customer, UserAccount admin, Product product, ProductOption option) {
		CustomerOrder order = createBaseOrder("SUPPLIER-ORDERED", customer, product, option);
		if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
			confirmDeposit(order, admin, "로컬 시드 입금 확인");
			Fulfillment fulfillment = startSupplierWork(order, admin, "로컬 시드 발주 시작");
			completeSupplierOrder(order, admin, fulfillment, "LOCAL-SO-001", "로컬 시드 발주 완료");
		}
	}

	private void seedShipped(UserAccount customer, UserAccount admin, Product product, ProductOption option) {
		CustomerOrder order = createBaseOrder("SHIPPED", customer, product, option);
		if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
			confirmDeposit(order, admin, "로컬 시드 입금 확인");
			Fulfillment fulfillment = startSupplierWork(order, admin, "로컬 시드 발주 시작");
			completeSupplierOrder(order, admin, fulfillment, "LOCAL-SO-002", "로컬 시드 발주 완료");
			createShipment(order, admin, "로컬 시드 송장 입력");
		}
	}

	private void seedDelivered(UserAccount customer, UserAccount admin, Product product, ProductOption option) {
		CustomerOrder order = createBaseOrder("DELIVERED", customer, product, option);
		if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
			confirmDeposit(order, admin, "로컬 시드 입금 확인");
			Fulfillment fulfillment = startSupplierWork(order, admin, "로컬 시드 발주 시작");
			completeSupplierOrder(order, admin, fulfillment, "LOCAL-SO-003", "로컬 시드 발주 완료");
			Shipment shipment = createShipment(order, admin, "로컬 시드 송장 입력");
			markDelivered(order, admin, shipment, "로컬 시드 배송완료");
		}
	}

	private void seedOutOfStock(UserAccount customer, UserAccount admin, Product product, ProductOption option) {
		CustomerOrder order = createBaseOrder("OUT-OF-STOCK", customer, product, option);
		if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
			confirmDeposit(order, admin, "로컬 시드 입금 확인");
			markOutOfStock(order, admin, "로컬 시드 품절");
		}
	}

	private CustomerOrder createBaseOrder(String key, UserAccount customer, Product product, ProductOption option) {
		String orderNumber = PREFIX + key;
		return orderRepository.findAll().stream()
			.filter(order -> orderNumber.equals(order.getOrderNumber()))
			.findFirst()
			.orElseGet(() -> {
				Instant now = Instant.now();
				long lineAmount = product.getBasePrice() + option.getAdditionalPrice();
				PaymentGroup paymentGroup = paymentGroupRepository.save(new PaymentGroup(
					PREFIX + "CHECKOUT-" + key,
					customer,
					lineAmount,
					now.plus(Duration.ofDays(3))
				));
				paymentGroup.configureBankTransfer(
					"로컬 테스트 은행",
					"000-0000-0000",
					"가라사니",
					customer.getDisplayName(),
					"로컬 시드 계좌입금 주문"
				);
				paymentGroup.confirmPolicy(now);
				CustomerOrder order = orderRepository.save(new CustomerOrder(
					orderNumber,
					customer,
					product.getSupplier(),
					paymentGroup,
					new ShippingAddressSnapshot(
						"로컬 주문 고객",
						"010-0000-0000",
						"05800",
						"서울특별시 송파구 동남로11길 4",
						"103동 1405호"
					),
					lineAmount,
					paymentGroup.getExpiresAt()
				));
				orderItemRepository.save(new OrderItem(order, product, option, activeNoticeVersion(product), 1));
				return order;
			});
	}

	private Integer activeNoticeVersion(Product product) {
		return productNoticeRepository
			.findFirstByProduct_IdAndStatusOrderByVersionDesc(product.getId(), ProductNoticeStatus.ACTIVE)
			.map(notice -> notice.getVersion())
			.orElse(null);
	}

	private void confirmDeposit(CustomerOrder order, UserAccount admin, String reason) {
		OrderStatus beforeStatus = order.getStatus();
		Instant now = Instant.now();
		PaymentGroup paymentGroup = order.getPaymentGroup();
		paymentGroup.confirmBankTransferDeposit(admin.getId(), reason, now);
		Payment payment = paymentRepository.save(Payment.bankTransferApproved(
			paymentGroup,
			"BANK-" + paymentGroup.getCheckoutNumber(),
			paymentGroup.getTotalAmount(),
			now
		));
		order.confirmBankTransferDeposit();
		recordHistory(order, admin, AdminOrderActionType.BANK_TRANSFER_DEPOSIT_CONFIRMED, beforeStatus, "Bank transfer deposit confirmed", reason);
		paymentEventRepository.save(new PaymentEvent(
			payment,
			paymentGroup,
			payment.getProviderPaymentKey(),
			PaymentEventType.BANK_TRANSFER_DEPOSIT_CONFIRMED,
			"Local seed bank transfer deposit confirmed",
			now
		));
	}

	private Fulfillment startSupplierWork(CustomerOrder order, UserAccount admin, String reason) {
		OrderStatus beforeStatus = order.getStatus();
		Instant now = Instant.now();
		order.startSupplierOrderWork(admin.getId(), now);
		Fulfillment fulfillment = new Fulfillment(order);
		fulfillment.startWork(now);
		fulfillmentRepository.save(fulfillment);
		recordHistory(order, admin, AdminOrderActionType.SUPPLIER_WORK_START, beforeStatus, "Local seed supplier work started", reason);
		return fulfillment;
	}

	private void completeSupplierOrder(CustomerOrder order, UserAccount admin, Fulfillment fulfillment, String supplierOrderNumber, String reason) {
		OrderStatus beforeStatus = order.getStatus();
		order.markSupplierOrdered();
		fulfillment.markOrdered(
			supplierOrderNumber,
			"로컬 시드 배송지 스냅샷",
			admin.getId(),
			LocalDate.now().plusDays(1),
			"로컬 시드 공급처 응답",
			Instant.now()
		);
		recordHistory(order, admin, AdminOrderActionType.SUPPLIER_ORDER_COMPLETED, beforeStatus, "Local seed supplier order completed", reason);
	}

	private Shipment createShipment(CustomerOrder order, UserAccount admin, String reason) {
		OrderStatus beforeStatus = order.getStatus();
		order.markShipped();
		Shipment shipment = shipmentRepository.save(new Shipment(order, "로컬택배", "LOCAL-" + order.getOrderNumber(), Instant.now()));
		recordHistory(order, admin, AdminOrderActionType.SHIPMENT_STARTED, beforeStatus, "Local seed shipment entered", reason);
		return shipment;
	}

	private void markDelivered(CustomerOrder order, UserAccount admin, Shipment shipment, String reason) {
		OrderStatus beforeStatus = order.getStatus();
		Instant now = Instant.now();
		order.markDeliveredByTracking();
		shipment.markDeliveredByTracking(now);
		recordHistory(order, admin, AdminOrderActionType.SHIPMENT_MANUAL_CORRECTION, beforeStatus, "Local seed delivered order", reason);
	}

	private void markOutOfStock(CustomerOrder order, UserAccount admin, String reason) {
		OrderStatus beforeStatus = order.getStatus();
		order.markOutOfStock();
		Fulfillment fulfillment = new Fulfillment(order);
		fulfillment.markOutOfStock(reason);
		fulfillmentRepository.save(fulfillment);
		refundService.createOutOfStockRefund(order);
		recordHistory(order, admin, AdminOrderActionType.OUT_OF_STOCK, beforeStatus, "Local seed out of stock refund requested", reason);
	}

	private void recordHistory(
		CustomerOrder order,
		UserAccount admin,
		AdminOrderActionType actionType,
		OrderStatus beforeStatus,
		String sideEffectSummary,
		String reason
	) {
		actionHistoryRepository.save(new AdminOrderActionHistory(
			order,
			admin.getId(),
			actionType,
			beforeStatus,
			order.getStatus(),
			reason
		));
		if (beforeStatus != order.getStatus()) {
			statusHistoryRepository.save(new OrderStatusHistory(
				order,
				admin.getId(),
				actionType.name(),
				beforeStatus,
				order.getStatus(),
				"ALLOWED",
				sideEffectSummary,
				reason
			));
		}
	}
}
