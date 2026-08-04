package com.dropshipshop.api.checkout;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.dropshipshop.api.catalog.domain.ProductNoticeStatus;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.common.StorefrontSalesProperties;
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
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@Service
public class CheckoutService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductNoticeRepository productNoticeRepository;
	private final PaymentGroupRepository paymentGroupRepository;
	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;
	private final OrderPolicyAgreementRepository orderPolicyAgreementRepository;
	private final UserAccountRepository userAccountRepository;
	private final AccountAgreementService accountAgreementService;
	private final AccountProfileService accountProfileService;
	private final CustomerPolicyLinkService customerPolicyLinkService;
	private final CheckoutPolicyProperties checkoutPolicyProperties;
	private final BankTransferProperties bankTransferProperties;
	private final NotificationService notificationService;
	private final StorefrontSalesProperties salesProperties;
	private final Clock clock;

	public CheckoutService(
		CartRepository cartRepository,
		CartItemRepository cartItemRepository,
		ProductNoticeRepository productNoticeRepository,
		PaymentGroupRepository paymentGroupRepository,
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository,
		OrderPolicyAgreementRepository orderPolicyAgreementRepository,
		UserAccountRepository userAccountRepository,
		AccountAgreementService accountAgreementService,
		AccountProfileService accountProfileService,
		CustomerPolicyLinkService customerPolicyLinkService,
		CheckoutPolicyProperties checkoutPolicyProperties,
		BankTransferProperties bankTransferProperties,
		NotificationService notificationService,
		StorefrontSalesProperties salesProperties
	) {
		this.cartRepository = cartRepository;
		this.cartItemRepository = cartItemRepository;
		this.productNoticeRepository = productNoticeRepository;
		this.paymentGroupRepository = paymentGroupRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
		this.orderPolicyAgreementRepository = orderPolicyAgreementRepository;
		this.userAccountRepository = userAccountRepository;
		this.accountAgreementService = accountAgreementService;
		this.accountProfileService = accountProfileService;
		this.customerPolicyLinkService = customerPolicyLinkService;
		this.checkoutPolicyProperties = checkoutPolicyProperties;
		this.bankTransferProperties = bankTransferProperties;
		this.notificationService = notificationService;
		this.salesProperties = salesProperties;
		this.clock = Clock.systemUTC();
	}

	@Transactional
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
		validateSellability(cartItems);

		ShippingAddressSnapshot address = new ShippingAddressSnapshot(
			request.recipientName(),
			request.recipientPhone(),
			request.postalCode(),
			request.address1(),
			request.address2()
		);
		Instant expiresAt = Instant.now(clock).plus(bankTransferProperties.depositDeadline());
		long totalAmount = cartItems.stream().mapToLong(this::lineAmount).sum();
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
				supplierItems.stream().mapToLong(this::lineAmount).sum(),
				expiresAt
			));
			createdOrders.add(order);
			orderItemRepository.saveAll(snapshotItems(order, supplierItems));
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
			request.address2()
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
			if (product.getStatus() != ProductStatus.ACTIVE || option.getStatus() != ProductOptionStatus.ACTIVE) {
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

	private Map<UUID, List<CartItem>> groupBySupplier(List<CartItem> cartItems) {
		Map<UUID, List<CartItem>> grouped = new LinkedHashMap<>();
		for (CartItem item : cartItems) {
			Supplier supplier = item.getProduct().getSupplier();
			grouped.computeIfAbsent(supplier.getId(), ignored -> new ArrayList<>()).add(item);
		}
		return grouped;
	}

	private List<OrderItem> snapshotItems(CustomerOrder order, List<CartItem> cartItems) {
		return cartItems.stream()
			.map(item -> new OrderItem(
				order,
				item.getProduct(),
				item.getProductOption(),
				activeNoticeVersion(item.getProduct().getId()),
				item.getQuantity()
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
		return (item.getProduct().getBasePrice() + item.getProductOption().getAdditionalPrice()) * item.getQuantity();
	}

	private CheckoutDtos.CheckoutResponse toCheckoutResponse(PaymentGroup paymentGroup) {
		List<CustomerOrder> customerOrders = orderRepository.findAllByPaymentGroup_IdOrderByCreatedAtAsc(paymentGroup.getId());
		CheckoutDtos.ShippingAddressResponse shippingAddress = shippingAddress(customerOrders);
		List<CheckoutDtos.OrderResponse> orders = customerOrders
			.stream()
			.map(this::toOrderResponse)
			.toList();
		return new CheckoutDtos.CheckoutResponse(
			paymentGroup.getId(),
			paymentGroup.getCheckoutNumber(),
			paymentGroup.getStatus(),
			paymentGroup.getTotalAmount(),
			paymentGroup.getRefundableAmount(),
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
			first.getAddress2()
		);
		boolean inconsistent = orders.stream().skip(1).anyMatch(order -> !address.equals(new ShippingAddressSnapshot(
			order.getRecipientName(),
			order.getRecipientPhone(),
			order.getPostalCode(),
			order.getAddress1(),
			order.getAddress2()
		)));
		if (inconsistent) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout shipping address is inconsistent");
		}
		return new CheckoutDtos.ShippingAddressResponse(
			address.recipientName(),
			address.recipientPhone(),
			address.postalCode(),
			address.address1(),
			address.address2()
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

	private CheckoutDtos.OrderResponse toOrderResponse(CustomerOrder order) {
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
			items
		);
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
