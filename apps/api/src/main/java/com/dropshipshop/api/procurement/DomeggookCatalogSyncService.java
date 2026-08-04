package com.dropshipshop.api.procurement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.dropshipshop.api.catalog.domain.PricingPolicy;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductChangeHistory;
import com.dropshipshop.api.catalog.domain.ProductChangeType;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.repository.PricingPolicyRepository;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;

@Service
class DomeggookCatalogSyncService {

	private static final UUID SYSTEM_USER_ID = new UUID(0, 0);
	private static final String REASON = "공급처 상품 정기 동기화";

	private final DomeggookPurchaseClient client;
	private final ProductRepository productRepository;
	private final ProductOptionRepository optionRepository;
	private final ProductChangeHistoryRepository historyRepository;
	private final PricingPolicyRepository pricingPolicyRepository;
	private final TransactionTemplate transactionTemplate;

	DomeggookCatalogSyncService(
		DomeggookPurchaseClient client,
		ProductRepository productRepository,
		ProductOptionRepository optionRepository,
		ProductChangeHistoryRepository historyRepository,
		PricingPolicyRepository pricingPolicyRepository,
		TransactionTemplate transactionTemplate
	) {
		this.client = client;
		this.productRepository = productRepository;
		this.optionRepository = optionRepository;
		this.historyRepository = historyRepository;
		this.pricingPolicyRepository = pricingPolicyRepository;
		this.transactionTemplate = transactionTemplate;
	}

	List<UUID> targetProductIds(int batchSize) {
		return transactionTemplate.execute(status -> productRepository.findSourceSyncTargets(PageRequest.of(0, batchSize))
			.stream().map(Product::getId).toList());
	}

	SyncResult sync(UUID productId, boolean apply) {
		String sourceItemNo = transactionTemplate.execute(status -> productRepository.findById(productId)
			.map(Product::getSourceItemNo)
			.orElse(null));
		if (sourceItemNo == null) return new SyncResult(productId, false, 0, 0);

		try {
			DomeggookPurchaseClient.CatalogSnapshot snapshot = client.catalogSnapshot(sourceItemNo);
			if (!apply) return new SyncResult(productId, snapshot.available(), snapshot.sourcePrice(), snapshot.options().size());
			transactionTemplate.executeWithoutResult(status -> apply(productId, snapshot));
			return new SyncResult(productId, snapshot.available(), snapshot.sourcePrice(), snapshot.options().size());
		} catch (RuntimeException exception) {
			if (apply) transactionTemplate.executeWithoutResult(status -> markFailed(productId, exception));
			throw exception;
		}
	}

	private void apply(UUID productId, DomeggookPurchaseClient.CatalogSnapshot snapshot) {
		Product product = productRepository.findById(productId).orElse(null);
		if (product == null || !isTarget(product)) return;

		PricingPolicy policy = pricingPolicyRepository.findFirstByActiveTrueOrderByCreatedAtAsc().orElse(null);
		List<PricedOption> pricedOptions = snapshot.options().stream()
			.map(option -> new PricedOption(option, salePrice(
				snapshot.sourcePrice() + option.sourceAdditionalPrice(), snapshot.minimumResalePrice(), policy
			)))
			.toList();
		long basePrice = pricedOptions.stream().mapToLong(PricedOption::salePrice).min()
			.orElseGet(() -> salePrice(snapshot.sourcePrice(), snapshot.minimumResalePrice(), policy));

		updateProductPrice(product, snapshot.sourcePrice(), basePrice);
		updateOptions(product, pricedOptions, basePrice);

		boolean previouslyUnavailable = Boolean.FALSE.equals(product.getSourceAvailable());
		if (!snapshot.available() && product.getStatus() == ProductStatus.ACTIVE) {
			updateProductStatus(product, ProductStatus.SOLD_OUT);
		} else if (snapshot.available() && product.getStatus() == ProductStatus.SOLD_OUT && previouslyUnavailable) {
			updateProductStatus(product, ProductStatus.ACTIVE);
		}
		product.markSourceSynced(snapshot.available(), Instant.now());
	}

	private void updateProductPrice(Product product, long sourcePrice, long basePrice) {
		if (product.getSourcePrice() == sourcePrice && product.getBasePrice() == basePrice) return;
		String before = "%d/%d".formatted(product.getSourcePrice(), product.getBasePrice());
		product.updateSourcePricing(sourcePrice, basePrice);
		historyRepository.save(history(product, null, ProductChangeType.PRICE, before, "%d/%d".formatted(sourcePrice, basePrice)));
	}

	private void updateOptions(Product product, List<PricedOption> sourceOptions, long basePrice) {
		Map<String, ProductOption> existingByCode = new HashMap<>();
		for (ProductOption option : optionRepository.findAllByProduct_IdOrderBySortOrderAscCreatedAtAsc(product.getId())) {
			if (option.getSourceOptionCode() != null) existingByCode.put(option.getSourceOptionCode(), option);
		}

		for (PricedOption priced : sourceOptions) {
			DomeggookPurchaseClient.SourceOption source = priced.source();
			ProductOptionStatus status = source.available() ? ProductOptionStatus.ACTIVE : ProductOptionStatus.SOLD_OUT;
			long additionalPrice = Math.max(0, priced.salePrice() - basePrice);
			ProductOption option = existingByCode.remove(source.sourceOptionCode());
			if (option == null) {
				option = optionRepository.save(new ProductOption(
					product, source.name(), additionalPrice, status, source.sourceOptionCode(),
					source.sourceAdditionalPrice(), source.sourceStockQuantity(), source.sortOrder()
				));
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
		return new ProductChangeHistory(product, option, SYSTEM_USER_ID, type, before, after, REASON);
	}

	private long salePrice(long sourcePrice, long minimumResalePrice, PricingPolicy policy) {
		BigDecimal rate = policy == null
			? BigDecimal.valueOf(25)
			: policy.getCommissionRate().add(policy.getTaxBufferRate())
				.add(policy.getOverheadRate()).add(policy.getSafetyMarginRate());
		int unit = policy == null ? 100 : policy.getRoundingUnit();
		long calculated = Math.round(sourcePrice * (1 + rate.doubleValue() / 100) / unit) * unit;
		long minimum = ((minimumResalePrice + unit - 1) / unit) * unit;
		return Math.max(calculated, minimum);
	}

	private void markFailed(UUID productId, RuntimeException exception) {
		productRepository.findById(productId).ifPresent(product -> product.markSourceSyncFailed(error(exception), Instant.now()));
	}

	private String error(RuntimeException exception) {
		String value = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
		return value.length() <= 1000 ? value : value.substring(0, 1000);
	}

	private boolean isTarget(Product product) {
		return product.getStatus() == ProductStatus.ACTIVE
			|| (product.getStatus() == ProductStatus.SOLD_OUT && Boolean.FALSE.equals(product.getSourceAvailable()));
	}

	private String optionValue(ProductOption option) {
		return "%s/%d/%s/%s/%d".formatted(
			option.getName(), option.getAdditionalPrice(), option.getSourceAdditionalPrice(),
			option.getSourceStockQuantity(), option.getSortOrder()
		);
	}

	record SyncResult(UUID productId, boolean available, long sourcePrice, int optionCount) {
	}

	private record PricedOption(DomeggookPurchaseClient.SourceOption source, long salePrice) {
	}
}
