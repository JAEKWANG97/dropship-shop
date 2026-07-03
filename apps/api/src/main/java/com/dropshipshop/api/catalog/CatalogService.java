package com.dropshipshop.api.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
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
import com.dropshipshop.api.catalog.domain.PricingPolicy;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.PricingPolicyRepository;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;
import com.dropshipshop.api.catalog.repository.ProductDetailBlockRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.storage.FileStorage;
import com.dropshipshop.api.common.storage.ImageFileValidator;
import com.dropshipshop.api.common.storage.StoredFile;
import com.dropshipshop.api.policy.CustomerPolicyLinkService;

@Service
public class CatalogService {

	private static final Safelist PRODUCT_DETAIL_HTML_SAFELIST = Safelist.none()
		.addTags(
			"p", "br", "b", "strong", "i", "em",
			"ul", "ol", "li",
			"h1", "h2", "h3", "h4",
			"span", "div",
			"table", "tr", "td", "th",
			"a", "img"
		)
		.addAttributes("a", "href")
		.addAttributes("img", "src", "alt")
		.addProtocols("a", "href", "http", "https")
		.addProtocols("img", "src", "http", "https")
		.preserveRelativeLinks(true);
	private static final Document.OutputSettings PRODUCT_DETAIL_HTML_OUTPUT = new Document.OutputSettings()
		.prettyPrint(false);
	private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("5.00");
	private static final BigDecimal DEFAULT_TAX_BUFFER_RATE = new BigDecimal("10.00");
	private static final BigDecimal DEFAULT_OVERHEAD_RATE = new BigDecimal("5.00");
	private static final BigDecimal DEFAULT_SAFETY_MARGIN_RATE = new BigDecimal("5.00");
	private static final int DEFAULT_ROUNDING_UNIT = 100;

	private final SupplierRepository supplierRepository;
	private final ProductRepository productRepository;
	private final PricingPolicyRepository pricingPolicyRepository;
	private final ProductOptionRepository productOptionRepository;
	private final ProductImageRepository productImageRepository;
	private final ProductDetailBlockRepository productDetailBlockRepository;
	private final ProductNoticeRepository productNoticeRepository;
	private final ProductChangeHistoryRepository productChangeHistoryRepository;
	private final CustomerPolicyLinkService customerPolicyLinkService;
	private final FileStorage fileStorage;
	private final ImageFileValidator imageFileValidator;

	public CatalogService(
		SupplierRepository supplierRepository,
		ProductRepository productRepository,
		PricingPolicyRepository pricingPolicyRepository,
		ProductOptionRepository productOptionRepository,
		ProductImageRepository productImageRepository,
		ProductDetailBlockRepository productDetailBlockRepository,
		ProductNoticeRepository productNoticeRepository,
		ProductChangeHistoryRepository productChangeHistoryRepository,
		CustomerPolicyLinkService customerPolicyLinkService,
		FileStorage fileStorage,
		ImageFileValidator imageFileValidator
	) {
		this.supplierRepository = supplierRepository;
		this.productRepository = productRepository;
		this.pricingPolicyRepository = pricingPolicyRepository;
		this.productOptionRepository = productOptionRepository;
		this.productImageRepository = productImageRepository;
		this.productDetailBlockRepository = productDetailBlockRepository;
		this.productNoticeRepository = productNoticeRepository;
		this.productChangeHistoryRepository = productChangeHistoryRepository;
		this.customerPolicyLinkService = customerPolicyLinkService;
		this.fileStorage = fileStorage;
		this.imageFileValidator = imageFileValidator;
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

	@Transactional(readOnly = true)
	public CatalogDtos.PricingPolicyResponse getPricingPolicy() {
		return toPricingPolicyResponse(activePricingPolicy());
	}

	@Transactional
	public CatalogDtos.PricingPolicyResponse updatePricingPolicy(CatalogDtos.PricingPolicyRequest request) {
		PricingPolicy policy = pricingPolicyRepository.findFirstByActiveTrueOrderByCreatedAtAsc()
			.orElseGet(this::defaultPricingPolicy);
		policy.update(
			request.name(),
			request.commissionRate(),
			request.taxBufferRate(),
			request.overheadRate(),
			request.safetyMarginRate(),
			request.roundingUnit()
		);
		return toPricingPolicyResponse(pricingPolicyRepository.save(policy));
	}

	@Transactional
	public CatalogDtos.AdminProductResponse createProduct(CatalogDtos.ProductCreateRequest request) {
		Supplier supplier = findSupplier(request.supplierId());
		Product product = new Product(
			supplier,
			request.name(),
			request.summary(),
			sourcePrice(request.sourcePrice(), request.basePrice()),
			request.basePrice(),
			request.categoryCode(),
			request.status()
		);
		return toAdminProductResponse(productRepository.save(product));
	}

	@Transactional(readOnly = true)
	public CatalogDtos.ProductDetailResponse getAdminProduct(UUID productId) {
		return toProductDetailResponse(findProduct(productId), true);
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
		product.updateBase(supplier, request.name(), request.summary(), sourcePrice(product, request), request.basePrice(), request.categoryCode());
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
		return toProductDetailResponse(product, true);
	}

	@Transactional
	public CatalogDtos.ProductImageUploadResponse uploadImage(UUID productId, MultipartFile file) {
		findProduct(productId);
		String extension = imageFileValidator.validateUpload(file);
		String objectKey = productId + "/" + UUID.randomUUID() + extension;
		StoredFile storedFile;
		try {
			storedFile = fileStorage.store(objectKey, file);
		} catch (RuntimeException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Image upload failed");
		}
		return new CatalogDtos.ProductImageUploadResponse(
			storedFile.url(),
			storedFile.objectKey(),
			storedFile.size(),
			storedFile.contentType()
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
		return toProductDetailResponse(product, true);
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
		return toProductDetailResponse(product, true);
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
		return toProductDetailResponse(product, false);
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
		if (product.getSourcePrice() != sourcePrice(product, request)) {
			recordChange(product, null, adminUserId, ProductChangeType.PRICE,
				"source=" + product.getSourcePrice(), "source=" + sourcePrice(product, request), request.reason());
		}
		if (product.getCategoryCode() != request.categoryCode()) {
			recordChange(product, null, adminUserId, ProductChangeType.PRODUCT_CATEGORY,
				product.getCategoryCode().name(), request.categoryCode().name(), request.reason());
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
		imageFileValidator.validateImageUrl(imageUrl);
	}

	private String sanitizeHtml(String html) {
		if (html == null) {
			return null;
		}
		return Jsoup.clean(html, "https://coreable-saf.com", PRODUCT_DETAIL_HTML_SAFELIST, PRODUCT_DETAIL_HTML_OUTPUT);
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

	private long sourcePrice(Long sourcePrice, long basePrice) {
		return sourcePrice == null ? basePrice : sourcePrice;
	}

	private long sourcePrice(Product product, CatalogDtos.ProductUpdateRequest request) {
		return request.sourcePrice() == null ? product.getSourcePrice() : request.sourcePrice();
	}

	private PricingPolicy activePricingPolicy() {
		return pricingPolicyRepository.findFirstByActiveTrueOrderByCreatedAtAsc()
			.orElse(defaultPricingPolicy());
	}

	private PricingPolicy defaultPricingPolicy() {
		return new PricingPolicy(
			"기본 가격 정책",
			DEFAULT_COMMISSION_RATE,
			DEFAULT_TAX_BUFFER_RATE,
			DEFAULT_OVERHEAD_RATE,
			DEFAULT_SAFETY_MARGIN_RATE,
			DEFAULT_ROUNDING_UNIT
		);
	}

	private BigDecimal totalMarkupRate(PricingPolicy policy) {
		return policy.getCommissionRate()
			.add(policy.getTaxBufferRate())
			.add(policy.getOverheadRate())
			.add(policy.getSafetyMarginRate());
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
			product.getSourcePrice(),
			product.getBasePrice(),
			product.getCategoryCode(),
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
			product.getCategoryCode(),
			product.getStatus(),
			product.getThumbnailImageUrl()
		);
	}

	private CatalogDtos.ProductDetailResponse toProductDetailResponse(Product product, boolean includeSourcePrice) {
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
			includeSourcePrice ? product.getSourcePrice() : null,
			product.getBasePrice(),
			product.getCategoryCode(),
			product.getStatus(),
			product.getThumbnailImageUrl(),
			product.getDetailVersion(),
			notice == null ? null : notice.version(),
			images,
			options,
			blocks,
			notice,
			policyLinks()
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

	private CatalogDtos.PricingPolicyResponse toPricingPolicyResponse(PricingPolicy policy) {
		return new CatalogDtos.PricingPolicyResponse(
			policy.getId(),
			policy.getName(),
			policy.getCommissionRate(),
			policy.getTaxBufferRate(),
			policy.getOverheadRate(),
			policy.getSafetyMarginRate(),
			policy.getRoundingUnit(),
			totalMarkupRate(policy)
		);
	}

	private List<CatalogDtos.PolicyLinkResponse> policyLinks() {
		return customerPolicyLinkService.links().stream()
			.map(link -> new CatalogDtos.PolicyLinkResponse(link.label(), link.href(), link.policyType()))
			.toList();
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
