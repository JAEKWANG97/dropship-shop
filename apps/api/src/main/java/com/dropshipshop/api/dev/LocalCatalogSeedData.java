package com.dropshipshop.api.dev;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductDetailBlock;
import com.dropshipshop.api.catalog.domain.ProductDetailBlockType;
import com.dropshipshop.api.catalog.domain.ProductImage;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.domain.ProductNotice;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductDetailBlockRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;

@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class LocalCatalogSeedData implements ApplicationRunner {

	private static final String SEED_SUPPLIER = "세이프허브 산업안전";

	private final SupplierRepository supplierRepository;
	private final ProductRepository productRepository;
	private final ProductOptionRepository productOptionRepository;
	private final ProductImageRepository productImageRepository;
	private final ProductDetailBlockRepository productDetailBlockRepository;
	private final ProductNoticeRepository productNoticeRepository;

	public LocalCatalogSeedData(
		SupplierRepository supplierRepository,
		ProductRepository productRepository,
		ProductOptionRepository productOptionRepository,
		ProductImageRepository productImageRepository,
		ProductDetailBlockRepository productDetailBlockRepository,
		ProductNoticeRepository productNoticeRepository
	) {
		this.supplierRepository = supplierRepository;
		this.productRepository = productRepository;
		this.productOptionRepository = productOptionRepository;
		this.productImageRepository = productImageRepository;
		this.productDetailBlockRepository = productDetailBlockRepository;
		this.productNoticeRepository = productNoticeRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (supplierRepository.existsByName(SEED_SUPPLIER)) {
			return;
		}

		List<Supplier> suppliers = supplierRepository.saveAll(List.of(
			new Supplier(SEED_SUPPLIER, "홍길동", "02-1234-5678", "safety@safehubpro.co.kr", "local seed supplier"),
			new Supplier("케이투 현장장비", "김현장", "02-2345-6789", "k2@safehubpro.co.kr", "local seed supplier"),
			new Supplier("쓰리엠 보호구", "박보호", "02-3456-7890", "3m@safehubpro.co.kr", "local seed supplier")
		));

		List<SeedProduct> products = List.of(
			new SeedProduct(0, "K2 안전모 K2-THINK 1", "가볍고 편한 기본형 안전모", 7200, ProductStatus.ACTIVE, "HELMET", "#ff4d00"),
			new SeedProduct(1, "K2 안전화 K2-67S", "현장 작업용 미끄럼 방지 안전화", 48500, ProductStatus.ACTIVE, "BOOTS", "#061b49"),
			new SeedProduct(0, "반사 형광조끼 SV-1001", "야간 작업용 고시인성 형광조끼", 5800, ProductStatus.ACTIVE, "VEST", "#f59e0b"),
			new SeedProduct(2, "3M 컴포트 그립 장갑 CG-100", "정밀 작업용 코팅 안전장갑", 1650, ProductStatus.ACTIVE, "GLOVES", "#475569"),
			new SeedProduct(0, "포스탑 추락방지 세트 FS-2020", "고소 작업용 안전벨트 세트", 89000, ProductStatus.ACTIVE, "HARNESS", "#0f766e"),
			new SeedProduct(2, "3M 보안경 SF401", "분진과 비산물 차단 보안경", 3300, ProductStatus.ACTIVE, "GOGGLES", "#2563eb"),
			new SeedProduct(1, "세이프원 안전모 SW-200", "대량 구매에 적합한 보급형 안전모", 6900, ProductStatus.ACTIVE, "SAFE", "#1d4ed8"),
			new SeedProduct(1, "지벤 안전화 ZB-186", "장시간 착용용 쿠션 안전화", 52000, ProductStatus.SOLD_OUT, "ZIBEN", "#111827"),
			new SeedProduct(2, "토와 파워그랩 장갑", "미끄럼 방지 작업 장갑", 2200, ProductStatus.ACTIVE, "TOWA", "#2563eb"),
			new SeedProduct(0, "보안경 김서림 방지형", "습한 현장용 안티포그 보안경", 4200, ProductStatus.HIDDEN, "FOG", "#64748b")
		);

		for (SeedProduct seed : products) {
			seedProduct(suppliers.get(seed.supplierIndex()), seed);
		}
	}

	private void seedProduct(Supplier supplier, SeedProduct seed) {
		String imageUrl = image(seed.label(), seed.color());
		Product product = productRepository.save(new Product(
			supplier,
			seed.name(),
			seed.summary(),
			seed.basePrice(),
			seed.status()
		));
		product.updateThumbnailImageUrl(imageUrl);

		productOptionRepository.save(new ProductOption(product, "기본", 0, ProductOptionStatus.ACTIVE));
		productOptionRepository.save(new ProductOption(product, "대량 구매", 0, seed.status() == ProductStatus.SOLD_OUT ? ProductOptionStatus.SOLD_OUT : ProductOptionStatus.ACTIVE));
		productImageRepository.save(new ProductImage(product, ProductImageType.THUMBNAIL, imageUrl, 0, seed.name()));
		productImageRepository.save(new ProductImage(product, ProductImageType.GALLERY, image(seed.label() + "-A", "#061b49"), 1, seed.name()));
		productImageRepository.save(new ProductImage(product, ProductImageType.GALLERY, image(seed.label() + "-B", seed.color()), 2, seed.name()));
		productDetailBlockRepository.save(new ProductDetailBlock(
			product,
			ProductDetailBlockType.HTML,
			null,
			"<h2>" + seed.name() + "</h2><p>" + seed.summary() + "</p><ul><li>배송비 포함 가격</li><li>세금계산서 발행 가능</li><li>최소주문 수량은 옵션별로 확인</li></ul>",
			1,
			null
		));
		productNoticeRepository.save(new ProductNotice(
			product,
			1,
			"상세 사양은 상품별 공급처 고시를 기준으로 확인합니다.",
			"배송비는 상품 가격에 포함됩니다.",
			"초기 불량과 AS는 고객센터 접수 후 처리합니다.",
			"배송/취소/환불 정책에 따라 처리합니다."
		));
	}

	private static String image(String label, String color) {
		String svg = """
			<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 480">
			  <rect width="480" height="480" rx="28" fill="#f8fafc"/>
			  <circle cx="240" cy="212" r="112" fill="#fff" stroke="#dbe2ee" stroke-width="8"/>
			  <rect x="104" y="324" width="272" height="42" rx="21" fill="%s"/>
			  <text x="240" y="352" text-anchor="middle" font-family="Arial" font-size="24" font-weight="700" fill="#fff">%s</text>
			</svg>
			""".formatted(color, label);
		return "data:image/svg+xml," + URLEncoder.encode(svg, StandardCharsets.UTF_8);
	}

	private record SeedProduct(
		int supplierIndex,
		String name,
		String summary,
		long basePrice,
		ProductStatus status,
		String label,
		String color
	) {
	}
}
