package com.dropshipshop.api.checkout;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.account.AccountAgreementService;
import com.dropshipshop.api.account.AccountProfileService;
import com.dropshipshop.api.cart.domain.CartItem;
import com.dropshipshop.api.cart.repository.CartItemRepository;
import com.dropshipshop.api.cart.repository.CartRepository;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.StorefrontSalesProperties;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.order.repository.OrderPolicyAgreementRepository;
import com.dropshipshop.api.payment.BankTransferProperties;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;
import com.dropshipshop.api.policy.CustomerPolicyLinkService;
import com.dropshipshop.api.refund.repository.RefundRepository;
import com.dropshipshop.api.supplierproduct.ProductSaleability;
import com.dropshipshop.api.supplierportal.SupplierContractTerminalService;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.persistence.EntityManager;

class CheckoutCatalogLockTest {

	@Test
	void rejectsCheckoutWhenProductSupplierChangedAfterCartDiscovery() {
		UUID cartId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		UUID discoveredSupplierId = UUID.randomUUID();
		UUID currentSupplierId = UUID.randomUUID();
		CartItemRepository cartItemRepository = mock(CartItemRepository.class);
		ProductRepository productRepository = mock(ProductRepository.class);
		ProductOptionRepository optionRepository = mock(ProductOptionRepository.class);
		SupplierRepository supplierRepository = mock(SupplierRepository.class);
		EntityManager entityManager = mock(EntityManager.class);
		CartItem discoveredItem = mock(CartItem.class);
		Product discoveredProduct = mock(Product.class);
		Supplier discoveredSupplier = mock(Supplier.class);
		Product lockedProduct = mock(Product.class);
		Supplier currentSupplier = mock(Supplier.class);
		when(discoveredItem.getProduct()).thenReturn(discoveredProduct);
		when(discoveredProduct.getId()).thenReturn(productId);
		when(discoveredProduct.getSupplier()).thenReturn(discoveredSupplier);
		when(discoveredSupplier.getId()).thenReturn(discoveredSupplierId);
		when(supplierRepository.findByIdForUpdate(discoveredSupplierId))
			.thenReturn(Optional.of(discoveredSupplier));
		when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(lockedProduct));
		when(lockedProduct.getSupplier()).thenReturn(currentSupplier);
		when(currentSupplier.getId()).thenReturn(currentSupplierId);
		CheckoutService service = new CheckoutService(
			mock(CartRepository.class),
			cartItemRepository,
			mock(ProductNoticeRepository.class),
			productRepository,
			optionRepository,
			supplierRepository,
			mock(PaymentGroupRepository.class),
			mock(CustomerOrderRepository.class),
			mock(OrderItemRepository.class),
			mock(OrderPolicyAgreementRepository.class),
			mock(RefundRepository.class),
			mock(UserAccountRepository.class),
			mock(AccountAgreementService.class),
			mock(AccountProfileService.class),
			mock(CustomerPolicyLinkService.class),
			mock(CheckoutPolicyProperties.class),
			mock(BankTransferProperties.class),
			mock(NotificationService.class),
			mock(StorefrontSalesProperties.class),
			mock(ProductSaleability.class),
			mock(SupplierContractTerminalService.class),
			entityManager
		);

		assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
			service,
			"lockCatalogAndReloadCart",
			cartId,
			List.of(discoveredItem)
		))
			.isInstanceOfSatisfying(ResponseStatusException.class,
				exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
					.isEqualTo(HttpStatus.BAD_REQUEST));

		verify(entityManager).clear();
		verify(optionRepository, never()).findAllByProductIdForUpdate(productId);
		verify(cartItemRepository, never()).findAllByCart_IdOrderByCreatedAtAsc(cartId);
	}
}
