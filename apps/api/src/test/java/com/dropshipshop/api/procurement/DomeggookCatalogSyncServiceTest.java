package com.dropshipshop.api.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.PricingPolicyRepository;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;

class DomeggookCatalogSyncServiceTest {

	private final DomeggookPurchaseClient client = mock(DomeggookPurchaseClient.class);
	private final ProductRepository productRepository = mock(ProductRepository.class);
	private final ProductOptionRepository optionRepository = mock(ProductOptionRepository.class);
	private final ProductChangeHistoryRepository historyRepository = mock(ProductChangeHistoryRepository.class);
	private final PricingPolicyRepository pricingPolicyRepository = mock(PricingPolicyRepository.class);
	private final UUID productId = UUID.randomUUID();
	private Product product;
	private ProductOption option;
	private DomeggookCatalogSyncService service;

	@BeforeEach
	void setUp() {
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		service = new DomeggookCatalogSyncService(
			client, productRepository, optionRepository, historyRepository, pricingPolicyRepository,
			new TransactionTemplate(transactionManager)
		);

		product = new Product(
			new Supplier("supplier", null, null, null, null), "product", "summary",
			1000, 1250, ProductCategory.PPE_SAFETY_HELMET, ProductStatus.ACTIVE
		);
		product.updateSourceItemNo("12345");
		option = new ProductOption(product, "기본", 0, ProductOptionStatus.ACTIVE, "00", 0L, 10L, 0);
		when(productRepository.findById(productId)).thenReturn(Optional.of(product));
		when(optionRepository.findAllByProduct_IdOrderBySortOrderAscCreatedAtAsc(Mockito.nullable(UUID.class)))
			.thenReturn(List.of(option));
		when(pricingPolicyRepository.findFirstByActiveTrueOrderByCreatedAtAsc()).thenReturn(Optional.empty());
	}

	@Test
	void appliesPriceAndSourceAvailabilityIdempotently() {
		DomeggookPurchaseClient.CatalogSnapshot unavailable = new DomeggookPurchaseClient.CatalogSnapshot(
			false, 1200, 2000, 1, 1,
			List.of(new DomeggookPurchaseClient.SourceOption("00", "기본", 100, 0L, false, 0))
		);
		DomeggookPurchaseClient.CatalogSnapshot recovered = new DomeggookPurchaseClient.CatalogSnapshot(
			true, 1200, 2000, 1, 1,
			List.of(new DomeggookPurchaseClient.SourceOption("00", "기본", 100, 5L, true, 0))
		);
		when(client.catalogSnapshot("12345")).thenReturn(unavailable, recovered, recovered);

		service.sync(productId, true);
		assertThat(product.getBasePrice()).isEqualTo(2000);
		assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
		assertThat(product.isSourceAutoSoldOut()).isTrue();
		assertThat(option.getStatus()).isEqualTo(ProductOptionStatus.SOLD_OUT);

		service.sync(productId, true);
		service.sync(productId, true);
		assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
		assertThat(product.getSourceAvailable()).isTrue();
		assertThat(product.isSourceAutoSoldOut()).isFalse();
		assertThat(option.getStatus()).isEqualTo(ProductOptionStatus.ACTIVE);
		verify(historyRepository, Mockito.times(10)).save(any());
	}

	@Test
	void doesNotRecoverManualSoldOutEvenWhenTheLastSourceSnapshotWasUnavailable() {
		product.updateStatus(ProductStatus.SOLD_OUT);
		product.markSourceSynced(false, java.time.Instant.now());
		when(client.catalogSnapshot("12345")).thenReturn(new DomeggookPurchaseClient.CatalogSnapshot(
			true, 1_200, 2_000, 1, 1,
			List.of(new DomeggookPurchaseClient.SourceOption("00", "기본", 100, 5L, true, 0))
		));

		service.sync(productId, true);

		assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
		assertThat(product.getSourceAvailable()).isFalse();
		assertThat(product.isSourceAutoSoldOut()).isFalse();
		verify(historyRepository, never()).save(any());
	}

	@Test
	void updatesOrderQuantityRulesAndHidesMoqOverTen() {
		when(client.catalogSnapshot("12345")).thenReturn(new DomeggookPurchaseClient.CatalogSnapshot(
			true, 1000, 0, 6, 6,
			List.of(new DomeggookPurchaseClient.SourceOption("00", "기본", 0, 10L, true, 0))
		));

		service.sync(productId, true);

		assertThat(product.getMinimumOrderQuantity()).isEqualTo(6);
		assertThat(product.getOrderQuantityStep()).isEqualTo(6);

		when(client.catalogSnapshot("12345")).thenReturn(new DomeggookPurchaseClient.CatalogSnapshot(
			true, 1000, 0, 11, 11,
			List.of(new DomeggookPurchaseClient.SourceOption("00", "기본", 0, 10L, true, 0))
		));
		service.sync(productId, true);

		assertThat(product.getMinimumOrderQuantity()).isEqualTo(11);
		assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
	}

	@Test
	void hidesMoqAboveStoredRangeWithoutCorruptingExistingRules() {
		when(client.catalogSnapshot("12345")).thenReturn(new DomeggookPurchaseClient.CatalogSnapshot(
			true, 1000, 0, 100, 100,
			List.of(new DomeggookPurchaseClient.SourceOption("00", "기본", 0, 10L, true, 0))
		));

		service.sync(productId, true);

		assertThat(product.getMinimumOrderQuantity()).isEqualTo(1);
		assertThat(product.getOrderQuantityStep()).isEqualTo(1);
		assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
	}

	@Test
	void leavesExistingDataAndRecordsTheFailure() {
		when(client.catalogSnapshot("12345")).thenThrow(new DomeggookApiException("429", "rate limited", false));

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(productId, true))
			.isInstanceOf(DomeggookApiException.class);

		assertThat(product.getBasePrice()).isEqualTo(1250);
		assertThat(product.getMinimumOrderQuantity()).isEqualTo(1);
		assertThat(product.getSourceSyncError()).contains("rate limited");
	}

	@Test
	void ignoresSnapshotWhenSourceItemChangesDuringTheUpstreamRequest() {
		when(client.catalogSnapshot("12345")).thenAnswer(invocation -> {
			product.updateSourceItemNo("67890");
			return new DomeggookPurchaseClient.CatalogSnapshot(
				true, 9_000, 0, 1, 1,
				List.of(new DomeggookPurchaseClient.SourceOption("00", "changed", 500, 1L, true, 0))
			);
		});

		service.sync(productId, true);

		assertThat(product.getSourceItemNo()).isEqualTo("67890");
		assertThat(product.getSourcePrice()).isEqualTo(1_000);
		assertThat(product.getBasePrice()).isEqualTo(1_250);
		assertThat(product.getVersion()).isZero();
		verify(historyRepository, never()).save(any());
	}

	@Test
	void doesNotMarkTheNewSourceAsFailedWhenTheOldSourceRequestFails() {
		when(client.catalogSnapshot("12345")).thenAnswer(invocation -> {
			product.updateSourceItemNo("67890");
			throw new DomeggookApiException("429", "old source failed", false);
		});

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sync(productId, true))
			.isInstanceOf(DomeggookApiException.class);

		assertThat(product.getSourceItemNo()).isEqualTo("67890");
		assertThat(product.getSourceSyncError()).isNull();
		assertThat(product.getVersion()).isZero();
		verify(historyRepository, never()).save(any());
	}
}
