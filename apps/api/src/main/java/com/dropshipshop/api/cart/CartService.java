package com.dropshipshop.api.cart;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.cart.domain.Cart;
import com.dropshipshop.api.cart.domain.CartItem;
import com.dropshipshop.api.cart.repository.CartItemRepository;
import com.dropshipshop.api.cart.repository.CartRepository;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.StorefrontSalesProperties;
import com.dropshipshop.api.common.money.MoneyMath;
import com.dropshipshop.api.supplierproduct.ProductSaleability;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@Service
public class CartService {

	private static final int MAX_QUANTITY = 99;

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductOptionRepository productOptionRepository;
	private final ProductRepository productRepository;
	private final SupplierRepository supplierRepository;
	private final UserAccountRepository userAccountRepository;
	private final StorefrontSalesProperties salesProperties;
	private final ProductSaleability productSaleability;

	public CartService(
		CartRepository cartRepository,
		CartItemRepository cartItemRepository,
		ProductOptionRepository productOptionRepository,
		ProductRepository productRepository,
		SupplierRepository supplierRepository,
		UserAccountRepository userAccountRepository,
		StorefrontSalesProperties salesProperties,
		ProductSaleability productSaleability
	) {
		this.cartRepository = cartRepository;
		this.cartItemRepository = cartItemRepository;
		this.productOptionRepository = productOptionRepository;
		this.productRepository = productRepository;
		this.supplierRepository = supplierRepository;
		this.userAccountRepository = userAccountRepository;
		this.salesProperties = salesProperties;
		this.productSaleability = productSaleability;
	}

	@Transactional
	public CartDtos.CartResponse getCart(UUID userId) {
		Cart cart = getOrCreateCart(userId);
		return toCartResponse(cart);
	}

	@Transactional
	public CartDtos.CartResponse addItem(UUID userId, CartDtos.AddCartItemRequest request) {
		salesProperties.requireEnabled();
		Cart cart = getOrCreateCartForUpdate(userId);
		ProductOptionRepository.OptionOwnership ownership = findOptionOwnership(request.productOptionId());
		UUID productId = ownership.getProductId();
		UUID supplierId = ownership.getSupplierId();
		supplierRepository.findByIdForUpdate(supplierId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product option not found"));
		Product product = productRepository.findByIdForUpdate(productId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product option not found"));
		if (!supplierId.equals(product.getSupplier().getId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product option is not sellable");
		}
		ProductOption option = productOptionRepository.findAllByProductIdForUpdate(productId).stream()
			.filter(current -> current.getId().equals(request.productOptionId()))
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product option not found"));
		CartItem item = cartItemRepository.findByCart_IdAndProductOption_Id(cart.getId(), option.getId())
			.orElseGet(() -> new CartItem(cart, product, option, 0));
		int nextQuantity = item.getQuantity() + request.quantity();
		if (nextQuantity > MAX_QUANTITY) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart item quantity cannot exceed 99");
		}
		requireOrderQuantity(product, nextQuantity);
		requireSellable(product, option, nextQuantity);
		item.updateQuantity(nextQuantity);
		cartItemRepository.save(item);
		return toCartResponse(cart);
	}

	@Transactional
	public CartDtos.CartResponse updateItem(UUID userId, UUID cartItemId, CartDtos.UpdateCartItemRequest request) {
		lockCart(userId);
		CartItem item = findUserCartItem(userId, cartItemId);
		requireOrderQuantity(item.getProduct(), request.quantity());
		requireSellable(item.getProduct(), item.getProductOption(), request.quantity());
		item.updateQuantity(request.quantity());
		return toCartResponse(item.getCart());
	}

	@Transactional
	public void removeItem(UUID userId, UUID cartItemId) {
		lockCart(userId);
		CartItem item = findUserCartItem(userId, cartItemId);
		cartItemRepository.delete(item);
	}

	@Transactional
	public CartDtos.CartValidationResponse validateCart(UUID userId) {
		Cart cart = getOrCreateCart(userId);
		List<CartDtos.CartValidationIssueResponse> issues = validationIssues(cart);
		return new CartDtos.CartValidationResponse(issues.isEmpty(), issues);
	}

	private Cart getOrCreateCart(UUID userId) {
		return cartRepository.findByUser_Id(userId)
			.orElseGet(() -> cartRepository.save(new Cart(findUser(userId))));
	}

	private Cart getOrCreateCartForUpdate(UUID userId) {
		return cartRepository.findByUserIdForUpdate(userId).orElseGet(() -> {
			UserAccount user = userAccountRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
			return cartRepository.findByUserIdForUpdate(userId)
				.orElseGet(() -> cartRepository.save(new Cart(user)));
		});
	}

	private void lockCart(UUID userId) {
		cartRepository.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found"));
	}

	private UserAccount findUser(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
	}

	private ProductOptionRepository.OptionOwnership findOptionOwnership(UUID productOptionId) {
		return productOptionRepository.findOwnershipById(productOptionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product option not found"));
	}

	private CartItem findUserCartItem(UUID userId, UUID cartItemId) {
		return cartItemRepository.findByIdAndCart_User_Id(cartItemId, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found"));
	}

	private void requireSellable(Product product, ProductOption option, int quantity) {
		if (!productSaleability.isSellable(product, option, quantity)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product option is not sellable");
		}
	}

	private CartDtos.CartResponse toCartResponse(Cart cart) {
		List<CartItem> items = cartItemRepository.findAllByCart_User_IdOrderByCreatedAtAsc(cart.getUser().getId());
		List<CartDtos.CartItemResponse> itemResponses = items.stream()
			.map(this::toItemResponse)
			.toList();
		List<CartDtos.CartValidationIssueResponse> issues = validationIssues(items);
		long subtotalAmount = 0;
		for (CartDtos.CartItemResponse item : itemResponses) {
			subtotalAmount = MoneyMath.addNonNegative(subtotalAmount, item.lineAmount());
		}
		return new CartDtos.CartResponse(
			cart.getId(),
			itemResponses,
			subtotalAmount,
			salesProperties.enabled(),
			salesProperties.enabled() ? null : salesProperties.closedNotice(),
			issues.isEmpty(),
			issues
		);
	}

	private List<CartDtos.CartValidationIssueResponse> validationIssues(Cart cart) {
		return validationIssues(cartItemRepository.findAllByCart_User_IdOrderByCreatedAtAsc(cart.getUser().getId()));
	}

	private List<CartDtos.CartValidationIssueResponse> validationIssues(List<CartItem> items) {
		List<CartDtos.CartValidationIssueResponse> issues = new ArrayList<>();
		if (items.isEmpty()) {
			issues.add(new CartDtos.CartValidationIssueResponse(null, "EMPTY_CART", "Cart is empty"));
			return issues;
		}
		if (!salesProperties.enabled()) {
			issues.add(new CartDtos.CartValidationIssueResponse(
				null,
				"SALES_NOT_OPEN",
				salesProperties.closedNotice()
			));
		}
		for (CartItem item : items) {
			String reason = unavailableReason(item);
			if (reason != null) {
				String code = productSaleability.isSellable(
					item.getProduct(), item.getProductOption(), item.getQuantity()
				)
					? "INVALID_ORDER_QUANTITY"
					: "UNSELLABLE_ITEM";
				issues.add(new CartDtos.CartValidationIssueResponse(item.getId(), code, reason));
			}
		}
		return issues;
	}

	private CartDtos.CartItemResponse toItemResponse(CartItem item) {
		Product product = item.getProduct();
		ProductOption option = item.getProductOption();
		long unitPrice = MoneyMath.addNonNegative(product.getBasePrice(), option.getAdditionalPrice());
		long lineAmount = MoneyMath.multiplyNonNegative(unitPrice, item.getQuantity());
		String unavailableReason = unavailableReason(item);
		return new CartDtos.CartItemResponse(
			item.getId(),
			product.getId(),
			option.getId(),
			product.getName(),
			option.getName(),
			item.getQuantity(),
			product.getMinimumOrderQuantity(),
			product.getOrderQuantityStep(),
			unitPrice,
			lineAmount,
			product.getStatus(),
			option.getStatus(),
			product.getThumbnailImageUrl(),
			unavailableReason == null,
			unavailableReason
		);
	}

	private String unavailableReason(CartItem item) {
		Product product = item.getProduct();
		ProductOption option = item.getProductOption();
		if (!productSaleability.isProductSellable(product)) {
			return "판매가 중지된 상품입니다. 삭제 후 주문해 주세요.";
		}
		if (option.getStatus() != ProductOptionStatus.ACTIVE) {
			return "현재 선택한 옵션은 판매가 중지되었습니다. 삭제 후 다른 옵션을 선택해 주세요.";
		}
		if (!productSaleability.hasValidCustomerUnitPrice(product, option)) {
			return "현재 가격을 확인 중인 상품입니다. 삭제 후 다시 선택해 주세요.";
		}
		if (!productSaleability.isSellable(product, option, item.getQuantity())) {
			return "현재 선택 수량은 품절 또는 주문 중지로 주문할 수 없습니다. 수량을 줄이거나 다른 옵션을 선택해 주세요.";
		}
		return orderQuantityReason(product, item.getQuantity());
	}

	private void requireOrderQuantity(Product product, int quantity) {
		String reason = orderQuantityReason(product, quantity);
		if (reason != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
		}
	}

	private String orderQuantityReason(Product product, int quantity) {
		if (quantity < product.getMinimumOrderQuantity()) {
			return "현재 수량은 %d개입니다. 최소 %d개부터 주문할 수 있습니다."
				.formatted(quantity, product.getMinimumOrderQuantity());
		}
		if (!product.acceptsOrderQuantity(quantity)) {
			return "현재 수량은 %d개입니다. %d개 단위로 주문할 수 있습니다."
				.formatted(quantity, product.getOrderQuantityStep());
		}
		return null;
	}
}
