package com.dropshipshop.api.order;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

@RestController
@RequestMapping("/api/orders")
@PreAuthorize("hasRole('CUSTOMER')")
class CustomerOrderController {

	private final CustomerOrderQueryService customerOrderQueryService;
	private final CurrentUser currentUser;

	CustomerOrderController(CustomerOrderQueryService customerOrderQueryService, CurrentUser currentUser) {
		this.customerOrderQueryService = customerOrderQueryService;
		this.currentUser = currentUser;
	}

	@GetMapping
	OrderDtos.OrderListResponse listOrders(Authentication authentication) {
		return customerOrderQueryService.listOrders(currentUser.id(authentication));
	}

	@GetMapping("/{orderId}")
	OrderDtos.OrderDetailResponse getOrder(@PathVariable UUID orderId, Authentication authentication) {
		return customerOrderQueryService.getOrder(currentUser.id(authentication), orderId);
	}
}
