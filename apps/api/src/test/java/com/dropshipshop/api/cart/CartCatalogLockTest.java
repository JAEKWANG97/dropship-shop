package com.dropshipshop.api.cart;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.cart.domain.Cart;
import com.dropshipshop.api.cart.repository.CartItemRepository;
import com.dropshipshop.api.cart.repository.CartRepository;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.StorefrontSalesProperties;
import com.dropshipshop.api.supplierproduct.ProductSaleability;
import com.dropshipshop.api.user.repository.UserAccountRepository;

class CartCatalogLockTest {

	@Test
	void rejectsAddWhenProductSupplierChangedAfterOwnershipDiscovery() {
		UUID userId = UUID.randomUUID();
		UUID optionId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		UUID discoveredSupplierId = UUID.randomUUID();
		UUID currentSupplierId = UUID.randomUUID();
		CartRepository cartRepository = mock(CartRepository.class);
		CartItemRepository cartItemRepository = mock(CartItemRepository.class);
		ProductOptionRepository optionRepository = mock(ProductOptionRepository.class);
		ProductRepository productRepository = mock(ProductRepository.class);
		SupplierRepository supplierRepository = mock(SupplierRepository.class);
		ProductOptionRepository.OptionOwnership ownership = mock(ProductOptionRepository.OptionOwnership.class);
		Product lockedProduct = mock(Product.class);
		Supplier lockedSupplier = mock(Supplier.class);
		Supplier currentSupplier = mock(Supplier.class);
		when(cartRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(mock(Cart.class)));
		when(optionRepository.findOwnershipById(optionId)).thenReturn(Optional.of(ownership));
		when(ownership.getProductId()).thenReturn(productId);
		when(ownership.getSupplierId()).thenReturn(discoveredSupplierId);
		when(supplierRepository.findByIdForUpdate(discoveredSupplierId)).thenReturn(Optional.of(lockedSupplier));
		when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(lockedProduct));
		when(lockedProduct.getSupplier()).thenReturn(currentSupplier);
		when(currentSupplier.getId()).thenReturn(currentSupplierId);
		CartService service = new CartService(
			cartRepository,
			cartItemRepository,
			optionRepository,
			productRepository,
			supplierRepository,
			mock(UserAccountRepository.class),
			mock(StorefrontSalesProperties.class),
			mock(ProductSaleability.class)
		);

		assertThatThrownBy(() -> service.addItem(userId, new CartDtos.AddCartItemRequest(optionId, 1)))
			.isInstanceOfSatisfying(ResponseStatusException.class,
				exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
					.isEqualTo(HttpStatus.BAD_REQUEST));

		verify(optionRepository, never()).findAllByProductIdForUpdate(productId);
		verify(cartItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
	}
}
