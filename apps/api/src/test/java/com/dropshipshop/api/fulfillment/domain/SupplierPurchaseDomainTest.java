package com.dropshipshop.api.fulfillment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;

class SupplierPurchaseDomainTest {

	@Test
	void orderItemKeepsSupplierSourceSnapshot() {
		CustomerOrder order = mock(CustomerOrder.class);
		Product product = mock(Product.class);
		ProductOption option = mock(ProductOption.class);
		Supplier supplier = mock(Supplier.class);
		when(product.getSupplier()).thenReturn(supplier);
		when(product.getName()).thenReturn("Safety helmet");
		when(product.getSummary()).thenReturn("Summary");
		when(product.getDetailVersion()).thenReturn(3);
		when(product.getBasePrice()).thenReturn(15000L);
		when(product.getSourcePrice()).thenReturn(10000L);
		when(product.getSourceItemNo()).thenReturn("12345678");
		when(option.getName()).thenReturn("White");
		when(option.getAdditionalPrice()).thenReturn(1000L);
		when(option.getSourceOptionCode()).thenReturn("OPT-1");
		when(option.getSourceAdditionalPrice()).thenReturn(500L);

		OrderItem item = new OrderItem(order, product, option, 1, 2);

		assertThat(item.getSourceItemNo()).isEqualTo("12345678");
		assertThat(item.getSourceOptionCode()).isEqualTo("OPT-1");
		assertThat(item.getSourceUnitPrice()).isEqualTo(10500L);
		assertThat(item.getLineAmount()).isEqualTo(32000L);
	}

	@Test
	void uncertainPurchaseCannotBeBlindlyRetried() {
		CustomerOrder order = mock(CustomerOrder.class);
		when(order.getSupplier()).thenReturn(mock(Supplier.class));
		Fulfillment fulfillment = new Fulfillment(order);
		fulfillment.startWork(Instant.now());
		fulfillment.queueDomeggookPurchase(10000, "fingerprint");
		fulfillment.markPurchaseProcessing();
		fulfillment.markPurchaseReconciliationRequired("timeout");

		assertThat(fulfillment.getPurchaseStatus()).isEqualTo(SupplierPurchaseStatus.RECONCILIATION_REQUIRED);
		assertThatThrownBy(fulfillment::retryPurchase)
			.isInstanceOf(IllegalStateException.class);
	}
}
