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
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@Service
public class CartService {

	private static final int MAX_QUANTITY = 99;

	private final CartRepository cartRepository;
	private final CartItemRepository cartItemRepository;
	private final ProductOptionRepository productOptionRepository;
	private final UserAccountRepository userAccountRepository;

	public CartService(
		CartRepository cartRepository,
		CartItemRepository cartItemRepository,
		ProductOptionRepository productOptionRepository,
		UserAccountRepository userAccountRepository
	) {
		this.cartRepository = cartRepository;
		this.cartItemRepository = cartItemRepository;
		this.productOptionRepository = productOptionRepository;
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional
	public CartDtos.CartResponse getCart(UUID userId) {
		Cart cart = getOrCreateCart(userId);
		return toCartResponse(cart);
	}

	@Transactional
	public CartDtos.CartResponse addItem(UUID userId, CartDtos.AddCartItemRequest request) {
		Cart cart = getOrCreateCart(userId);
		ProductOption option = findOption(request.productOptionId());
		Product product = option.getProduct();
		requireSellable(product, option);

		CartItem item = cartItemRepository.findByCart_IdAndProductOption_Id(cart.getId(), option.getId())
			.orElseGet(() -> new CartItem(cart, product, option, 0));
		int nextQuantity = item.getQuantity() + request.quantity();
		if (nextQuantity > MAX_QUANTITY) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart item quantity cannot exceed 99");
		}
		item.updateQuantity(nextQuantity);
		cartItemRepository.save(item);
		return toCartResponse(cart);
	}

	@Transactional
	public CartDtos.CartResponse updateItem(UUID userId, UUID cartItemId, CartDtos.UpdateCartItemRequest request) {
		CartItem item = findUserCartItem(userId, cartItemId);
		item.updateQuantity(request.quantity());
		return toCartResponse(item.getCart());
	}

	@Transactional
	public void removeItem(UUID userId, UUID cartItemId) {
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

	private UserAccount findUser(UUID userId) {
		return userAccountRepository.findById(userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
	}

	private ProductOption findOption(UUID productOptionId) {
		return productOptionRepository.findById(productOptionId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product option not found"));
	}

	private CartItem findUserCartItem(UUID userId, UUID cartItemId) {
		return cartItemRepository.findByIdAndCart_User_Id(cartItemId, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found"));
	}

	private void requireSellable(Product product, ProductOption option) {
		if (product.getStatus() != ProductStatus.ACTIVE || option.getStatus() != ProductOptionStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product option is not sellable");
		}
	}

	private CartDtos.CartResponse toCartResponse(Cart cart) {
		List<CartItem> items = cartItemRepository.findAllByCart_User_IdOrderByCreatedAtAsc(cart.getUser().getId());
		List<CartDtos.CartItemResponse> itemResponses = items.stream()
			.map(this::toItemResponse)
			.toList();
		List<CartDtos.CartValidationIssueResponse> issues = validationIssues(items);
		long subtotalAmount = itemResponses.stream()
			.mapToLong(CartDtos.CartItemResponse::lineAmount)
			.sum();
		return new CartDtos.CartResponse(cart.getId(), itemResponses, subtotalAmount, issues.isEmpty(), issues);
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
		for (CartItem item : items) {
			String reason = unavailableReason(item.getProduct(), item.getProductOption());
			if (reason != null) {
				issues.add(new CartDtos.CartValidationIssueResponse(item.getId(), "UNSELLABLE_ITEM", reason));
			}
		}
		return issues;
	}

	private CartDtos.CartItemResponse toItemResponse(CartItem item) {
		Product product = item.getProduct();
		ProductOption option = item.getProductOption();
		long unitPrice = product.getBasePrice() + option.getAdditionalPrice();
		String unavailableReason = unavailableReason(product, option);
		return new CartDtos.CartItemResponse(
			item.getId(),
			product.getId(),
			option.getId(),
			product.getName(),
			option.getName(),
			item.getQuantity(),
			unitPrice,
			unitPrice * item.getQuantity(),
			product.getStatus(),
			option.getStatus(),
			product.getThumbnailImageUrl(),
			unavailableReason == null,
			unavailableReason
		);
	}

	private String unavailableReason(Product product, ProductOption option) {
		if (product.getStatus() != ProductStatus.ACTIVE) {
			return "판매가 중지된 상품입니다. 삭제 후 주문해 주세요.";
		}
		if (option.getStatus() != ProductOptionStatus.ACTIVE) {
			return "현재 선택한 옵션은 판매가 중지되었습니다. 삭제 후 다른 옵션을 선택해 주세요.";
		}
		return null;
	}
}
