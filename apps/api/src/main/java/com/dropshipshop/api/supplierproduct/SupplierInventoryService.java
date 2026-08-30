package com.dropshipshop.api.supplierproduct;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.domain.InventoryMode;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierAvailability;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.OrderItemRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalHasher;
import com.dropshipshop.api.supplierportal.SupplierPortalInputPolicy;
import com.dropshipshop.api.supplierproduct.domain.SupplierInventoryChangeHistory;
import com.dropshipshop.api.supplierproduct.repository.SupplierInventoryChangeHistoryRepository;

@Service
class SupplierInventoryService {

	private static final String HASH_DOMAIN = "supplier-inventory-update-v1";

	private final SupplierRepository supplierRepository;
	private final ProductRepository productRepository;
	private final ProductOptionRepository optionRepository;
	private final OrderItemRepository orderItemRepository;
	private final SupplierInventoryChangeHistoryRepository historyRepository;
	private final SupplierPortalInputPolicy inputPolicy;
	private final SupplierPortalHasher hasher;
	private final Clock clock;

	SupplierInventoryService(
		SupplierRepository supplierRepository,
		ProductRepository productRepository,
		ProductOptionRepository optionRepository,
		OrderItemRepository orderItemRepository,
		SupplierInventoryChangeHistoryRepository historyRepository,
		SupplierPortalInputPolicy inputPolicy,
		SupplierPortalHasher hasher
	) {
		this.supplierRepository = supplierRepository;
		this.productRepository = productRepository;
		this.optionRepository = optionRepository;
		this.orderItemRepository = orderItemRepository;
		this.historyRepository = historyRepository;
		this.inputPolicy = inputPolicy;
		this.hasher = hasher;
		this.clock = Clock.systemUTC();
	}

	@Transactional
	SupplierProductDtos.InventoryResponse update(
		UUID userId,
		UUID productId,
		UUID optionId,
		String idempotencyKey,
		SupplierProductDtos.InventoryUpdateRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		validateRequest(request);
		String requestHash = requestHash(productId, optionId, request);
		Supplier supplier = supplierRepository.findByManagerUserIdForUpdate(userId)
			.filter(candidate -> candidate.isPortalAuthorityActive(Instant.now(clock)))
			.orElseThrow(this::notFound);
		SupplierProductDtos.InventoryResponse replay = replay(
			supplier.getId(), optionId, key, requestHash
		);
		if (replay != null) {
			return replay;
		}
		Product product = productRepository.findByIdAndSupplierIdAndManagementChannelForUpdate(
			productId, supplier.getId(), ProductManagementChannel.SUPPLIER_PORTAL
		).orElseThrow(this::notFound);
		List<ProductOption> options = optionRepository.findAllByProductIdForUpdate(productId);

		replay = replay(supplier.getId(), optionId, key, requestHash);
		if (replay != null) {
			return replay;
		}
		ProductOption option = options.stream()
			.filter(candidate -> candidate.getId().equals(optionId))
			.findFirst()
			.orElseThrow(this::notFound);
		if (!option.getProduct().getId().equals(product.getId())) {
			throw notFound();
		}
		if (option.getInventoryVersion() != request.expectedInventoryVersion()) {
			throw inventoryConflict(option);
		}
		if (option.getInventoryMode() != request.inventoryMode()
			&& orderItemRepository.existsByProductOption_IdAndOrder_Status(optionId, OrderStatus.PAYMENT_PENDING)) {
			throw inventoryConflict(option);
		}

		SupplierAvailability beforeAvailability = option.getSupplierAvailability();
		InventoryMode beforeMode = option.getInventoryMode();
		Long beforeOnHand = option.getOnHandQuantity();
		long beforeReserved = option.getReservedQuantity();
		long beforeVersion = option.getInventoryVersion();
		try {
			option.updateInventory(request.supplierAvailability(), request.inventoryMode(), request.onHandQuantity());
		} catch (IllegalStateException exception) {
			throw inventoryConflict(option);
		}
		Instant now = Instant.now(clock);
		historyRepository.save(new SupplierInventoryChangeHistory(
			option,
			supplier,
			userId,
			beforeAvailability,
			option.getSupplierAvailability(),
			beforeMode,
			option.getInventoryMode(),
			beforeOnHand,
			option.getOnHandQuantity(),
			beforeReserved,
			option.getReservedQuantity(),
			beforeVersion,
			option.getInventoryVersion(),
			requestHash,
			key,
			now
		));
		return response(option);
	}

	private SupplierProductDtos.InventoryResponse replay(
		UUID supplierId,
		UUID optionId,
		String idempotencyKey,
		String requestHash
	) {
		SupplierInventoryChangeHistory history = historyRepository
			.findBySupplier_IdAndSubjectProductOptionIdAndIdempotencyKey(supplierId, optionId, idempotencyKey)
			.orElse(null);
		if (history == null) {
			return null;
		}
		if (!history.matchesReplay(idempotencyKey, requestHash)) {
			throw new ApiErrorException(
				HttpStatus.CONFLICT,
				ApiErrorCode.IDEMPOTENCY_CONFLICT,
				"Idempotency-Key was already used with a different inventory request"
			);
		}
		return response(history);
	}

	private String requestHash(
		UUID productId,
		UUID optionId,
		SupplierProductDtos.InventoryUpdateRequest request
	) {
		return hasher.hmac(
			HASH_DOMAIN,
			productId.toString(),
			optionId.toString(),
			request.expectedInventoryVersion().toString(),
			request.supplierAvailability().name(),
			request.inventoryMode().name(),
			request.onHandQuantity() == null ? "null" : request.onHandQuantity().toString()
		);
	}

	private void validateRequest(SupplierProductDtos.InventoryUpdateRequest request) {
		if (request.inventoryMode() == InventoryMode.TRACKED && request.onHandQuantity() == null) {
			throw validation("Tracked inventory requires onHandQuantity");
		}
		if (request.inventoryMode() == InventoryMode.UNTRACKED && request.onHandQuantity() != null) {
			throw validation("Untracked inventory must omit onHandQuantity");
		}
	}

	private SupplierProductDtos.InventoryResponse response(ProductOption option) {
		return new SupplierProductDtos.InventoryResponse(
			option.getId(),
			option.getInventoryVersion(),
			option.getSupplierAvailability(),
			option.getInventoryMode(),
			option.getOnHandQuantity(),
			option.getReservedQuantity(),
			option.isTracked() ? option.getAvailableQuantity() : null
		);
	}

	private SupplierProductDtos.InventoryResponse response(SupplierInventoryChangeHistory history) {
		Long availableQuantity = history.getAfterInventoryMode() == InventoryMode.TRACKED
			? Math.subtractExact(history.getAfterOnHandQuantity(), history.getAfterReservedQuantity())
			: null;
		return new SupplierProductDtos.InventoryResponse(
			history.getSubjectProductOptionId(),
			history.getAfterInventoryVersion(),
			history.getAfterSupplierAvailability(),
			history.getAfterInventoryMode(),
			history.getAfterOnHandQuantity(),
			history.getAfterReservedQuantity(),
			availableQuantity
		);
	}

	private ApiErrorException inventoryConflict(ProductOption option) {
		return new ApiErrorException(
			HttpStatus.CONFLICT,
			ApiErrorCode.INVENTORY_CONFLICT,
			"Inventory changed; refresh and retry",
			new SupplierProductDtos.InventoryConflictDetails(response(option))
		);
	}

	private ApiErrorException validation(String message) {
		return new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, message);
	}

	private ApiErrorException notFound() {
		return new ApiErrorException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Product option not found");
	}
}
