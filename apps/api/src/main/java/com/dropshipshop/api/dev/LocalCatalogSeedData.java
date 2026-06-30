package com.dropshipshop.api.dev;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
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
	private final Path imageStoragePath;

	public LocalCatalogSeedData(
		SupplierRepository supplierRepository,
		ProductRepository productRepository,
		ProductOptionRepository productOptionRepository,
		ProductImageRepository productImageRepository,
		ProductDetailBlockRepository productDetailBlockRepository,
		ProductNoticeRepository productNoticeRepository,
		@Value("${app.catalog.image-storage-path:build/product-images}") String imageStoragePath
	) {
		this.supplierRepository = supplierRepository;
		this.productRepository = productRepository;
		this.productOptionRepository = productOptionRepository;
		this.productImageRepository = productImageRepository;
		this.productDetailBlockRepository = productDetailBlockRepository;
		this.productNoticeRepository = productNoticeRepository;
		this.imageStoragePath = Path.of(imageStoragePath).normalize();
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		List<SeedProduct> products = List.of(
			new SeedProduct(0, "K2 안전모 K2-THINK 1", "가볍고 편한 기본형 안전모", 7200, ProductStatus.ACTIVE, ProductCategory.PPE_SAFETY_HELMET, "HELMET", "#ff4d00"),
			new SeedProduct(1, "K2 안전화 K2-67S", "현장 작업용 미끄럼 방지 안전화", 48500, ProductStatus.ACTIVE, ProductCategory.PPE_SAFETY_SHOES, "BOOTS", "#061b49"),
			new SeedProduct(0, "반사 형광조끼 SV-1001", "야간 작업용 고시인성 형광조끼", 5800, ProductStatus.ACTIVE, ProductCategory.PPE_HIGH_VISIBILITY_VEST, "VEST", "#f59e0b"),
			new SeedProduct(2, "3M 컴포트 그립 장갑 CG-100", "정밀 작업용 코팅 안전장갑", 1650, ProductStatus.ACTIVE, ProductCategory.PPE_INSULATED_GLOVES, "GLOVES", "#475569"),
			new SeedProduct(0, "포스탑 추락방지 세트 FS-2020", "고소 작업용 안전벨트 세트", 89000, ProductStatus.ACTIVE, ProductCategory.PPE_FALL_ARREST_HARNESS, "HARNESS", "#0f766e"),
			new SeedProduct(2, "3M 보안경 SF401", "분진과 비산물 차단 보안경", 3300, ProductStatus.ACTIVE, ProductCategory.PPE_SAFETY_GLASSES, "GOGGLES", "#2563eb"),
			new SeedProduct(1, "세이프원 안전모 SW-200", "대량 구매에 적합한 보급형 안전모", 6900, ProductStatus.ACTIVE, ProductCategory.PPE_SAFETY_HELMET, "SAFE", "#1d4ed8"),
			new SeedProduct(1, "지벤 안전화 ZB-186", "장시간 착용용 쿠션 안전화", 52000, ProductStatus.SOLD_OUT, ProductCategory.PPE_SAFETY_SHOES, "ZIBEN", "#111827"),
			new SeedProduct(2, "토와 파워그랩 장갑", "미끄럼 방지 작업 장갑", 2200, ProductStatus.ACTIVE, ProductCategory.PPE_INSULATED_GLOVES, "TOWA", "#2563eb"),
			new SeedProduct(0, "보안경 김서림 방지형", "습한 현장용 안티포그 보안경", 4200, ProductStatus.HIDDEN, ProductCategory.PPE_SAFETY_GLASSES, "FOG", "#64748b")
		);

		products.forEach(this::ensureImages);
		if (supplierRepository.existsByName(SEED_SUPPLIER)) {
			return;
		}

		List<Supplier> suppliers = supplierRepository.saveAll(List.of(
			new Supplier(SEED_SUPPLIER, "홍길동", "02-1234-5678", "safety@safehubpro.co.kr", "local seed supplier"),
			new Supplier("케이투 현장장비", "김현장", "02-2345-6789", "k2@safehubpro.co.kr", "local seed supplier"),
			new Supplier("쓰리엠 보호구", "박보호", "02-3456-7890", "3m@safehubpro.co.kr", "local seed supplier")
		));

		for (SeedProduct seed : products) {
			seedProduct(suppliers.get(seed.supplierIndex()), seed);
		}
	}

	private void seedProduct(Supplier supplier, SeedProduct seed) {
		String imageUrl = image(seed, "thumb", seed.color());
		Product product = productRepository.save(new Product(
			supplier,
			seed.name(),
			seed.summary(),
			seed.basePrice(),
			seed.categoryCode(),
			seed.status()
		));
		product.updateThumbnailImageUrl(imageUrl);

		productOptionRepository.save(new ProductOption(product, "기본", 0, ProductOptionStatus.ACTIVE));
		productOptionRepository.save(new ProductOption(product, "대량 구매", 0, seed.status() == ProductStatus.SOLD_OUT ? ProductOptionStatus.SOLD_OUT : ProductOptionStatus.ACTIVE));
		productImageRepository.save(new ProductImage(product, ProductImageType.THUMBNAIL, imageUrl, 0, seed.name()));
		productImageRepository.save(new ProductImage(product, ProductImageType.GALLERY, image(seed, "dark", "#061b49"), 1, seed.name()));
		productImageRepository.save(new ProductImage(product, ProductImageType.GALLERY, image(seed, "accent", seed.color()), 2, seed.name()));
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

	private void ensureImages(SeedProduct seed) {
		image(seed, "thumb", seed.color());
		image(seed, "dark", "#061b49");
		image(seed, "accent", seed.color());
	}

	private String image(SeedProduct seed, String variant, String color) {
		String objectKey = "local-seed/" + seed.label().toLowerCase(Locale.ROOT) + "-" + variant + ".png";
		Path target = imageStoragePath.resolve(objectKey).normalize();
		try {
			Files.createDirectories(target.getParent());
			if (Files.notExists(target)) {
				ImageIO.write(productImage(seed.label(), Color.decode(color)), "png", target.toFile());
			}
		} catch (Exception exception) {
			throw new IllegalStateException("Failed to create local seed product image", exception);
		}
		return "/uploads/products/" + objectKey;
	}

	private static BufferedImage productImage(String label, Color accent) {
		BufferedImage image = new BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(Color.decode("#f8fafc"));
		graphics.fillRoundRect(0, 0, 640, 640, 40, 40);
		graphics.setColor(Color.decode("#ffffff"));
		graphics.fillRoundRect(88, 72, 464, 464, 36, 36);
		graphics.setColor(Color.decode("#dbe2ee"));
		graphics.setStroke(new BasicStroke(4));
		graphics.drawRoundRect(88, 72, 464, 464, 36, 36);
		graphics.setColor(new Color(6, 27, 73, 24));
		graphics.fillOval(178, 456, 284, 38);
		drawProduct(graphics, label, accent);
		graphics.dispose();
		return image;
	}

	private static void drawProduct(Graphics2D graphics, String label, Color accent) {
		switch (label) {
			case "BOOTS", "ZIBEN" -> drawBoots(graphics, accent);
			case "VEST" -> drawVest(graphics, accent);
			case "GLOVES", "TOWA" -> drawGloves(graphics, accent);
			case "HARNESS" -> drawHarness(graphics, accent);
			case "GOGGLES", "FOG" -> drawGoggles(graphics, accent);
			default -> drawHelmet(graphics, accent);
		}
	}

	private static void drawHelmet(Graphics2D graphics, Color accent) {
		graphics.setColor(accent);
		graphics.fillArc(170, 180, 300, 220, 0, 180);
		graphics.fillRoundRect(144, 294, 352, 52, 26, 26);
		graphics.setColor(Color.decode("#ffffff"));
		graphics.setStroke(new BasicStroke(14));
		graphics.drawArc(226, 204, 188, 136, 0, 180);
		graphics.drawLine(320, 190, 320, 314);
	}

	private static void drawBoots(Graphics2D graphics, Color accent) {
		graphics.setColor(accent);
		Polygon left = new Polygon(new int[] {180, 284, 292, 338, 338, 170}, new int[] {190, 190, 350, 374, 416, 416}, 6);
		graphics.fillPolygon(left);
		Polygon right = new Polygon(new int[] {334, 438, 446, 492, 492, 324}, new int[] {190, 190, 350, 374, 416, 416}, 6);
		graphics.fillPolygon(right);
		graphics.setColor(Color.decode("#ffffff"));
		graphics.fillRoundRect(176, 376, 166, 22, 11, 11);
		graphics.fillRoundRect(330, 376, 166, 22, 11, 11);
	}

	private static void drawVest(Graphics2D graphics, Color accent) {
		graphics.setColor(accent);
		Polygon vest = new Polygon(new int[] {230, 410, 466, 392, 248, 174}, new int[] {160, 160, 440, 470, 470, 440}, 6);
		graphics.fillPolygon(vest);
		graphics.setColor(Color.decode("#ffffff"));
		graphics.setStroke(new BasicStroke(18));
		graphics.drawLine(270, 186, 238, 430);
		graphics.drawLine(370, 186, 402, 430);
		graphics.drawLine(198, 330, 442, 330);
	}

	private static void drawGloves(Graphics2D graphics, Color accent) {
		graphics.setColor(accent);
		graphics.fillRoundRect(194, 230, 118, 206, 58, 58);
		graphics.fillRoundRect(328, 230, 118, 206, 58, 58);
		for (int x = 178; x <= 414; x += 38) {
			graphics.fillRoundRect(x, 174, 34, 138, 17, 17);
		}
		graphics.setColor(Color.decode("#ffffff"));
		graphics.fillRoundRect(198, 386, 244, 24, 12, 12);
	}

	private static void drawHarness(Graphics2D graphics, Color accent) {
		graphics.setColor(accent);
		graphics.setStroke(new BasicStroke(34, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.drawLine(244, 168, 320, 444);
		graphics.drawLine(396, 168, 320, 444);
		graphics.drawLine(218, 294, 422, 294);
		graphics.setStroke(new BasicStroke(18));
		graphics.drawOval(280, 384, 80, 80);
	}

	private static void drawGoggles(Graphics2D graphics, Color accent) {
		graphics.setColor(accent);
		graphics.fillRoundRect(150, 246, 340, 130, 65, 65);
		graphics.setColor(Color.decode("#e0f2fe"));
		graphics.fill(new Ellipse2D.Double(190, 266, 110, 82));
		graphics.fill(new Ellipse2D.Double(340, 266, 110, 82));
		graphics.setColor(Color.decode("#ffffff"));
		graphics.setStroke(new BasicStroke(14));
		graphics.drawLine(300, 306, 340, 306);
	}

	private record SeedProduct(
		int supplierIndex,
		String name,
		String summary,
		long basePrice,
		ProductStatus status,
		ProductCategory categoryCode,
		String label,
		String color
	) {
	}
}
