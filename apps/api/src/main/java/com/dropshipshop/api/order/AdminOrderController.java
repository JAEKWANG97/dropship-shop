package com.dropshipshop.api.order;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.auth.security.CurrentUser;
import com.dropshipshop.api.procurement.DomeggookPurchaseService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
class AdminOrderController {

	private final AdminOrderQueryService adminOrderQueryService;
	private final AdminOrderFulfillmentService adminOrderFulfillmentService;
	private final AdminOrderPaymentService adminOrderPaymentService;
	private final AdminOrderShipmentService adminOrderShipmentService;
	private final DomeggookPurchaseService domeggookPurchaseService;
	private final CurrentUser currentUser;

	AdminOrderController(
		AdminOrderQueryService adminOrderQueryService,
		AdminOrderFulfillmentService adminOrderFulfillmentService,
		AdminOrderPaymentService adminOrderPaymentService,
		AdminOrderShipmentService adminOrderShipmentService,
		DomeggookPurchaseService domeggookPurchaseService,
		CurrentUser currentUser
	) {
		this.adminOrderQueryService = adminOrderQueryService;
		this.adminOrderFulfillmentService = adminOrderFulfillmentService;
		this.adminOrderPaymentService = adminOrderPaymentService;
		this.adminOrderShipmentService = adminOrderShipmentService;
		this.domeggookPurchaseService = domeggookPurchaseService;
		this.currentUser = currentUser;
	}

	@GetMapping
	AdminOrderDtos.AdminOrderListResponse listSupplierOrderPendingOrders(
		@RequestParam(required = false) com.dropshipshop.api.order.domain.OrderStatus status
	) {
		if (status == null) {
			return adminOrderQueryService.listSupplierOrderPendingOrders();
		}
		return adminOrderQueryService.listOrders(status);
	}

	@GetMapping("/{orderId}")
	AdminOrderDtos.AdminOrderDetailResponse getOrder(@PathVariable UUID orderId) {
		return adminOrderQueryService.getOrder(orderId);
	}

	@GetMapping("/{orderId}/status-history")
	AdminOrderDtos.OrderStatusHistoryListResponse listOrderStatusHistory(@PathVariable UUID orderId) {
		return adminOrderQueryService.listOrderStatusHistory(orderId);
	}

	@PostMapping("/{orderId}/supplier-work-start")
	@ResponseStatus(HttpStatus.OK)
	AdminOrderDtos.AdminOrderActionResponse startSupplierWork(
		@PathVariable UUID orderId,
		@Valid @RequestBody AdminOrderDtos.SupplierWorkStartRequest request,
		Authentication authentication
	) {
		return adminOrderFulfillmentService.startSupplierWork(orderId, currentUser.id(authentication), request);
	}

	@PostMapping("/{orderId}/supplier-order-completed")
	@ResponseStatus(HttpStatus.OK)
	AdminOrderDtos.AdminOrderActionResponse markSupplierOrderCompleted(
		@PathVariable UUID orderId,
		@Valid @RequestBody AdminOrderDtos.SupplierOrderCompletedRequest request,
		Authentication authentication
	) {
		return adminOrderFulfillmentService.markSupplierOrderCompleted(orderId, currentUser.id(authentication), request);
	}

	@PostMapping("/{orderId}/out-of-stock")
	@ResponseStatus(HttpStatus.OK)
	AdminOrderDtos.AdminOrderActionResponse markOutOfStock(
		@PathVariable UUID orderId,
		@Valid @RequestBody AdminOrderDtos.OutOfStockRequest request,
		Authentication authentication
	) {
		return adminOrderFulfillmentService.markOutOfStock(orderId, currentUser.id(authentication), request);
	}

	@PostMapping("/{orderId}/delay-notice")
	@ResponseStatus(HttpStatus.OK)
	AdminOrderDtos.AdminOrderActionResponse sendDelayNotice(
		@PathVariable UUID orderId,
		@Valid @RequestBody AdminOrderDtos.DelayNoticeRequest request,
		Authentication authentication
	) {
		return adminOrderFulfillmentService.sendDelayNotice(orderId, currentUser.id(authentication), request);
	}

	@PostMapping("/{orderId}/shipments")
	@ResponseStatus(HttpStatus.OK)
	AdminOrderDtos.AdminOrderActionResponse createShipment(
		@PathVariable UUID orderId,
		@Valid @RequestBody AdminOrderDtos.ShipmentCreateRequest request,
		Authentication authentication
	) {
		return adminOrderShipmentService.createShipment(orderId, currentUser.id(authentication), request);
	}

	@PostMapping("/{orderId}/confirm-deposit")
	@ResponseStatus(HttpStatus.OK)
	AdminOrderDtos.AdminOrderActionResponse confirmDeposit(
		@PathVariable UUID orderId,
		@Valid @RequestBody AdminOrderDtos.BankTransferDepositConfirmRequest request,
		Authentication authentication
	) {
		return adminOrderPaymentService.confirmBankTransferDeposit(orderId, currentUser.id(authentication), request);
	}

	@PostMapping("/{orderId}/unpaid-cancel")
	@ResponseStatus(HttpStatus.OK)
	AdminOrderDtos.AdminOrderActionResponse cancelUnpaid(
		@PathVariable UUID orderId,
		@Valid @RequestBody AdminOrderDtos.BankTransferUnpaidCancelRequest request,
		Authentication authentication
	) {
		return adminOrderPaymentService.cancelUnpaidBankTransfer(orderId, currentUser.id(authentication), request);
	}

	@PostMapping("/{orderId}/deposit-mismatch")
	@ResponseStatus(HttpStatus.OK)
	AdminOrderDtos.AdminOrderActionResponse recordDepositMismatch(
		@PathVariable UUID orderId,
		@Valid @RequestBody AdminOrderDtos.BankTransferDepositMismatchRequest request,
		Authentication authentication
	) {
		return adminOrderPaymentService.recordBankTransferDepositMismatch(orderId, currentUser.id(authentication), request);
	}

	@PostMapping("/{orderId}/supplier-order/validate")
	AdminOrderDtos.SupplierPurchaseValidationResponse validateSupplierOrder(@PathVariable UUID orderId) {
		try {
			DomeggookPurchaseService.ValidationResult result = domeggookPurchaseService.validate(orderId);
			return new AdminOrderDtos.SupplierPurchaseValidationResponse(
				result.expectedAmount(),
				result.itemAmount(),
				result.shippingAmount()
			);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@PostMapping("/{orderId}/supplier-order/retry")
	AdminOrderDtos.AdminOrderDetailResponse retrySupplierOrder(@PathVariable UUID orderId) {
		try {
			domeggookPurchaseService.retry(orderId);
			return adminOrderQueryService.getOrder(orderId);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@PostMapping("/{orderId}/supplier-order/reconcile")
	AdminOrderDtos.AdminOrderDetailResponse reconcileSupplierOrder(@PathVariable UUID orderId) {
		try {
			domeggookPurchaseService.reconcile(orderId);
			return adminOrderQueryService.getOrder(orderId);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@PostMapping("/{orderId}/supplier-order/cancel")
	AdminOrderDtos.AdminOrderDetailResponse cancelSupplierOrder(
		@PathVariable UUID orderId,
		@Valid @RequestBody AdminOrderDtos.SupplierPurchaseCancelRequest request
	) {
		try {
			domeggookPurchaseService.cancel(orderId, request.reason());
			return adminOrderQueryService.getOrder(orderId);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}
}
