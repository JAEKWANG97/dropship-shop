package com.dropshipshop.api.cart;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cart")
@PreAuthorize("hasRole('CUSTOMER')")
class CartController {

	private final CartService cartService;
	private final CurrentUser currentUser;

	CartController(CartService cartService, CurrentUser currentUser) {
		this.cartService = cartService;
		this.currentUser = currentUser;
	}

	@GetMapping
	CartDtos.CartResponse getCart(Authentication authentication) {
		return cartService.getCart(currentUser.id(authentication));
	}

	@PostMapping("/items")
	@ResponseStatus(HttpStatus.CREATED)
	CartDtos.CartResponse addItem(
		@Valid @RequestBody CartDtos.AddCartItemRequest request,
		Authentication authentication
	) {
		return cartService.addItem(currentUser.id(authentication), request);
	}

	@PatchMapping("/items/{cartItemId}")
	CartDtos.CartResponse updateItem(
		@PathVariable UUID cartItemId,
		@Valid @RequestBody CartDtos.UpdateCartItemRequest request,
		Authentication authentication
	) {
		return cartService.updateItem(currentUser.id(authentication), cartItemId, request);
	}

	@DeleteMapping("/items/{cartItemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeItem(@PathVariable UUID cartItemId, Authentication authentication) {
		cartService.removeItem(currentUser.id(authentication), cartItemId);
	}

	@PostMapping("/validate")
	CartDtos.CartValidationResponse validateCart(Authentication authentication) {
		return cartService.validateCart(currentUser.id(authentication));
	}
}
