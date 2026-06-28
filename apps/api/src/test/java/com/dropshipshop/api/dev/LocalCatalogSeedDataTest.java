package com.dropshipshop.api.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.repository.ProductDetailBlockRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;

@SpringBootTest(properties = {
	"app.seed.enabled=true",
	"spring.datasource.url=jdbc:h2:mem:local_seed_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.flyway.enabled=false",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"app.catalog.image-storage-path=build/test-product-images"
})
@ActiveProfiles("local")
class LocalCatalogSeedDataTest {

	private final SupplierRepository supplierRepository;
	private final ProductRepository productRepository;
	private final ProductOptionRepository productOptionRepository;
	private final ProductImageRepository productImageRepository;
	private final ProductDetailBlockRepository productDetailBlockRepository;
	private final ProductNoticeRepository productNoticeRepository;
	private final Path imageStoragePath;

	@Autowired
	LocalCatalogSeedDataTest(
		SupplierRepository supplierRepository,
		ProductRepository productRepository,
		ProductOptionRepository productOptionRepository,
		ProductImageRepository productImageRepository,
		ProductDetailBlockRepository productDetailBlockRepository,
		ProductNoticeRepository productNoticeRepository,
		@Value("${app.catalog.image-storage-path}") String imageStoragePath
	) {
		this.supplierRepository = supplierRepository;
		this.productRepository = productRepository;
		this.productOptionRepository = productOptionRepository;
		this.productImageRepository = productImageRepository;
		this.productDetailBlockRepository = productDetailBlockRepository;
		this.productNoticeRepository = productNoticeRepository;
		this.imageStoragePath = Path.of(imageStoragePath);
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
}
