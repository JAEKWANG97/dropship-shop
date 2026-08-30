package com.dropshipshop.api.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.dropshipshop.api.catalog.domain.PricingPolicy;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductChangeActorType;
import com.dropshipshop.api.catalog.domain.ProductChangeHistory;
import com.dropshipshop.api.catalog.domain.ProductComplianceStatus;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.pricing.CatalogPriceCalculator;
import com.dropshipshop.api.catalog.repository.PricingPolicyRepository;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;

class DomeggookCatalogSyncProductionPathTest {

	private final DomeggookPurchaseClient client = mock(DomeggookPurchaseClient.class);
	private final ProductRepository productRepository = mock(ProductRepository.class);
	private final ProductOptionRepository optionRepository = mock(ProductOptionRepository.class);
	private final ProductChangeHistoryRepository historyRepository = mock(ProductChangeHistoryRepository.class);
	private final PricingPolicyRepository pricingPolicyRepository = mock(PricingPolicyRepository.class);
	private final SupplierRepository supplierRepository = mock(SupplierRepository.class);
	private final ProductImageRepository imageRepository = mock(ProductImageRepository.class);
	private final ProductNoticeRepository noticeRepository = mock(ProductNoticeRepository.class);
	private final UUID supplierId = UUID.randomUUID();
	private final UUID productId = UUID.randomUUID();
	private final Supplier supplier = new Supplier("supplier", null, null, null, null);
	private DomeggookCatalogSyncService service;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(supplier, "id", supplierId);
		PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
		when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
		service = new DomeggookCatalogSyncService(
			client,
			productRepository,
			optionRepository,
			historyRepository,
			pricingPolicyRepository,
			supplierRepository,
			imageRepository,
			noticeRepository,
			new CatalogPriceCalculator(),
			new TransactionTemplate(transactionManager)
		);
	}

	@Test
	void productionPathLocksAggregateInOrderAndRecordsOneSystemVersion() {
		Product product = coreableProduct();
		ProductOption option = new ProductOption(
			product, "기본", 0, ProductOptionStatus.ACTIVE, "00", 0L, 10L, 0
		);
		ReflectionTestUtils.setField(option, "id", UUID.randomUUID());
		PricingPolicy policy = new PricingPolicy(
			"active", new BigDecimal("5.00"), new BigDecimal("10.00"),
			new BigDecimal("5.00"), new BigDecimal("5.00"), 100
		);
		when(productRepository.findById(productId)).thenReturn(Optional.of(product));
		when(productRepository.findSupplierIdById(productId)).thenReturn(Optional.of(supplierId));
		when(supplierRepository.findByIdForUpdate(supplierId)).thenReturn(Optional.of(supplier));
		when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
		when(optionRepository.findAllByProductIdForUpdate(productId)).thenReturn(List.of(option));
		when(pricingPolicyRepository.findActiveForUpdate()).thenReturn(Optional.of(policy));
		when(imageRepository.existsByProduct_IdAndType(any(), any())).thenReturn(true);
		when(noticeRepository.existsByProduct_IdAndStatus(any(), any())).thenReturn(true);
		when(client.catalogSnapshot("12345")).thenReturn(new DomeggookPurchaseClient.CatalogSnapshot(
			true,
			1200,
			0,
			1,
			1,
			List.of(
				new DomeggookPurchaseClient.SourceOption("00", "기본", 100, 7L, true, 0),
				new DomeggookPurchaseClient.SourceOption("01", "대형", 300, 3L, true, 1)
			)
		));
		when(optionRepository.save(any(ProductOption.class))).thenAnswer(invocation -> {
			ProductOption saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
			return saved;
		});

		DomeggookCatalogSyncService.SyncResult result = service.sync(productId, true);

		assertThat(result.available()).isTrue();
		assertThat(product.getVersion()).isEqualTo(1);
		verify(supplierRepository).findByIdForUpdate(supplierId);
		verify(productRepository).findByIdForUpdate(productId);
		verify(optionRepository).findAllByProductIdForUpdate(productId);

		ArgumentCaptor<ProductChangeHistory> histories = ArgumentCaptor.forClass(ProductChangeHistory.class);
		verify(historyRepository, org.mockito.Mockito.atLeastOnce()).save(histories.capture());
		assertThat(histories.getAllValues()).allSatisfy(history -> {
			assertThat(history.getActorType()).isEqualTo(ProductChangeActorType.SYSTEM);
			assertThat(history.getActorSystemCode()).isEqualTo("DOMEGGOOK_CATALOG_SYNC");
			assertThat(history.getBeforeVersion()).isZero();
			assertThat(history.getAfterVersion()).isEqualTo(1);
		});
		assertThat(histories.getAllValues())
			.anySatisfy(history -> assertThat(history.getAfterValue())
				.contains("policyVersion=1", "대형/400/300/3/1"));
	}

	@Test
	void recoveredSourceDoesNotReactivateAComplianceRejectedProduct() {
		Product product = coreableProduct();
		product.updateStatus(ProductStatus.SOLD_OUT);
		product.markSourceSynced(false, java.time.Instant.now());
		product.markSourceAutoSoldOut();
		product.updateComplianceStatus(ProductComplianceStatus.REJECTED);
		ProductOption option = new ProductOption(
			product, "기본", 0, ProductOptionStatus.SOLD_OUT, "00", 0L, 0L, 0
		);
		ReflectionTestUtils.setField(option, "id", UUID.randomUUID());
		when(productRepository.findById(productId)).thenReturn(Optional.of(product));
		when(productRepository.findSupplierIdById(productId)).thenReturn(Optional.of(supplierId));
		when(supplierRepository.findByIdForUpdate(supplierId)).thenReturn(Optional.of(supplier));
		when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
		when(optionRepository.findAllByProductIdForUpdate(productId)).thenReturn(List.of(option));
		when(pricingPolicyRepository.findActiveForUpdate()).thenReturn(Optional.empty());
		when(imageRepository.existsByProduct_IdAndType(any(), any())).thenReturn(true);
		when(noticeRepository.existsByProduct_IdAndStatus(any(), any())).thenReturn(true);
		when(client.catalogSnapshot("12345")).thenReturn(new DomeggookPurchaseClient.CatalogSnapshot(
			true,
			1_000,
			0,
			1,
			1,
			List.of(new DomeggookPurchaseClient.SourceOption("00", "기본", 0, 10L, true, 0))
		));

		service.sync(productId, true);

		assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
		assertThat(product.getSourceAvailable()).isTrue();
	}

	@Test
	void recoveredSourceDoesNotReactivateAProductMissingRequiredPresentation() {
		Product product = coreableProduct();
		product.updateStatus(ProductStatus.SOLD_OUT);
		product.markSourceSynced(false, java.time.Instant.now());
		product.markSourceAutoSoldOut();
		ProductOption option = new ProductOption(
			product, "기본", 0, ProductOptionStatus.SOLD_OUT, "00", 0L, 0L, 0
		);
		ReflectionTestUtils.setField(option, "id", UUID.randomUUID());
		when(productRepository.findById(productId)).thenReturn(Optional.of(product));
		when(productRepository.findSupplierIdById(productId)).thenReturn(Optional.of(supplierId));
		when(supplierRepository.findByIdForUpdate(supplierId)).thenReturn(Optional.of(supplier));
		when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
		when(optionRepository.findAllByProductIdForUpdate(productId)).thenReturn(List.of(option));
		when(pricingPolicyRepository.findActiveForUpdate()).thenReturn(Optional.empty());
		when(imageRepository.existsByProduct_IdAndType(any(), any())).thenReturn(false);
		when(noticeRepository.existsByProduct_IdAndStatus(any(), any())).thenReturn(true);
		when(client.catalogSnapshot("12345")).thenReturn(new DomeggookPurchaseClient.CatalogSnapshot(
			true,
			1_000,
			0,
			1,
			1,
			List.of(new DomeggookPurchaseClient.SourceOption("00", "기본", 0, 10L, true, 0))
		));

		service.sync(productId, true);

		assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
		assertThat(product.getSourceAvailable()).isTrue();
	}

	@Test
	void recoveredSourceDoesNotReactivateWhenMinimumOrderQuantityExceedsTen() {
		Product product = coreableProduct();
		product.updateStatus(ProductStatus.SOLD_OUT);
		product.markSourceSynced(false, java.time.Instant.now());
		product.markSourceAutoSoldOut();
		ProductOption option = new ProductOption(
			product, "기본", 0, ProductOptionStatus.SOLD_OUT, "00", 0L, 0L, 0
		);
		ReflectionTestUtils.setField(option, "id", UUID.randomUUID());
		when(productRepository.findById(productId)).thenReturn(Optional.of(product));
		when(productRepository.findSupplierIdById(productId)).thenReturn(Optional.of(supplierId));
		when(supplierRepository.findByIdForUpdate(supplierId)).thenReturn(Optional.of(supplier));
		when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
		when(optionRepository.findAllByProductIdForUpdate(productId)).thenReturn(List.of(option));
		when(pricingPolicyRepository.findActiveForUpdate()).thenReturn(Optional.empty());
		when(imageRepository.existsByProduct_IdAndType(any(), any())).thenReturn(true);
		when(noticeRepository.existsByProduct_IdAndStatus(any(), any())).thenReturn(true);
		when(client.catalogSnapshot("12345")).thenReturn(new DomeggookPurchaseClient.CatalogSnapshot(
			true,
			1_000,
			0,
			11,
			11,
			List.of(new DomeggookPurchaseClient.SourceOption("00", "기본", 0, 10L, true, 0))
		));

		service.sync(productId, true);

		assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
		assertThat(product.isSourceAutoSoldOut()).isTrue();
		assertThat(product.getMinimumOrderQuantity()).isEqualTo(11);
		assertThat(product.getSourceAvailable()).isTrue();
	}

	@Test
	void supplierPortalProductNeverCallsUpstreamOrTakesWriterLocks() {
		Product product = new Product(
			supplier,
			"portal",
			"summary",
			1000,
			1200,
			ProductCategory.PPE_WORK_GLOVES,
			ProductStatus.ACTIVE,
			ProductManagementChannel.SUPPLIER_PORTAL
		);
		ReflectionTestUtils.setField(product, "id", productId);
		product.updateSourceItemNo("12345");
		when(productRepository.findById(productId)).thenReturn(Optional.of(product));

		DomeggookCatalogSyncService.SyncResult result = service.sync(productId, true);

		assertThat(result.available()).isFalse();
		assertThat(product.getVersion()).isZero();
		verify(client, never()).catalogSnapshot(any());
		verify(supplierRepository, never()).findByIdForUpdate(any());
		verify(productRepository, never()).findByIdForUpdate(any());
	}

	private Product coreableProduct() {
		Product product = new Product(
			supplier,
			"product",
			"summary",
			1000,
			1250,
			ProductCategory.PPE_SAFETY_HELMET,
			ProductStatus.ACTIVE
		);
		ReflectionTestUtils.setField(product, "id", productId);
		product.updateSourceItemNo("12345");
		return product;
	}
}
