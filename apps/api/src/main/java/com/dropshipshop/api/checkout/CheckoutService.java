package com.dropshipshop.api.checkout;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.account.AccountAgreementService;
import com.dropshipshop.api.account.AccountProfileService;
import com.dropshipshop.api.cart.domain.Cart;
import com.dropshipshop.api.cart.domain.CartItem;
import com.dropshipshop.api.cart.repository.CartItemRepository;
import com.dropshipshop.api.cart.repository.CartRepository;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductNoticeStatus;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.StorefrontSalesProperties;
import com.dropshipshop.api.common.money.MoneyMath;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.OrderPolicyAgreement;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.payment.domain.PaymentGroupStatus;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.order.repository.OrderPolicyAgreementRepository;
import com.dropshipshop.api.payment.BankTransferProperties;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.policy.CustomerPolicyLinkService;
import com.dropshipshop.api.refund.domain.Refund;
import com.dropshipshop.api.refund.domain.RefundReason;
import com.dropshipshop.api.refund.domain.RefundStatus;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.supplierproduct.ProductSaleability;
import com.dropshipshop.api.supplierportal.SupplierContractTerminalService;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.persistence.EntityManager;

@Service
public class CheckoutService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductNoticeRepository productNoticeRepository;
	private final ProductRepository productRepository;
	private final ProductOptionRepository productOptionRepository;
	private final SupplierRepository supplierRepository;
	private final PaymentGroupRepository paymentGroupRepository;
	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final OrderPolicyAgreementRepository orderPolicyAgreementRepository;
	private final RefundRepository refundRepository;
	private final UserAccountRepository userAccountRepository;
	private final AccountAgreementService accountAgreementService;
	private final AccountProfileService accountProfileService;
	private final CustomerPolicyLinkService customerPolicyLinkService;
	private final CheckoutPolicyProperties checkoutPolicyProperties;
	private final BankTransferProperties bankTransferProperties;
	private final NotificationService notificationService;
	private final StorefrontSalesProperties salesProperties;
	private final ProductSaleability productSaleability;
	private final SupplierContractTerminalService contractTerminalService;
	private final EntityManager entityManager;
	private final Clock clock;

	public CheckoutService(
		CartRepository cartRepository,
		CartItemRepository cartItemRepository,
		ProductNoticeRepository productNoticeRepository,
		ProductRepository productRepository,
		ProductOptionRepository productOptionRepository,
		SupplierRepository supplierRepository,
		PaymentGroupRepository paymentGroupRepository,
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		OrderPolicyAgreementRepository orderPolicyAgreementRepository,
		RefundRepository refundRepository,
		UserAccountRepository userAccountRepository,
		AccountAgreementService accountAgreementService,
		AccountProfileService accountProfileService,
		CustomerPolicyLinkService customerPolicyLinkService,
		CheckoutPolicyProperties checkoutPolicyProperties,
		BankTransferProperties bankTransferProperties,
		NotificationService notificationService,
		StorefrontSalesProperties salesProperties,
		ProductSaleability productSaleability,
		SupplierContractTerminalService contractTerminalService,
		EntityManager entityManager
	) {
		this.cartRepository = cartRepository;
		this.cartItemRepository = cartItemRepository;
		this.productNoticeRepository = productNoticeRepository;
		this.productRepository = productRepository;
		this.productOptionRepository = productOptionRepository;
		this.supplierRepository = supplierRepository;
		this.paymentGroupRepository = paymentGroupRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.orderPolicyAgreementRepository = orderPolicyAgreementRepository;
		this.refundRepository = refundRepository;
		this.userAccountRepository = userAccountRepository;
		this.accountAgreementService = accountAgreementService;
		this.accountProfileService = accountProfileService;
		this.customerPolicyLinkService = customerPolicyLinkService;
		this.checkoutPolicyProperties = checkoutPolicyProperties;
		this.bankTransferProperties = bankTransferProperties;
		this.notificationService = notificationService;
		this.salesProperties = salesProperties;
		this.productSaleability = productSaleability;
		this.contractTerminalService = contractTerminalService;
		this.entityManager = entityManager;
		this.clock = Clock.systemUTC();
	}

	@Transactional(noRollbackFor = SupplierContractExpiredCheckoutException.class)
	public CheckoutDtos.CheckoutResponse createCheckout(UUID userId, CheckoutDtos.CreateCheckoutRequest request) {
		salesProperties.requireEnabled();
		UserAccount user = findUser(userId);
		accountAgreementService.requireCurrentAgreement(userId);
		accountProfileService.requireRequiredInfo(userId);
		Cart cart = cartRepository.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty"));
		List<CartItem> cartItems = cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(cart.getId());
		if (cartItems.isEmpty()) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Checkout was already submitted for this cart. Please check your checkout or cart."
			);
		}
		LockedCatalog lockedCatalog = lockCatalogAndReloadCart(cart.getId(), cartItems);
		cartItems = lockedCatalog.cartItems();
		boolean contractExpired = false;
		Instant lockTime = Instant.now(clock);
		for (Supplier supplier : lockedCatalog.portalSuppliers()) {
			contractExpired |= contractTerminalService.expireIfOverdue(supplier, null, null, lockTime);
		}
		if (contractExpired) {
			throw new SupplierContractExpiredCheckoutException();
		}
		user = findUser(userId);
		validateSellability(cartItems);

		ShippingAddressSnapshot address = new ShippingAddressSnapshot(
			request.recipientName(),
			request.recipientPhone(),
			request.postalCode(),
			request.address1(),
			request.address2(),
			request.deliveryMemo()
		);
		Instant checkoutAt = Instant.now(clock);
		Instant expiresAt = checkoutAt.plus(bankTransferProperties.depositDeadline());
		long totalAmount = sumLineAmounts(cartItems);
		reserveInventory(cartItems);
		PaymentGroup paymentGroup = paymentGroupRepository.save(new PaymentGroup(
			nextCheckoutNumber(),
			user,
			totalAmount,
			expiresAt
		));
		paymentGroup.configureBankTransfer(
			bankTransferProperties.bankName(),
			bankTransferProperties.accountNumber(),
			bankTransferProperties.accountHolder(),
			depositorName(request),
			bankTransferProperties.cashReceiptNotice()
		);

		List<CustomerOrder> createdOrders = new ArrayList<>();
		for (List<CartItem> supplierItems : groupBySupplier(cartItems).values()) {
			CustomerOrder order = orderRepository.save(new CustomerOrder(
				nextOrderNumber(),
				user,
				supplierItems.getFirst().getProduct().getSupplier(),
				paymentGroup,
				address,
				sumLineAmounts(supplierItems),
				expiresAt
			));
			createdOrders.add(order);
			orderItemRepository.saveAll(snapshotItems(order, supplierItems, checkoutAt));
		}

		cartItemRepository.deleteAll(cartItems);
		if (!createdOrders.isEmpty()) {
			notificationService.paymentPending(user, createdOrders.getFirst(), paymentGroup);
		}
		return toCheckoutResponse(paymentGroup);
	}

	@Transactional(readOnly = true)
	public CheckoutDtos.CheckoutResponse getCheckout(UUID userId, String checkoutNumber) {
		return toCheckoutResponse(findPaymentGroup(userId, checkoutNumber));
	}

	@Transactional
	public CheckoutDtos.CheckoutResponse updateShippingAddress(
		UUID userId,
		String checkoutNumber,
		CheckoutDtos.UpdateShippingAddressRequest request
	) {
		PaymentGroup paymentGroup = findPaymentGroup(userId, checkoutNumber);
		if (paymentGroup.getStatus() != PaymentGroupStatus.PAYMENT_PENDING) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout address can be changed only before payment confirmation");
		}
		if (paymentGroup.getPolicyConfirmedAt() != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout policy confirmation is already completed");
		}
		ShippingAddressSnapshot address = new ShippingAddressSnapshot(
			request.recipientName(),
			request.recipientPhone(),
			request.postalCode(),
			request.address1(),
			request.address2(),
			request.deliveryMemo()
		);
		List<CustomerOrder> orders = orderRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(paymentGroup.getId());
		if (orders.isEmpty() || orders.stream().anyMatch(order -> order.getStatus() != OrderStatus.PAYMENT_PENDING)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout address can be changed only while payment is pending");
		}
		orders.forEach(order -> order.updatePaymentPendingAddress(address));
		return toCheckoutResponse(paymentGroup);
	}

	@Transactional
	public CheckoutDtos.PolicyConfirmationResponse confirmPolicy(
		UUID userId,
		String checkoutNumber,
		CheckoutDtos.PolicyConfirmationRequest request
	) {
		PaymentGroup paymentGroup = findPaymentGroup(userId, checkoutNumber);
		if (paymentGroup.getStatus() != PaymentGroupStatus.PAYMENT_PENDING) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout policy can be confirmed only while payment is pending");
		}
		if (paymentGroup.getPolicyConfirmedAt() != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Policy already confirmed");
		}
		accountAgreementService.requireCurrentAgreement(userId);
		validatePolicyVersions(request);
		List<CustomerOrder> orders = orderRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(paymentGroup.getId());
		if (orders.isEmpty() || orders.stream().anyMatch(order -> order.getStatus() != OrderStatus.PAYMENT_PENDING)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout policy can be confirmed only while payment is pending");
		}
		shippingAddress(orders);
		Instant confirmedAt = Instant.now(clock);
		orderPolicyAgreementRepository.save(new OrderPolicyAgreement(
			paymentGroup,
			paymentGroup.getUser(),
			accountAgreementService.requiredTermsVersion(),
			accountAgreementService.requiredPrivacyVersion(),
			checkoutPolicyProperties.orderPolicyVersion(),
			checkoutPolicyProperties.cancellationRefundPolicyVersion(),
			checkoutPolicyProperties.outOfStockNoticeVersion(),
			checkoutPolicyProperties.confirmedNoticeText(),
			confirmedAt
		));
		paymentGroup.confirmPolicy(confirmedAt);
		return new CheckoutDtos.PolicyConfirmationResponse(paymentGroup.getCheckoutNumber(), confirmedAt);
	}

	private UserAccount findUser(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
	}

	private PaymentGroup findPaymentGroup(UUID userId, String checkoutNumber) {
		return paymentGroupRepository.findByCheckoutNumberAndUser_Id(checkoutNumber, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout not found"));
	}

	private void validateSellability(List<CartItem> cartItems) {
		for (CartItem item : cartItems) {
			Product product = item.getProduct();
			ProductOption option = item.getProductOption();
			if (!productSaleability.isSellable(product, option, item.getQuantity())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart contains unavailable item");
			}
			if (!product.acceptsOrderQuantity(item.getQuantity())) {
				throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST,
					"장바구니 상품 수량이 현재 최소 주문 조건과 맞지 않습니다. 장바구니에서 수량을 변경해 주세요."
				);
			}
		}
	}

	private void reserveInventory(List<CartItem> cartItems) {
		for (CartItem item : cartItems) {
			ProductOption option = item.getProductOption();
			if (option.isTracked()) {
				option.reserve(item.getQuantity());
			}
		}
	}

	private LockedCatalog lockCatalogAndReloadCart(UUID cartId, List<CartItem> discoveredItems) {
		Set<UUID> supplierIds = new TreeSet<>();
		Set<UUID> portalSupplierIds = new TreeSet<>();
		Set<UUID> productIds = new TreeSet<>();
		Map<UUID, UUID> discoveredSupplierByProductId = new LinkedHashMap<>();
		for (CartItem item : discoveredItems) {
			UUID productId = item.getProduct().getId();
			UUID supplierId = item.getProduct().getSupplier().getId();
			supplierIds.add(supplierId);
			if (item.getProduct().getManagementChannel() == ProductManagementChannel.SUPPLIER_PORTAL) {
				portalSupplierIds.add(supplierId);
			}
			productIds.add(productId);
			discoveredSupplierByProductId.put(productId, supplierId);
		}
		entityManager.clear();
		Map<UUID, Supplier> lockedSuppliers = new LinkedHashMap<>();
		for (UUID supplierId : supplierIds) {
			Supplier supplier = supplierRepository.findByIdForUpdate(supplierId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart contains unavailable item"));
			lockedSuppliers.put(supplierId, supplier);
		}
		for (UUID productId : productIds) {
			Product product = productRepository.findByIdForUpdate(productId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart contains unavailable item"));
			if (!discoveredSupplierByProductId.get(productId).equals(product.getSupplier().getId())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart contains unavailable item");
			}
		}
		for (UUID productId : productIds) {
			productOptionRepository.findAllByProductIdForUpdate(productId);
		}
		return new LockedCatalog(
			cartItemRepository.findAllByCart_IdOrderByCreatedAtAsc(cartId),
			portalSupplierIds.stream().map(lockedSuppliers::get).toList()
		);
	}

	private Map<UUID, List<CartItem>> groupBySupplier(List<CartItem> cartItems) {
		Map<UUID, List<CartItem>> grouped = new LinkedHashMap<>();
		for (CartItem item : cartItems) {
			Supplier supplier = item.getProduct().getSupplier();
			grouped.computeIfAbsent(supplier.getId(), ignored -> new ArrayList<>()).add(item);
		}
		return grouped;
	}

	private record LockedCatalog(List<CartItem> cartItems, List<Supplier> portalSuppliers) {
	}

	private List<OrderItem> snapshotItems(CustomerOrder order, List<CartItem> cartItems, Instant reservationTime) {
		return cartItems.stream()
			.map(item -> new OrderItem(
				order,
				item.getProduct(),
				item.getProductOption(),
				activeNoticeVersion(item.getProduct().getId()),
				item.getQuantity(),
				reservationTime
			))
			.toList();
	}

	private Integer activeNoticeVersion(UUID productId) {
		return productNoticeRepository
			.findFirstByProduct_IdAndStatusOrderByVersionDesc(productId, ProductNoticeStatus.ACTIVE)
			.map(notice -> notice.getVersion())
			.orElse(null);
	}

	private long lineAmount(CartItem item) {
		long unitPrice = MoneyMath.requireCustomerUnitPrice(
			MoneyMath.addNonNegative(item.getProduct().getBasePrice(), item.getProductOption().getAdditionalPrice()),
			"unitPrice"
		);
		return MoneyMath.multiplyPositive(unitPrice, item.getQuantity());
	}

	private long sumLineAmounts(List<CartItem> items) {
		long total = 0;
		for (CartItem item : items) {
			total = MoneyMath.addPositive(total, lineAmount(item));
		}
		return total;
	}

	private CheckoutDtos.CheckoutResponse toCheckoutResponse(PaymentGroup paymentGroup) {
		List<CustomerOrder> customerOrders = orderRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(paymentGroup.getId());
		List<Refund> refunds = refundRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(paymentGroup.getId());
		CustomerRefundProjection checkoutRefund = customerRefundProjection(refunds);
		CheckoutDtos.ShippingAddressResponse shippingAddress = shippingAddress(customerOrders);
		List<CheckoutDtos.OrderResponse> orders = customerOrders
			.stream()
			.map(order -> toOrderResponse(order, refunds))
			.toList();
		return new CheckoutDtos.CheckoutResponse(
			paymentGroup.getId(),
			paymentGroup.getCheckoutNumber(),
			paymentGroup.getStatus(),
			paymentGroup.getTotalAmount(),
			paymentGroup.getRefundableAmount(),
			checkoutRefund.status(),
			checkoutRefund.label(),
			checkoutRefund.amount(),
			paymentGroup.getExpiresAt(),
			paymentGroup.getPolicyConfirmedAt(),
			new CheckoutDtos.BankTransferDepositResponse(
				paymentGroup.getBankTransferBankName(),
				paymentGroup.getBankTransferAccountNumber(),
				paymentGroup.getBankTransferAccountHolder(),
				paymentGroup.getBankTransferDepositorName(),
				paymentGroup.getTotalAmount(),
				paymentGroup.getExpiresAt(),
				paymentGroup.getBankTransferCashReceiptNotice()
			),
			shippingAddress,
			policyEvidence(),
			policyLinks(),
			orders
		);
	}

	private CheckoutDtos.ShippingAddressResponse shippingAddress(List<CustomerOrder> orders) {
		if (orders.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout has no orders");
		}
		CustomerOrder first = orders.getFirst();
		ShippingAddressSnapshot address = new ShippingAddressSnapshot(
			first.getRecipientName(),
			first.getRecipientPhone(),
			first.getPostalCode(),
			first.getAddress1(),
			first.getAddress2(),
			first.getDeliveryMemo()
		);
		boolean inconsistent = orders.stream().skip(1).anyMatch(order -> !address.equals(new ShippingAddressSnapshot(
			order.getRecipientName(),
			order.getRecipientPhone(),
			order.getPostalCode(),
			order.getAddress1(),
			order.getAddress2(),
			order.getDeliveryMemo()
		)));
		if (inconsistent) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout shipping address is inconsistent");
		}
		return new CheckoutDtos.ShippingAddressResponse(
			address.recipientName(),
			address.recipientPhone(),
			address.postalCode(),
			address.address1(),
			address.address2(),
			address.deliveryMemo()
		);
	}

	private CheckoutDtos.PolicyEvidenceResponse policyEvidence() {
		return new CheckoutDtos.PolicyEvidenceResponse(
			accountAgreementService.requiredTermsVersion(),
			accountAgreementService.requiredPrivacyVersion(),
			checkoutPolicyProperties.orderPolicyVersion(),
			checkoutPolicyProperties.cancellationRefundPolicyVersion(),
			checkoutPolicyProperties.outOfStockNoticeVersion(),
			checkoutPolicyProperties.confirmedNoticeText()
		);
	}

	private void validatePolicyVersions(CheckoutDtos.PolicyConfirmationRequest request) {
		if (!accountAgreementService.requiredTermsVersion().equals(request.termsVersion())
			|| !accountAgreementService.requiredPrivacyVersion().equals(request.privacyVersion())
			|| !checkoutPolicyProperties.orderPolicyVersion().equals(request.orderPolicyVersion())
			|| !checkoutPolicyProperties.cancellationRefundPolicyVersion().equals(request.cancellationRefundPolicyVersion())
			|| !checkoutPolicyProperties.outOfStockNoticeVersion().equals(request.outOfStockNoticeVersion())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout policy versions are not current");
		}
	}

	private String depositorName(CheckoutDtos.CreateCheckoutRequest request) {
		if (request.depositorName() == null || request.depositorName().isBlank()) {
			return request.recipientName();
		}
		return request.depositorName().trim();
	}

	private List<CheckoutDtos.PolicyLinkResponse> policyLinks() {
		return customerPolicyLinkService.links().stream()
			.map(link -> new CheckoutDtos.PolicyLinkResponse(link.label(), link.href(), link.policyType()))
			.toList();
	}

	private CheckoutDtos.OrderResponse toOrderResponse(CustomerOrder order, List<Refund> refunds) {
		List<Refund> applicableRefunds = refunds.stream()
			.filter(refund -> refund.getOrder() == null || refund.getOrder().getId().equals(order.getId()))
			.toList();
		CustomerRefundProjection refundProjection = customerRefundProjection(applicableRefunds);
		List<CheckoutDtos.OrderItemResponse> items = orderItemRepository
			.findAllByOrder_IdOrderByCreatedAtAsc(order.getId())
			.stream()
			.map(this::toOrderItemResponse)
			.toList();
		return new CheckoutDtos.OrderResponse(
			order.getId(),
			order.getOrderNumber(),
			order.getSupplier().getId(),
			"Delivery group",
			order.getStatus(),
			order.getSubtotalAmount(),
			order.getShippingFee(),
			order.getDiscountAmount(),
			order.getTotalAmount(),
			refundProjection.status(),
			refundProjection.label(),
			refundProjection.amount(),
			items
		);
	}

	private CustomerRefundProjection customerRefundProjection(List<Refund> refunds) {
		List<Refund> received = refunds.stream().filter(this::isReceivedPaymentException).toList();
		if (received.isEmpty()) {
			return CustomerRefundProjection.none();
		}
		long amount = received.stream().mapToLong(Refund::getRefundAmount).reduce(0, Math::addExact);
		boolean completed = received.stream().allMatch(refund -> refund.getStatus() == RefundStatus.COMPLETED);
		return new CustomerRefundProjection(
			completed ? "REFUNDED" : "REFUND_PROCESSING",
			completed ? "환불 완료" : "입금 확인 및 환불 처리 중",
			amount
		);
	}

	private boolean isReceivedPaymentException(Refund refund) {
		return refund.getReason() == RefundReason.PAYMENT_AMOUNT_MISMATCH
			|| refund.getReason() == RefundReason.LATE_DEPOSIT_EXCEPTION
			|| refund.getReason() == RefundReason.SALE_UNAVAILABLE_AT_DEPOSIT;
	}

	private record CustomerRefundProjection(String status, String label, Long amount) {
		static CustomerRefundProjection none() {
			return new CustomerRefundProjection(null, null, null);
		}
	}

	private CheckoutDtos.OrderItemResponse toOrderItemResponse(OrderItem item) {
		return new CheckoutDtos.OrderItemResponse(
			item.getId(),
			item.getProductName(),
			item.getOptionName(),
			item.getQuantity(),
			item.getUnitPrice(),
			item.getLineAmount(),
			item.getProductDetailVersion(),
			item.getProductNoticeVersion()
		);
	}

	private String nextCheckoutNumber() {
		String checkoutNumber;
		do {
			checkoutNumber = "CO" + randomNumber();
		} while (paymentGroupRepository.existsByCheckoutNumber(checkoutNumber));
		return checkoutNumber;
	}

	private String nextOrderNumber() {
		String orderNumber;
		do {
			orderNumber = "OD" + randomNumber();
		} while (orderRepository.existsByOrderNumber(orderNumber));
		return orderNumber;
	}

	private long randomNumber() {
		return 100_000_000_000L + Math.abs(RANDOM.nextLong() % 900_000_000_000L);
	}
}
