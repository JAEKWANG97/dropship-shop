package com.dropshipshop.api.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductComplianceStatus;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.domain.ProductNoticeStatus;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductDetailBlockRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;

@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.MOCK,
	properties = {
	"app.seed.enabled=true",
	"spring.datasource.url=jdbc:h2:mem:local_seed_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"app.storage.local.upload-dir=build/test-product-images",
	"app.catalog.image-storage-path=build/test-product-images"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalCatalogSeedDataTest {

	private final SupplierRepository supplierRepository;
	private final ProductRepository productRepository;
	private final ProductOptionRepository productOptionRepository;
	private final ProductImageRepository productImageRepository;
	private final ProductDetailBlockRepository productDetailBlockRepository;
	private final ProductNoticeRepository productNoticeRepository;
	private final Path imageStoragePath;
	private final MockMvc mockMvc;
	private final LocalCatalogSeedData seedData;

	@Autowired
	LocalCatalogSeedDataTest(
		SupplierRepository supplierRepository,
		ProductRepository productRepository,
		ProductOptionRepository productOptionRepository,
		ProductImageRepository productImageRepository,
		ProductDetailBlockRepository productDetailBlockRepository,
		ProductNoticeRepository productNoticeRepository,
		@Value("${app.catalog.image-storage-path}") String imageStoragePath,
		MockMvc mockMvc,
		LocalCatalogSeedData seedData
	) {
		this.supplierRepository = supplierRepository;
		this.productRepository = productRepository;
		this.productOptionRepository = productOptionRepository;
		this.productImageRepository = productImageRepository;
		this.productDetailBlockRepository = productDetailBlockRepository;
		this.productNoticeRepository = productNoticeRepository;
		this.imageStoragePath = Path.of(imageStoragePath);
		this.mockMvc = mockMvc;
		this.seedData = seedData;
	}

	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void restoresExistingSeedProductsWithoutTouchingOtherProducts() throws Exception {
		Map<String, UUID> seedProductIds = productRepository.findAll().stream()
			.collect(Collectors.toMap(Product::getName, Product::getId));
		productRepository.findAll().forEach(product -> {
			product.updateStatus(ProductStatus.HIDDEN);
			product.updateComplianceStatus(ProductComplianceStatus.PENDING);
		});
		productRepository.flush();

		Supplier externalSupplier = supplierRepository.save(new Supplier(
			"외부 공급처", "담당자", "02-0000-0000", "external@example.com", "not seed data"
		));
		Product externalProduct = productRepository.saveAndFlush(new Product(
			externalSupplier,
			"사용자 등록 상품",
			"시드 복구 대상이 아닌 상품",
			10000,
			ProductStatus.HIDDEN
		));
		Path thumbnail = imageStoragePath.resolve("local-seed/helmet-thumb.png");
		Files.deleteIfExists(thumbnail);

		seedData.run(new DefaultApplicationArguments(new String[0]));

		Map<String, ProductStatus> expectedStatuses = Map.of(
			LocalCatalogSeedData.PRIMARY_PRODUCT_NAME, ProductStatus.ACTIVE,
			"K2 안전화 K2-67S", ProductStatus.ACTIVE,
			"반사 형광조끼 SV-1001", ProductStatus.ACTIVE,
			"3M 컴포트 그립 장갑 CG-100", ProductStatus.ACTIVE,
			"포스탑 추락방지 세트 FS-2020", ProductStatus.ACTIVE,
			"3M 보안경 SF401", ProductStatus.ACTIVE,
			"세이프원 안전모 SW-200", ProductStatus.ACTIVE,
			"지벤 안전화 ZB-186", ProductStatus.SOLD_OUT,
			"토와 파워그랩 장갑", ProductStatus.ACTIVE,
			"보안경 김서림 방지형", ProductStatus.HIDDEN
		);
		List<Product> restoredSeeds = productRepository.findAll().stream()
			.filter(product -> expectedStatuses.containsKey(product.getName()))
			.toList();
		assertThat(restoredSeeds).hasSize(10);
		restoredSeeds.forEach(product -> {
				assertThat(product.getId()).isEqualTo(seedProductIds.get(product.getName()));
				assertThat(product.getStatus()).isEqualTo(expectedStatuses.get(product.getName()));
				assertThat(product.getComplianceStatus()).isEqualTo(ProductComplianceStatus.NOT_REQUIRED);
			});

		Product primaryProduct = productRepository.findAllByStatus(ProductStatus.ACTIVE).stream()
			.filter(product -> product.getName().equals(LocalCatalogSeedData.PRIMARY_PRODUCT_NAME))
			.findFirst()
			.orElseThrow();
		assertThat(primaryProduct.getBasePrice()).isPositive();
		assertThat(primaryProduct.getThumbnailImageUrl()).isNotBlank();
		assertThat(productOptionRepository.existsByProduct_IdAndStatus(primaryProduct.getId(), ProductOptionStatus.ACTIVE)).isTrue();
		assertThat(productImageRepository.existsByProduct_IdAndType(primaryProduct.getId(), ProductImageType.THUMBNAIL)).isTrue();
		assertThat(productNoticeRepository.existsByProduct_IdAndStatus(primaryProduct.getId(), ProductNoticeStatus.ACTIVE)).isTrue();
		assertThat(Files.exists(thumbnail)).isTrue();
		Product moqProduct = restoredSeeds.stream()
			.filter(product -> product.getName().equals(LocalCatalogSeedData.MOQ_PRODUCT_NAME))
			.findFirst()
			.orElseThrow();
		assertThat(moqProduct.getMinimumOrderQuantity()).isEqualTo(6);
		assertThat(moqProduct.getOrderQuantityStep()).isEqualTo(6);

		Product untouched = productRepository.findById(externalProduct.getId()).orElseThrow();
		assertThat(untouched.getStatus()).isEqualTo(ProductStatus.HIDDEN);
		assertThat(untouched.getComplianceStatus()).isEqualTo(ProductComplianceStatus.PENDING);
		assertThat(productRepository.count()).isEqualTo(11);
		assertThat(productOptionRepository.count()).isEqualTo(20);
		assertThat(productImageRepository.count()).isEqualTo(30);
	}

	@Test
	void seedsLocalCatalogData() {
		assertThat(supplierRepository.count()).isEqualTo(3);
		assertThat(productRepository.count()).isEqualTo(10);
		assertThat(productOptionRepository.count()).isEqualTo(20);
		assertThat(productImageRepository.count()).isEqualTo(30);
		assertThat(productDetailBlockRepository.count()).isEqualTo(10);
		assertThat(productNoticeRepository.count()).isEqualTo(10);
		assertThat(productRepository.findAll())
			.extracting(product -> product.getStatus())
			.contains(ProductStatus.ACTIVE, ProductStatus.SOLD_OUT, ProductStatus.HIDDEN);
		assertThat(productImageRepository.findAll())
			.allSatisfy(image -> {
				assertThat(image.getImageUrl())
					.startsWith("/uploads/products/local-seed/")
					.endsWith(".png");
				String objectKey = image.getImageUrl().substring("/uploads/products/".length());
				assertThat(Files.exists(imageStoragePath.resolve(objectKey))).isTrue();
			});
	}

	@Test
	void servesLocalSeedImagesWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/uploads/products/local-seed/helmet-thumb.png"))
			.andExpect(status().isOk());
	}
}
