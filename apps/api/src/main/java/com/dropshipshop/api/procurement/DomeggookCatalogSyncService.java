package com.dropshipshop.api.procurement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.dropshipshop.api.catalog.domain.PricingPolicy;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductChangeActor;
import com.dropshipshop.api.catalog.domain.ProductChangeHistory;
import com.dropshipshop.api.catalog.domain.ProductChangeType;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.domain.ProductNoticeStatus;
import com.dropshipshop.api.catalog.repository.PricingPolicyRepository;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.catalog.pricing.CatalogPriceCalculator;
import com.dropshipshop.api.catalog.pricing.ProductPriceCalculation;
import com.dropshipshop.api.common.money.MoneyMath;

@Service
class DomeggookCatalogSyncService {

	private static final String SYSTEM_CODE = "DOMEGGOOK_CATALOG_SYNC";
	private static final String REASON = "공급처 상품 정기 동기화";

	private final DomeggookPurchaseClient client;
	private final ProductRepository productRepository;
	private final ProductOptionRepository optionRepository;
	private final ProductChangeHistoryRepository historyRepository;
	private final PricingPolicyRepository pricingPolicyRepository;
	private final SupplierRepository supplierRepository;
	private final ProductImageRepository imageRepository;
	private final ProductNoticeRepository noticeRepository;
	private final CatalogPriceCalculator priceCalculator;
	private final TransactionTemplate transactionTemplate;

	@Autowired
	DomeggookCatalogSyncService(
		DomeggookPurchaseClient client,
		ProductRepository productRepository,
		ProductOptionRepository optionRepository,
		ProductChangeHistoryRepository historyRepository,
		PricingPolicyRepository pricingPolicyRepository,
		SupplierRepository supplierRepository,
		ProductImageRepository imageRepository,
		ProductNoticeRepository noticeRepository,
		CatalogPriceCalculator priceCalculator,
		TransactionTemplate transactionTemplate
	) {
		this.client = client;
		this.productRepository = productRepository;
		this.optionRepository = optionRepository;
		this.historyRepository = historyRepository;
		this.pricingPolicyRepository = pricingPolicyRepository;
		this.supplierRepository = supplierRepository;
		this.imageRepository = imageRepository;
		this.noticeRepository = noticeRepository;
		this.priceCalculator = priceCalculator;
		this.transactionTemplate = transactionTemplate;
	}

	DomeggookCatalogSyncService(
		DomeggookPurchaseClient client,
		ProductRepository productRepository,
		ProductOptionRepository optionRepository,
		ProductChangeHistoryRepository historyRepository,
		PricingPolicyRepository pricingPolicyRepository,
		TransactionTemplate transactionTemplate
	) {
		this(client, productRepository, optionRepository, historyRepository, pricingPolicyRepository,
			null, null, null, null, transactionTemplate);
	}

	List<UUID> targetProductIds(int batchSize) {
		return transactionTemplate.execute(status -> productRepository.findSourceSyncTargets(PageRequest.of(0, batchSize))
			.stream().map(Product::getId).toList());
	}

	SyncResult sync(UUID productId, boolean apply) {
		String sourceItemNo = transactionTemplate.execute(status -> productRepository.findById(productId)
			.filter(product -> product.getManagementChannel() == ProductManagementChannel.COREABLE)
			.map(Product::getSourceItemNo)
			.orElse(null));
		if (sourceItemNo == null) return new SyncResult(productId, false, 0, 0);

		try {
			DomeggookPurchaseClient.CatalogSnapshot snapshot = client.catalogSnapshot(sourceItemNo);
			if (!apply) return new SyncResult(productId, snapshot.available(), snapshot.sourcePrice(), snapshot.options().size());
			transactionTemplate.executeWithoutResult(status -> apply(productId, sourceItemNo, snapshot));
			return new SyncResult(productId, snapshot.available(), snapshot.sourcePrice(), snapshot.options().size());
		} catch (RuntimeException exception) {
			if (apply) transactionTemplate.executeWithoutResult(status -> markFailed(productId, sourceItemNo, exception));
			throw exception;
		}
	}

	private void apply(UUID productId, String expectedSourceItemNo, DomeggookPurchaseClient.CatalogSnapshot snapshot) {
		Product product = lockProduct(productId);
		if (product == null || !isTarget(product)
			|| !Objects.equals(expectedSourceItemNo, product.getSourceItemNo())) return;

		List<ProductOption> lockedOptions = new ArrayList<>(supplierRepository == null
			? optionRepository.findAllByProduct_IdOrderBySortOrderAscCreatedAtAsc(productId)
			: optionRepository.findAllByProductIdForUpdate(productId));
		PricingPolicy policy = (supplierRepository == null
			? pricingPolicyRepository.findFirstByActiveTrueOrderByCreatedAtAsc()
			: pricingPolicyRepository.findActiveForUpdate()).orElse(null);
		ProductPriceCalculation calculation = priceCalculator == null || policy == null ? null : priceCalculator.calculate(
			snapshot.sourcePrice(),
			snapshot.options().stream().map(DomeggookPurchaseClient.SourceOption::sourceAdditionalPrice).toList(),
			snapshot.minimumResalePrice(),
			policy
		);
		List<PricedOption> pricedOptions = new java.util.ArrayList<>();
		for (int index = 0; index < snapshot.options().size(); index++) {
			DomeggookPurchaseClient.SourceOption option = snapshot.options().get(index);
			long price = calculation == null
				? salePrice(MoneyMath.addNonNegative(snapshot.sourcePrice(), option.sourceAdditionalPrice()),
					snapshot.minimumResalePrice(), policy)
				: calculation.options().get(index).customerTotalPrice();
			pricedOptions.add(new PricedOption(option, price));
		}
		long basePrice = calculation == null
			? salePrice(snapshot.sourcePrice(), snapshot.minimumResalePrice(), policy)
			: calculation.basePrice();

		updateProductPrice(product, snapshot.sourcePrice(), basePrice);
		if (policy != null) product.applyPricing(policy, basePrice);
		if (snapshot.minimumOrderQuantity() <= 99 && snapshot.orderQuantityStep() <= 99) {
			updateOrderQuantityRules(product, snapshot.minimumOrderQuantity(), snapshot.orderQuantityStep());
		}
		updateOptions(product, lockedOptions, pricedOptions, basePrice);

		if (snapshot.minimumOrderQuantity() > 10 && product.getStatus() == ProductStatus.ACTIVE) {
			updateProductStatus(product, ProductStatus.HIDDEN);
		} else if (!snapshot.available() && product.getStatus() == ProductStatus.ACTIVE) {
			updateProductStatus(product, ProductStatus.SOLD_OUT);
			product.markSourceAutoSoldOut();
		} else if (snapshot.available() && snapshot.minimumOrderQuantity() <= 10
			&& product.getStatus() == ProductStatus.SOLD_OUT && product.isSourceAutoSoldOut()
			&& isSaleReadyForReactivation(product, lockedOptions)) {
			updateProductStatus(product, ProductStatus.ACTIVE);
			product.clearSourceAutoSoldOut();
		}
		product.markSourceSynced(snapshot.available(), Instant.now());
		historyRepository.save(history(product, null, ProductChangeType.PRODUCT_BASE,
			"aggregateVersion=" + product.getVersion(),
			"aggregateVersion=" + (product.getVersion() + 1) + ";" + pricingSnapshot(product, lockedOptions, snapshot.minimumResalePrice())));
		product.incrementVersion();
	}

	private void updateOrderQuantityRules(Product product, int minimumOrderQuantity, int orderQuantityStep) {
		if (
			product.getMinimumOrderQuantity() == minimumOrderQuantity
			&& product.getOrderQuantityStep() == orderQuantityStep
		) return;
		String before = "%d/%d".formatted(product.getMinimumOrderQuantity(), product.getOrderQuantityStep());
		product.updateOrderQuantityRules(minimumOrderQuantity, orderQuantityStep);
		historyRepository.save(history(
			product, null, ProductChangeType.ORDER_QUANTITY, before,
			"%d/%d".formatted(minimumOrderQuantity, orderQuantityStep)
		));
	}

	private void updateProductPrice(Product product, long sourcePrice, long basePrice) {
		if (product.getSourcePrice() == sourcePrice && product.getBasePrice() == basePrice) return;
		String before = "%d/%d".formatted(product.getSourcePrice(), product.getBasePrice());
		product.updateSourcePricing(sourcePrice, basePrice);
		historyRepository.save(history(product, null, ProductChangeType.PRICE, before, "%d/%d".formatted(sourcePrice, basePrice)));
	}

	private void updateOptions(
		Product product,
		List<ProductOption> lockedOptions,
		List<PricedOption> sourceOptions,
		long basePrice
	) {
		Map<String, ProductOption> existingByCode = new HashMap<>();
		for (ProductOption option : lockedOptions) {
			if (option.getSourceOptionCode() != null) existingByCode.put(option.getSourceOptionCode(), option);
		}

		for (PricedOption priced : sourceOptions) {
			DomeggookPurchaseClient.SourceOption source = priced.source();
			ProductOptionStatus status = source.available() ? ProductOptionStatus.ACTIVE : ProductOptionStatus.SOLD_OUT;
			long additionalPrice = MoneyMath.subtractNonNegative(priced.salePrice(), basePrice);
			ProductOption option = existingByCode.remove(source.sourceOptionCode());
			if (option == null) {
				option = optionRepository.save(new ProductOption(
					product, source.name(), additionalPrice, status, source.sourceOptionCode(),
					source.sourceAdditionalPrice(), source.sourceStockQuantity(), source.sortOrder()
				));
				lockedOptions.add(option);
				historyRepository.save(history(product, option, ProductChangeType.OPTION_BASE, null, optionValue(option)));
				continue;
			}

			String before = optionValue(option);
			option.update(
				source.name(), additionalPrice, source.sourceOptionCode(), source.sourceAdditionalPrice(),
				source.sourceStockQuantity(), source.sortOrder()
			);
			String after = optionValue(option);
			if (!before.equals(after)) {
				historyRepository.save(history(product, option, ProductChangeType.OPTION_BASE, before, after));
			}
			if (option.getStatus() != status) {
				ProductOptionStatus oldStatus = option.getStatus();
				option.updateStatus(status);
				historyRepository.save(history(product, option, ProductChangeType.OPTION_STATUS, oldStatus.name(), status.name()));
			}
		}

		for (ProductOption missing : existingByCode.values()) {
			if (missing.getStatus() == ProductOptionStatus.STOPPED) continue;
			ProductOptionStatus oldStatus = missing.getStatus();
			missing.updateStatus(ProductOptionStatus.STOPPED);
			historyRepository.save(history(product, missing, ProductChangeType.OPTION_STATUS, oldStatus.name(), ProductOptionStatus.STOPPED.name()));
		}
	}

	private void updateProductStatus(Product product, ProductStatus status) {
		ProductStatus before = product.getStatus();
		product.updateStatus(status);
		historyRepository.save(history(product, null, ProductChangeType.PRODUCT_STATUS, before.name(), status.name()));
	}

	private ProductChangeHistory history(
		Product product,
		ProductOption option,
		ProductChangeType type,
		String before,
		String after
	) {
		return new ProductChangeHistory(
			product,
			option,
			ProductChangeActor.system(SYSTEM_CODE),
			product.getVersion(),
			product.getVersion() + 1,
			type,
			before,
			after,
			REASON
		);
	}

	private long salePrice(long sourcePrice, long minimumResalePrice, PricingPolicy policy) {
		MoneyMath.requireNonNegative(sourcePrice, "sourcePrice");
		MoneyMath.requireNonNegative(minimumResalePrice, "minimumResalePrice");
		BigDecimal rate = policy == null
			? BigDecimal.valueOf(25)
			: policy.getCommissionRate().add(policy.getTaxBufferRate())
				.add(policy.getOverheadRate()).add(policy.getSafetyMarginRate());
		int unit = policy == null ? 100 : policy.getRoundingUnit();
		try {
			long calculated = BigDecimal.valueOf(sourcePrice)
				.multiply(BigDecimal.valueOf(100).add(rate))
				.divide(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(unit), 0, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(unit))
				.longValueExact();
			long minimum = BigDecimal.valueOf(minimumResalePrice)
				.divide(BigDecimal.valueOf(unit), 0, RoundingMode.CEILING)
				.multiply(BigDecimal.valueOf(unit))
				.longValueExact();
			return MoneyMath.requireCustomerUnitPrice(Math.max(calculated, minimum), "calculated unit price");
		} catch (ArithmeticException exception) {
			throw new IllegalArgumentException("Calculated unit price exceeds the supported range", exception);
		}
	}

	private void markFailed(UUID productId, String expectedSourceItemNo, RuntimeException exception) {
		Product product = lockProduct(productId);
		if (product == null || product.getManagementChannel() != ProductManagementChannel.COREABLE
			|| !Objects.equals(expectedSourceItemNo, product.getSourceItemNo())) return;
		if (supplierRepository != null) optionRepository.findAllByProductIdForUpdate(productId);
		product.markSourceSyncFailed(error(exception), Instant.now());
		historyRepository.save(history(product, null, ProductChangeType.PRODUCT_BASE,
			"sourceSyncError=null", "sourceSyncError=UPSTREAM_FAILURE"));
		product.incrementVersion();
	}

	private Product lockProduct(UUID productId) {
		if (supplierRepository == null) {
			return productRepository.findById(productId)
				.filter(product -> product.getManagementChannel() == ProductManagementChannel.COREABLE)
				.orElse(null);
		}
		UUID supplierId = productRepository.findSupplierIdById(productId).orElse(null);
		if (supplierId == null || supplierRepository.findByIdForUpdate(supplierId).isEmpty()) return null;
		return productRepository.findByIdForUpdate(productId)
			.filter(product -> product.getManagementChannel() == ProductManagementChannel.COREABLE)
			.orElse(null);
	}

	private boolean isSaleReadyForReactivation(Product product, List<ProductOption> options) {
		if (product.getBasePrice() <= 0
			|| product.getBasePrice() > MoneyMath.MAX_CUSTOMER_UNIT_PRICE
			|| !product.getComplianceStatus().allowsSale()
			|| options.stream().noneMatch(option -> option.getStatus() == ProductOptionStatus.ACTIVE)) {
			return false;
		}
		return imageRepository == null || noticeRepository == null
			|| (imageRepository.existsByProduct_IdAndType(product.getId(), ProductImageType.THUMBNAIL)
				&& noticeRepository.existsByProduct_IdAndStatus(product.getId(), ProductNoticeStatus.ACTIVE));
	}

	private String error(RuntimeException exception) {
		String value = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
		return value.length() <= 1000 ? value : value.substring(0, 1000);
	}

	private boolean isTarget(Product product) {
		return product.getManagementChannel() == ProductManagementChannel.COREABLE
			&& (product.getStatus() == ProductStatus.ACTIVE
				|| (product.getStatus() == ProductStatus.SOLD_OUT && product.isSourceAutoSoldOut()));
	}

	private String optionValue(ProductOption option) {
		return "%s/%d/%s/%s/%d".formatted(
			option.getName(), option.getAdditionalPrice(), option.getSourceAdditionalPrice(),
			option.getSourceStockQuantity(), option.getSortOrder()
		);
	}

	private String pricingSnapshot(Product product, List<ProductOption> options, long minimumResalePrice) {
		PricingPolicy policy = product.getPricingPolicyApplied();
		String policyValue = policy == null ? "policy=null" :
			"policyId=%s;policyVersion=%s;commission=%s;taxBuffer=%s;overhead=%s;safetyMargin=%s;rounding=%d;minimumResale=%d"
				.formatted(policy.getId(), product.getPricingPolicyVersionApplied(), policy.getCommissionRate(),
					policy.getTaxBufferRate(), policy.getOverheadRate(), policy.getSafetyMarginRate(),
					policy.getRoundingUnit(), minimumResalePrice);
		return policyValue + ";basePrice=" + product.getBasePrice() + ";options=" + options.stream()
			.map(this::optionValue)
			.toList();
	}

	record SyncResult(UUID productId, boolean available, long sourcePrice, int optionCount) {
	}

	private record PricedOption(DomeggookPurchaseClient.SourceOption source, long salePrice) {
	}
}
