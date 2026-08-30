package com.dropshipshop.api.checkout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.payment.domain.PaymentGroup;
import com.dropshipshop.api.payment.repository.PaymentGroupRepository;

@Service
public class CheckoutLockService {

	private final PaymentGroupRepository paymentGroupRepository;
	private final SupplierRepository supplierRepository;
	private final ProductRepository productRepository;
	private final ProductOptionRepository optionRepository;
	private final CustomerOrderRepository orderRepository;
	private final OrderItemRepository orderItemRepository;

	CheckoutLockService(
		PaymentGroupRepository paymentGroupRepository,
		SupplierRepository supplierRepository,
		ProductRepository productRepository,
		ProductOptionRepository optionRepository,
		CustomerOrderRepository orderRepository,
		OrderItemRepository orderItemRepository
	) {
		this.paymentGroupRepository = paymentGroupRepository;
		this.supplierRepository = supplierRepository;
		this.productRepository = productRepository;
		this.optionRepository = optionRepository;
		this.orderRepository = orderRepository;
		this.orderItemRepository = orderItemRepository;
	}

	public LockedCheckout lock(UUID paymentGroupId) {
		List<UUID> supplierIds = orderRepository.findSupplierIdsByPaymentGroupId(paymentGroupId);
		List<UUID> productIds = orderItemRepository.findProductIdsByPaymentGroupId(paymentGroupId);
		PaymentGroup paymentGroup = paymentGroupRepository.findByIdForUpdate(paymentGroupId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment group not found"));

		List<Supplier> suppliers = supplierIds.stream()
			.map(id -> supplierRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new IllegalStateException("Checkout supplier no longer exists")))
			.toList();
		List<Product> products = productIds.stream()
			.map(id -> productRepository.findByIdForUpdate(id)
				.orElseThrow(() -> new IllegalStateException("Checkout product no longer exists")))
			.toList();
		Map<UUID, ProductOption> optionsById = new LinkedHashMap<>();
		for (Product product : products) {
			for (ProductOption option : optionRepository.findAllByProductIdForUpdate(product.getId())) {
				optionsById.put(option.getId(), option);
			}
		}
		List<CustomerOrder> orders = orderRepository.findAllByPaymentGroupIdForUpdate(paymentGroupId);
		List<OrderItem> items = orderItemRepository.findAllByPaymentGroupIdForUpdate(paymentGroupId);
		if (orders.isEmpty() || items.isEmpty()) {
			throw new IllegalStateException("Checkout aggregate is incomplete");
		}
		return new LockedCheckout(paymentGroup, suppliers, products, optionsById, orders, items);
	}

	public record LockedCheckout(
		PaymentGroup paymentGroup,
		List<Supplier> suppliers,
		List<Product> products,
		Map<UUID, ProductOption> optionsById,
		List<CustomerOrder> orders,
		List<OrderItem> items
	) {
	}
}
