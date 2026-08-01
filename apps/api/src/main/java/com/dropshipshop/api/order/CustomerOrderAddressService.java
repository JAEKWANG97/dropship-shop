package com.dropshipshop.api.order;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.domain.ShippingAddressSnapshot;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;

@Service
class CustomerOrderAddressService {

	private final CustomerOrderRepository orderRepository;
	private final CustomerOrderQueryService customerOrderQueryService;

	CustomerOrderAddressService(
		CustomerOrderRepository orderRepository,
		CustomerOrderQueryService customerOrderQueryService
	) {
		this.orderRepository = orderRepository;
		this.customerOrderQueryService = customerOrderQueryService;
	}

	@Transactional
	OrderDtos.OrderDetailResponse updateShippingAddress(
		UUID userId,
		UUID orderId,
		OrderDtos.UpdateShippingAddressRequest request
	) {
		CustomerOrder order = orderRepository.findByIdAndUser_Id(orderId, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
		if (order.getPaymentGroup().getPolicyConfirmedAt() != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirmed order shipping address cannot be changed");
		}
		if (order.getStatus() != OrderStatus.SUPPLIER_ORDER_PENDING
			|| order.getSupplierOrderStartedAt() != null
			|| order.getAddressLockedAt() != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order address can be changed only before supplier work starts");
		}
		order.updateCustomerAddressBeforeSupplierWork(new ShippingAddressSnapshot(
			request.recipientName(),
			request.recipientPhone(),
			request.postalCode(),
			request.address1(),
			request.address2()
		));
		return customerOrderQueryService.getOrder(userId, orderId);
	}
}
