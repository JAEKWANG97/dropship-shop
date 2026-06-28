package com.dropshipshop.api.catalog;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductChangeHistory;
import com.dropshipshop.api.catalog.domain.ProductChangeType;
import com.dropshipshop.api.catalog.domain.ProductDetailBlock;
import com.dropshipshop.api.catalog.domain.ProductDetailBlockType;
import com.dropshipshop.api.catalog.domain.ProductImage;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.domain.ProductNotice;
import com.dropshipshop.api.catalog.domain.ProductNoticeStatus;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;
import com.dropshipshop.api.catalog.repository.ProductDetailBlockRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;

@Service
public class CatalogService {

	private static final Pattern SCRIPT_TAG = Pattern.compile("(?is)<script.*?</script>");
	private static final Pattern EVENT_ATTRIBUTE = Pattern.compile("(?i)\\son[a-z]+\\s*=\\s*(['\"]).*?\\1");
	private static final Pattern JAVASCRIPT_URL = Pattern.compile("(?i)javascript:");
	private static final List<String> ALLOWED_IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp");
	private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
	private static final List<CatalogDtos.PolicyLinkResponse> PRODUCT_POLICY_LINKS = List.of(
		new CatalogDtos.PolicyLinkResponse("배송 정책", "/api/policies/shipping", "SHIPPING_POLICY"),
		new CatalogDtos.PolicyLinkResponse("취소/환불 정책", "/api/policies/cancellation-refund", "CANCELLATION_REFUND_POLICY"),
		new CatalogDtos.PolicyLinkResponse("결제 후 품절 안내", "/api/policies/stock-risk", "OUT_OF_STOCK_NOTICE")
	);

	private final SupplierRepository supplierRepository;
	private final ProductRepository productRepository;
	private final ProductOptionRepository productOptionRepository;
	private final ProductImageRepository productImageRepository;
	private final ProductDetailBlockRepository productDetailBlockRepository;
	private final ProductNoticeRepository productNoticeRepository;
	private final ProductChangeHistoryRepository productChangeHistoryRepository;
	private final Path imageStoragePath;

	public CatalogService(
		SupplierRepository supplierRepository,
		ProductRepository productRepository,
		ProductOptionRepository productOptionRepository,
		ProductImageRepository productImageRepository,
		ProductDetailBlockRepository productDetailBlockRepository,
		ProductNoticeRepository productNoticeRepository,
		ProductChangeHistoryRepository productChangeHistoryRepository,
		@Value("${app.catalog.image-storage-path:build/product-images}") String imageStoragePath
	) {
		this.supplierRepository = supplierRepository;
		this.productRepository = productRepository;
		this.productOptionRepository = productOptionRepository;
		this.productImageRepository = productImageRepository;
		this.productDetailBlockRepository = productDetailBlockRepository;
		this.productNoticeRepository = productNoticeRepository;
		this.productChangeHistoryRepository = productChangeHistoryRepository;
		this.imageStoragePath = Path.of(imageStoragePath);
	}

	@Transactional(readOnly = true)
	public List<CatalogDtos.SupplierResponse> listSuppliers() {
		return supplierRepository.findAll().stream()
			.map(this::toSupplierResponse)
			.toList();
	}

	@Transactional
	public CatalogDtos.SupplierResponse createSupplier(CatalogDtos.SupplierRequest request) {
		Supplier supplier = new Supplier(request.name(), request.contactName(), request.phone(), request.email(), request.memo());
		if (request.status() != null) {
			supplier.update(request.name(), request.contactName(), request.phone(), request.email(), request.memo(), request.status());
		}
		return toSupplierResponse(supplierRepository.save(supplier));
	}

	@Transactional(readOnly = true)
	public CatalogDtos.SupplierResponse getSupplier(UUID supplierId) {
		return toSupplierResponse(findSupplier(supplierId));
	}

	@Transactional
	public CatalogDtos.SupplierResponse updateSupplier(UUID supplierId, CatalogDtos.SupplierRequest request) {
		Supplier supplier = findSupplier(supplierId);
		SupplierStatus status = request.status() == null ? supplier.getStatus() : request.status();
		supplier.update(request.name(), request.contactName(), request.phone(), request.email(), request.memo(), status);
		return toSupplierResponse(supplier);
	}

	@Transactional(readOnly = true)
	public List<CatalogDtos.AdminProductResponse> listAdminProducts() {
		return productRepository.findAllByOrderByCreatedAtDesc().stream()
			.map(this::toAdminProductResponse)
			.toList();
	}

	@Transactional
	public CatalogDtos.AdminProductResponse createProduct(CatalogDtos.ProductCreateRequest request) {
		Supplier supplier = findSupplier(request.supplierId());
		Product product = new Product(supplier, request.name(), request.summary(), request.basePrice(), request.status());
		return toAdminProductResponse(productRepository.save(product));
	}

	@Transactional(readOnly = true)
	public CatalogDtos.ProductDetailResponse getAdminProduct(UUID productId) {
		return toProductDetailResponse(findProduct(productId));
	}

	@Transactional(readOnly = true)
	public CatalogDtos.ProductChangeHistoryListResponse listProductChanges(UUID productId) {
		findProduct(productId);
		return new CatalogDtos.ProductChangeHistoryListResponse(
			productChangeHistoryRepository.findAllByProduct_IdOrderByCreatedAtAsc(productId)
				.stream()
				.map(this::toChangeHistoryResponse)
				.toList()
		);
	}

	@Transactional
	public CatalogDtos.AdminProductResponse updateProduct(
		UUID productId,
		CatalogDtos.ProductUpdateRequest request,
		UUID adminUserId
	) {
		Product product = findProduct(productId);
		Supplier supplier = findSupplier(request.supplierId());
		requireReason(request.reason());
		recordProductBaseChanges(product, supplier, request, adminUserId);
		product.updateBase(supplier, request.name(), request.summary(), request.basePrice());
		return toAdminProductResponse(product);
	}

	@Transactional
	public CatalogDtos.AdminProductResponse updateProductStatus(
		UUID productId,
		CatalogDtos.ProductStatusRequest request,
		UUID adminUserId
	) {
		Product product = findProduct(productId);
		requireReason(request.reason());
		if (product.getStatus() != request.status()) {
			recordChange(product, null, adminUserId, ProductChangeType.PRODUCT_STATUS,
				product.getStatus().name(), request.status().name(), request.reason());
			product.updateStatus(request.status());
		}
		return toAdminProductResponse(product);
	}

	@Transactional
	public CatalogDtos.ProductOptionResponse createOption(UUID productId, CatalogDtos.ProductOptionRequest request) {
		Product product = findProduct(productId);
		ProductOptionStatus status = request.status() == null ? ProductOptionStatus.ACTIVE : request.status();
		ProductOption option = new ProductOption(product, request.name(), request.additionalPrice(), status);
		return toOptionResponse(productOptionRepository.save(option));
	}

	@Transactional
	public CatalogDtos.ProductOptionResponse updateOption(
		UUID productId,
		UUID optionId,
		CatalogDtos.ProductOptionRequest request,
		UUID adminUserId
	) {
		ProductOption option = findOption(productId, optionId);
		requireReason(request.reason());
		if (option.getAdditionalPrice() != request.additionalPrice()) {
			recordChange(option.getProduct(), option, adminUserId, ProductChangeType.PRICE,
				String.valueOf(option.getAdditionalPrice()), String.valueOf(request.additionalPrice()), request.reason());
		}
		if (!Objects.equals(option.getName(), request.name())) {
			recordChange(option.getProduct(), option, adminUserId, ProductChangeType.OPTION_BASE,
				option.getName(), request.name(), request.reason());
		}
		option.update(request.name(), request.additionalPrice());
		return toOptionResponse(option);
	}

	@Transactional
	public CatalogDtos.ProductOptionResponse updateOptionStatus(
		UUID productId,
		UUID optionId,
		CatalogDtos.ProductOptionStatusRequest request,
		UUID adminUserId
	) {
		ProductOption option = findOption(productId, optionId);
		requireReason(request.reason());
		if (option.getStatus() != request.status()) {
			recordChange(option.getProduct(), option, adminUserId, ProductChangeType.OPTION_STATUS,
				option.getStatus().name(), request.status().name(), request.reason());
			option.updateStatus(request.status());
		}
		return toOptionResponse(option);
	}

	@Transactional
	public CatalogDtos.ProductDetailResponse replaceImages(
		UUID productId,
		CatalogDtos.ProductImagesRequest request,
		UUID adminUserId
	) {
		Product product = findProduct(productId);
		requireReason(request.reason());
		validateImages(request.images());
		productImageRepository.deleteAllByProduct_Id(productId);
		List<ProductImage> images = request.images().stream()
			.map(item -> new ProductImage(product, item.type(), item.imageUrl(), item.sortOrder(), item.altText()))
			.toList();
		productImageRepository.saveAll(images);
		product.updateThumbnailImageUrl(thumbnailUrl(images));
		recordChange(product, null, adminUserId, ProductChangeType.IMAGES, null, "replaced", request.reason());
		return toProductDetailResponse(product);
	}

	@Transactional
	public CatalogDtos.ProductImageUploadResponse uploadImage(UUID productId, MultipartFile file) {
		findProduct(productId);
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
		}
		if (file.getSize() > MAX_IMAGE_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is too large");
		}
		String extension = extension(file.getOriginalFilename());
		if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image extension");
		}
		String objectKey = productId + "/" + UUID.randomUUID() + extension;
		Path target = imageStoragePath.resolve(objectKey).normalize();
		try {
			Files.createDirectories(target.getParent());
			file.transferTo(target);
		} catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image upload failed");
		}
		return new CatalogDtos.ProductImageUploadResponse(
			"/uploads/products/" + objectKey,
			objectKey,
			file.getSize(),
			file.getContentType()
		);
	}

	@Transactional
	public CatalogDtos.ProductDetailResponse replaceDetailBlocks(
		UUID productId,
		CatalogDtos.ProductDetailBlocksRequest request,
		UUID adminUserId
	) {
		Product product = findProduct(productId);
		requireReason(request.reason());
		validateDetailBlocks(request.detailBlocks());
		productDetailBlockRepository.deleteAllByProduct_Id(productId);
		List<ProductDetailBlock> blocks = request.detailBlocks().stream()
			.map(item -> new ProductDetailBlock(
				product,
				item.type(),
				item.imageUrl(),
				item.type() == ProductDetailBlockType.HTML ? sanitizeHtml(item.htmlContent()) : null,
				item.sortOrder(),
				item.altText()
			))
			.toList();
		productDetailBlockRepository.saveAll(blocks);
		product.bumpDetailVersion();
		recordChange(product, null, adminUserId, ProductChangeType.DETAIL_BLOCKS, null,
			"detailVersion=" + product.getDetailVersion(), request.reason());
		return toProductDetailResponse(product);
	}

	@Transactional
	public CatalogDtos.ProductDetailResponse updateNotice(
		UUID productId,
		CatalogDtos.ProductNoticeRequest request,
		UUID adminUserId
	) {
		Product product = findProduct(productId);
		requireReason(request.reason());
		int nextVersion = productNoticeRepository.countByProduct_Id(productId) + 1;
		ProductNotice notice = new ProductNotice(
			product,
			nextVersion,
			request.productInfoNotice(),
			request.shippingInfo(),
			request.asInfo(),
			request.returnExchangeInfo()
		);
		productNoticeRepository.save(notice);
		recordChange(product, null, adminUserId, ProductChangeType.NOTICE, null,
			"productNoticeVersion=" + nextVersion, request.reason());
		return toProductDetailResponse(product);
	}

	@Transactional(readOnly = true)
	public List<CatalogDtos.ProductSummaryResponse> listPublicProducts() {
		return productRepository.findAllByStatus(ProductStatus.ACTIVE).stream()
			.map(this::toProductSummaryResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public CatalogDtos.ProductDetailResponse getPublicProduct(UUID productId) {
		Product product = findProduct(productId);
		if (product.getStatus() == ProductStatus.HIDDEN || product.getStatus() == ProductStatus.STOPPED) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
		}
		return toProductDetailResponse(product);
	}

	private void recordProductBaseChanges(
		Product product,
		Supplier newSupplier,
		CatalogDtos.ProductUpdateRequest request,
		UUID adminUserId
	) {
		if (!Objects.equals(product.getSupplier().getId(), newSupplier.getId())) {
			recordChange(product, null, adminUserId, ProductChangeType.SUPPLIER,
				product.getSupplier().getId().toString(), newSupplier.getId().toString(), request.reason());
		}
		if (product.getBasePrice() != request.basePrice()) {
			recordChange(product, null, adminUserId, ProductChangeType.PRICE,
				String.valueOf(product.getBasePrice()), String.valueOf(request.basePrice()), request.reason());
		}
		if (!Objects.equals(product.getName(), request.name()) || !Objects.equals(product.getSummary(), request.summary())) {
			recordChange(product, null, adminUserId, ProductChangeType.PRODUCT_BASE,
				product.getName() + " / " + product.getSummary(),
				request.name() + " / " + request.summary(),
				request.reason());
		}
	}

	private Supplier findSupplier(UUID supplierId) {
		return supplierRepository.findById(supplierId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
	}

	private Product findProduct(UUID productId) {
		return productRepository.findById(productId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
	}

	private ProductOption findOption(UUID productId, UUID optionId) {
		return productOptionRepository.findByIdAndProduct_Id(optionId, productId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product option not found"));
	}

	private void recordChange(
		Product product,
		ProductOption option,
		UUID adminUserId,
		ProductChangeType changeType,
		String beforeValue,
		String afterValue,
		String reason
	) {
		productChangeHistoryRepository.save(new ProductChangeHistory(
			product,
			option,
			adminUserId,
			changeType,
			beforeValue,
			afterValue,
			reason
		));
	}

	private void validateImages(List<CatalogDtos.ProductImageItem> images) {
		long thumbnailCount = images.stream().filter(image -> image.type() == ProductImageType.THUMBNAIL).count();
		long galleryCount = images.stream().filter(image -> image.type() == ProductImageType.GALLERY).count();
		if (thumbnailCount > 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only one thumbnail image is allowed");
		}
		if (galleryCount > 10) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ten gallery images are allowed");
		}
		images.forEach(image -> validateImageUrl(image.imageUrl()));
	}

	private void validateDetailBlocks(List<CatalogDtos.ProductDetailBlockItem> blocks) {
		long imageCount = blocks.stream().filter(block -> block.type() == ProductDetailBlockType.IMAGE).count();
		if (imageCount > 50) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only fifty detail images are allowed");
		}
		for (CatalogDtos.ProductDetailBlockItem block : blocks) {
			if (block.type() == ProductDetailBlockType.IMAGE) {
				if (isBlank(block.imageUrl())) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image block requires imageUrl");
				}
				validateImageUrl(block.imageUrl());
			}
			if (block.type() == ProductDetailBlockType.HTML && isBlank(block.htmlContent())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HTML block requires htmlContent");
			}
		}
	}

	private void validateImageUrl(String imageUrl) {
		String normalized = imageUrl.toLowerCase(Locale.ROOT);
		int queryIndex = normalized.indexOf('?');
		if (queryIndex >= 0) {
			normalized = normalized.substring(0, queryIndex);
		}
		int hashIndex = normalized.indexOf('#');
		if (hashIndex >= 0) {
			normalized = normalized.substring(0, hashIndex);
		}
		boolean allowed = ALLOWED_IMAGE_EXTENSIONS.stream().anyMatch(normalized::endsWith);
		if (!allowed) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image extension");
		}
	}

	private String extension(String filename) {
		if (filename == null) {
			return "";
		}
		int dotIndex = filename.lastIndexOf('.');
		if (dotIndex < 0) {
			return "";
		}
		return filename.substring(dotIndex).toLowerCase(Locale.ROOT);
	}

	private String sanitizeHtml(String html) {
		if (html == null) {
			return null;
		}
		String withoutScripts = SCRIPT_TAG.matcher(html).replaceAll("");
		String withoutEventAttributes = EVENT_ATTRIBUTE.matcher(withoutScripts).replaceAll("");
		return JAVASCRIPT_URL.matcher(withoutEventAttributes).replaceAll("");
	}

	private String thumbnailUrl(List<ProductImage> images) {
		return images.stream()
			.filter(image -> image.getType() == ProductImageType.THUMBNAIL)
			.findFirst()
			.map(ProductImage::getImageUrl)
			.orElse(null);
	}

	private void requireReason(String reason) {
		if (isBlank(reason)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Change reason is required");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private CatalogDtos.SupplierResponse toSupplierResponse(Supplier supplier) {
		return new CatalogDtos.SupplierResponse(
			supplier.getId(),
			supplier.getName(),
			supplier.getContactName(),
			supplier.getPhone(),
			supplier.getEmail(),
			supplier.getMemo(),
			supplier.getStatus()
		);
	}

	private CatalogDtos.AdminProductResponse toAdminProductResponse(Product product) {
		return new CatalogDtos.AdminProductResponse(
			product.getId(),
			product.getSupplier().getId(),
			product.getSupplier().getName(),
			product.getName(),
			product.getSummary(),
			product.getBasePrice(),
			product.getStatus(),
			product.getThumbnailImageUrl(),
			product.getDetailVersion()
		);
	}

	private CatalogDtos.ProductSummaryResponse toProductSummaryResponse(Product product) {
		return new CatalogDtos.ProductSummaryResponse(
			product.getId(),
			product.getName(),
			product.getSummary(),
			product.getBasePrice(),
			product.getStatus(),
			product.getThumbnailImageUrl()
		);
	}

	private CatalogDtos.ProductDetailResponse toProductDetailResponse(Product product) {
		List<CatalogDtos.ProductImageResponse> images = productImageRepository
			.findAllByProduct_IdOrderBySortOrderAsc(product.getId())
			.stream()
			.map(this::toImageResponse)
			.toList();
		List<CatalogDtos.ProductOptionResponse> options = productOptionRepository
			.findAllByProduct_IdOrderByCreatedAtAsc(product.getId())
			.stream()
			.map(this::toOptionResponse)
			.toList();
		List<CatalogDtos.ProductDetailBlockResponse> blocks = productDetailBlockRepository
			.findAllByProduct_IdOrderBySortOrderAsc(product.getId())
			.stream()
			.map(this::toDetailBlockResponse)
			.toList();
		CatalogDtos.ProductNoticeResponse notice = productNoticeRepository
			.findFirstByProduct_IdAndStatusOrderByVersionDesc(product.getId(), ProductNoticeStatus.ACTIVE)
			.map(this::toNoticeResponse)
			.orElse(null);
		return new CatalogDtos.ProductDetailResponse(
			product.getId(),
			product.getName(),
			product.getSummary(),
			product.getBasePrice(),
			product.getStatus(),
			product.getThumbnailImageUrl(),
			product.getDetailVersion(),
			notice == null ? null : notice.version(),
			images,
			options,
			blocks,
			notice,
			PRODUCT_POLICY_LINKS
		);
	}

	private CatalogDtos.ProductOptionResponse toOptionResponse(ProductOption option) {
		return new CatalogDtos.ProductOptionResponse(
			option.getId(),
			option.getName(),
			option.getAdditionalPrice(),
			option.getStatus()
		);
	}

	private CatalogDtos.ProductImageResponse toImageResponse(ProductImage image) {
		return new CatalogDtos.ProductImageResponse(
			image.getId(),
			image.getType(),
			image.getImageUrl(),
			image.getSortOrder(),
			image.getAltText()
		);
	}

	private CatalogDtos.ProductChangeHistoryResponse toChangeHistoryResponse(ProductChangeHistory history) {
		return new CatalogDtos.ProductChangeHistoryResponse(
			history.getId(),
			history.getProductOption() == null ? null : history.getProductOption().getId(),
			history.getAdminUserId(),
			history.getChangeType(),
			history.getBeforeValue(),
			history.getAfterValue(),
			history.getReason(),
			history.getCreatedAt()
		);
	}

	private CatalogDtos.ProductDetailBlockResponse toDetailBlockResponse(ProductDetailBlock block) {
		return new CatalogDtos.ProductDetailBlockResponse(
			block.getId(),
			block.getType(),
			block.getImageUrl(),
			block.getHtmlContent(),
			block.getSortOrder(),
			block.getAltText()
		);
	}

	private CatalogDtos.ProductNoticeResponse toNoticeResponse(ProductNotice notice) {
		return new CatalogDtos.ProductNoticeResponse(
			notice.getId(),
			notice.getVersion(),
			notice.getProductInfoNotice(),
			notice.getShippingInfo(),
			notice.getAsInfo(),
			notice.getReturnExchangeInfo()
		);
	}
}
