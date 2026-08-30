package com.dropshipshop.api.fulfillment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.fulfillment.domain.Fulfillment;
import com.dropshipshop.api.fulfillment.repository.FulfillmentRepository;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderItem;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;

@Service
public class SupplierFulfillmentRoutingService {

	private static final Duration DEFAULT_PII_WINDOW = Duration.ofDays(60);

	private final FulfillmentRepository fulfillmentRepository;
	private final UserAccountRepository userAccountRepository;
	private final NotificationService notificationService;
	private final SupplierPortalFeatureGate featureGate;

	SupplierFulfillmentRoutingService(
		FulfillmentRepository fulfillmentRepository,
		UserAccountRepository userAccountRepository,
		NotificationService notificationService,
		SupplierPortalFeatureGate featureGate
	) {
		this.fulfillmentRepository = fulfillmentRepository;
		this.userAccountRepository = userAccountRepository;
		this.notificationService = notificationService;
		this.featureGate = featureGate;
	}

	public Fulfillment routePaidOrder(CustomerOrder order, List<OrderItem> items, Instant now) {
		if (items.isEmpty() || items.stream()
			.anyMatch(item -> item.getManagementChannelSnapshot() != ProductManagementChannel.SUPPLIER_PORTAL)) {
			return fulfillmentRepository.findByOrder_Id(order.getId()).orElse(null);
		}
		Fulfillment existing = fulfillmentRepository.findByOrder_Id(order.getId()).orElse(null);
		if (existing != null) {
			return existing;
		}
		Supplier supplier = order.getSupplier();
		Fulfillment fulfillment = new Fulfillment(order);
		if (isPortalOperational(supplier, now)) {
			fulfillment.routeToSupplierPortal(now, now.plus(DEFAULT_PII_WINDOW));
			order.lockAddressForSupplierPortal(now);
			fulfillmentRepository.save(fulfillment);
			notificationService.supplierFulfillmentRequested(supplier, order);
			return fulfillment;
		}
		// KEEP deliberately leaves sales active while Coreable handles newly paid work.
		return fulfillmentRepository.save(fulfillment);
	}

	private boolean isPortalOperational(Supplier supplier, Instant now) {
		return featureGate.isEnabled()
			&& supplier.getPortalStatus() == SupplierPortalStatus.ACTIVE
			&& supplier.getManagerUserId() != null
			&& supplier.getContactEmailVerifiedAt() != null
			&& supplier.getEmail() != null
			&& !supplier.getEmail().isBlank()
			&& supplier.hasTimeValidContract(now)
			&& userAccountRepository.findByIdAndStatus(supplier.getManagerUserId(), UserStatus.ACTIVE).isPresent();
	}
}
