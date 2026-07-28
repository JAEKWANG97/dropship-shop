package com.dropshipshop.api.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductChangeHistory;
import com.dropshipshop.api.catalog.domain.ProductChangeType;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductComplianceStatus;
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
	public CatalogDtos.AdminProductPageResponse listAdminProducts(
		String query,
		ProductStatus status,
		ProductCategory category,
		UUID supplierId,
		CatalogDtos.ProductReadinessFilter readiness,
		int page,
		int size
	) {
		String keyword = normalizeKeyword(query);
		Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
		Page<Product> products = productRepository.findAdminProducts(
			keyword,
			status,
			category,
			supplierId,
			readiness == null ? null : readiness == CatalogDtos.ProductReadinessFilter.READY,
			PageRequest.of(page, size, sort)
		);
		Map<UUID, SaleReadiness> readinessByProductId = saleReadinessByProductId(products.getContent());
		return new CatalogDtos.AdminProductPageResponse(
			products.getContent().stream()
				.map(product -> toAdminProductResponse(product, readinessByProductId.get(product.getId())))
				.toList(),
			products.getNumber(),
			products.getSize(),
			products.getTotalElements(),
			products.getTotalPages()
		);
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
		if (request.status() == ProductStatus.ACTIVE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상품은 HIDDEN으로 등록한 뒤 판매 필수정보를 확인하세요");
		}
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
		product.updateSourceUrl(request.sourceUrl());
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
		product.updateSourceUrl(request.sourceUrl());
		product.updateComplianceStatus(valueOrDefault(request.complianceStatus(), product.getComplianceStatus()));
		validateIfActive(product);
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
			if (request.status() == ProductStatus.ACTIVE) {
				validateSaleReadiness(product);
			}
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
		ProductOption option = new ProductOption(
			product,
			request.name(),
			request.additionalPrice(),
			status,
			request.sourceOptionCode(),
			request.sourceAdditionalPrice(),
			request.sourceStockQuantity(),
			valueOrDefault(request.sortOrder(), 0)
		);
		return toOptionResponse(productOptionRepository.save(option), true);
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
		option.update(
			request.name(),
			request.additionalPrice(),
			valueOrDefault(request.sourceOptionCode(), option.getSourceOptionCode()),
			valueOrDefault(request.sourceAdditionalPrice(), option.getSourceAdditionalPrice()),
			valueOrDefault(request.sourceStockQuantity(), option.getSourceStockQuantity()),
			valueOrDefault(request.sortOrder(), option.getSortOrder())
		);
		return toOptionResponse(option, true);
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
			validateIfActive(option.getProduct());
		}
		return toOptionResponse(option, true);
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
		validateIfActive(product);
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
	public CatalogDtos.PublicProductPageResponse listPublicProducts(
		String query,
		ProductCategory category,
		List<ProductCategory> categories,
		long minPrice,
		Long maxPrice,
		String sort,
		int page,
		int size
	) {
		if (maxPrice != null && minPrice > maxPrice) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minPrice must not exceed maxPrice");
		}
		List<ProductCategory> selectedCategories = category != null
			? List.of(category)
			: categories == null || categories.isEmpty()
				? Arrays.asList(ProductCategory.values())
				: categories.stream().distinct().toList();
		String keyword = normalizeKeyword(query);
		Page<Product> result = productRepository.findPublicProducts(
			keyword,
			selectedCategories,
			minPrice,
			maxPrice,
			PageRequest.of(page, size, publicProductSort(sort))
		);
		Map<ProductCategory, Long> categoryCounts = new EnumMap<>(ProductCategory.class);
		Arrays.stream(ProductCategory.values()).forEach(value -> categoryCounts.put(value, 0L));
		productRepository.countPublicProductsByCategory()
			.forEach(count -> categoryCounts.put(count.getCategoryCode(), count.getProductCount()));
		return new CatalogDtos.PublicProductPageResponse(
			result.getContent().stream().map(this::toProductSummaryResponse).toList(),
			result.getNumber(),
			result.getSize(),
			result.getTotalElements(),
			result.getTotalPages(),
			Map.copyOf(categoryCounts)
		);
	}

	private Sort publicProductSort(String value) {
		return switch (value) {
			case "latest", "" -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
			case "price-asc" -> Sort.by(Sort.Order.asc("basePrice"), Sort.Order.desc("id"));
			case "price-desc" -> Sort.by(Sort.Order.desc("basePrice"), Sort.Order.desc("id"));
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported product sort");
		};
	}

	private String normalizeKeyword(String value) {
		return value == null || value.isBlank()
			? null
			: "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
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
		if (!Objects.equals(product.getSourceUrl(), request.sourceUrl())) {
			recordChange(product, null, adminUserId, ProductChangeType.PRODUCT_BASE,
				product.getSourceUrl(), request.sourceUrl(), request.reason());
		}
		if (request.complianceStatus() != null && product.getComplianceStatus() != request.complianceStatus()) {
			recordChange(product, null, adminUserId, ProductChangeType.COMPLIANCE_STATUS,
				product.getComplianceStatus().name(), request.complianceStatus().name(), request.reason());
		}
	}

	private void validateIfActive(Product product) {
		if (product.getStatus() == ProductStatus.ACTIVE) {
			validateSaleReadiness(product);
		}
	}

	private void validateSaleReadiness(Product product) {
		SaleReadiness readiness = saleReadiness(product);
		if (!readiness.saleReady()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ACTIVE 전환 불가: " + readiness.blockers().stream()
				.map(this::saleBlockerLabel)
				.collect(Collectors.joining(", ")));
		}
	}

	private SaleReadiness saleReadiness(Product product) {
		return saleReadiness(
			product,
			productOptionRepository.findAllByProduct_IdOrderBySortOrderAscCreatedAtAsc(product.getId()).size(),
			productOptionRepository.existsByProduct_IdAndStatus(product.getId(), ProductOptionStatus.ACTIVE),
			productImageRepository.existsByProduct_IdAndType(product.getId(), ProductImageType.THUMBNAIL),
			productNoticeRepository.existsByProduct_IdAndStatus(product.getId(), ProductNoticeStatus.ACTIVE),
			!productDetailBlockRepository.findAllByProduct_IdOrderBySortOrderAsc(product.getId()).isEmpty()
		);
	}

	private SaleReadiness saleReadiness(
		Product product,
		long optionCount,
		boolean hasActiveOption,
		boolean hasThumbnail,
		boolean hasProductNotice,
		boolean hasDetailContent
	) {
		List<CatalogDtos.SaleBlocker> blockers = new ArrayList<>();
		if (product.getBasePrice() <= 0) blockers.add(CatalogDtos.SaleBlocker.BASE_PRICE);
		if (!hasThumbnail) blockers.add(CatalogDtos.SaleBlocker.THUMBNAIL);
		if (!hasActiveOption) blockers.add(CatalogDtos.SaleBlocker.ACTIVE_OPTION);
		if (!hasProductNotice) blockers.add(CatalogDtos.SaleBlocker.PRODUCT_NOTICE);
		if (!product.getComplianceStatus().allowsSale()) blockers.add(CatalogDtos.SaleBlocker.COMPLIANCE);
		return new SaleReadiness(blockers.isEmpty(), List.copyOf(blockers), optionCount, hasThumbnail, hasProductNotice, hasDetailContent);
	}

	private Map<UUID, SaleReadiness> saleReadinessByProductId(List<Product> products) {
		if (products.isEmpty()) return Map.of();
		List<UUID> productIds = products.stream().map(Product::getId).toList();
		Map<UUID, ProductOptionRepository.ProductOptionCounts> optionCounts = productOptionRepository.countByProductIds(productIds)
			.stream()
			.collect(Collectors.toMap(ProductOptionRepository.ProductOptionCounts::getProductId, value -> value));
		Set<UUID> thumbnailProductIds = Set.copyOf(productImageRepository.findProductIdsByType(productIds, ProductImageType.THUMBNAIL));
		Set<UUID> noticeProductIds = Set.copyOf(productNoticeRepository.findProductIdsByStatus(productIds, ProductNoticeStatus.ACTIVE));
		Set<UUID> detailProductIds = Set.copyOf(productDetailBlockRepository.findProductIdsWithDetailContent(productIds));
		Map<UUID, SaleReadiness> result = new HashMap<>();
		for (Product product : products) {
			ProductOptionRepository.ProductOptionCounts counts = optionCounts.get(product.getId());
			result.put(product.getId(), saleReadiness(
				product,
				counts == null ? 0 : counts.getOptionCount(),
				counts != null && counts.getActiveOptionCount() > 0,
				thumbnailProductIds.contains(product.getId()),
				noticeProductIds.contains(product.getId()),
				detailProductIds.contains(product.getId())
			));
		}
		return result;
	}

	private String saleBlockerLabel(CatalogDtos.SaleBlocker blocker) {
		return switch (blocker) {
			case BASE_PRICE -> "판매가";
			case THUMBNAIL -> "대표 이미지";
			case ACTIVE_OPTION -> "판매 가능한 옵션";
			case PRODUCT_NOTICE -> "상품 고시";
			case COMPLIANCE -> "인증 검수";
		};
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

	private String valueOrDefault(String value, String defaultValue) {
		return value == null ? defaultValue : value;
	}

	private Long valueOrDefault(Long value, Long defaultValue) {
		return value == null ? defaultValue : value;
	}

	private int valueOrDefault(Integer value, int defaultValue) {
		return value == null ? defaultValue : value;
	}

	private ProductComplianceStatus valueOrDefault(ProductComplianceStatus value, ProductComplianceStatus defaultValue) {
		return value == null ? defaultValue : value;
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
		return toAdminProductResponse(product, saleReadiness(product));
	}

	private CatalogDtos.AdminProductResponse toAdminProductResponse(Product product, SaleReadiness readiness) {
		return new CatalogDtos.AdminProductResponse(
			product.getId(),
			product.getSupplier().getId(),
			product.getSupplier().getName(),
			product.getName(),
			product.getSummary(),
			product.getSourcePrice(),
			product.getSourceUrl(),
			product.getBasePrice(),
			product.getCategoryCode(),
			product.getStatus(),
			product.getComplianceStatus(),
			product.getThumbnailImageUrl(),
			product.getDetailVersion(),
			readiness.saleReady(),
			readiness.blockers(),
			readiness.optionCount(),
			readiness.hasThumbnail(),
			readiness.hasProductNotice(),
			readiness.hasDetailContent()
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
			.findAllByProduct_IdOrderBySortOrderAscCreatedAtAsc(product.getId())
			.stream()
			.map(option -> toOptionResponse(option, includeSourcePrice))
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
		SaleReadiness readiness = saleReadiness(
			product,
			options.size(),
			options.stream().anyMatch(option -> option.status() == ProductOptionStatus.ACTIVE),
			images.stream().anyMatch(image -> image.type() == ProductImageType.THUMBNAIL),
			notice != null,
			!blocks.isEmpty()
		);
		return new CatalogDtos.ProductDetailResponse(
			product.getId(),
			includeSourcePrice ? product.getSupplier().getId() : null,
			includeSourcePrice ? product.getSupplier().getName() : null,
			product.getName(),
			product.getSummary(),
			includeSourcePrice ? product.getSourcePrice() : null,
			includeSourcePrice ? product.getSourceUrl() : null,
			product.getBasePrice(),
			product.getCategoryCode(),
			product.getStatus(),
			includeSourcePrice ? product.getComplianceStatus() : null,
			product.getThumbnailImageUrl(),
			product.getDetailVersion(),
			notice == null ? null : notice.version(),
			images,
			options,
			blocks,
			notice,
			policyLinks(),
			includeSourcePrice ? readiness.saleReady() : null,
			includeSourcePrice ? readiness.blockers() : null,
			includeSourcePrice ? readiness.optionCount() : null,
			includeSourcePrice ? readiness.hasThumbnail() : null,
			includeSourcePrice ? readiness.hasProductNotice() : null,
			includeSourcePrice ? readiness.hasDetailContent() : null
		);
	}

	private record SaleReadiness(
		boolean saleReady,
		List<CatalogDtos.SaleBlocker> blockers,
		long optionCount,
		boolean hasThumbnail,
		boolean hasProductNotice,
		boolean hasDetailContent
	) {
	}

	private CatalogDtos.ProductOptionResponse toOptionResponse(ProductOption option, boolean includeSourceMetadata) {
		return new CatalogDtos.ProductOptionResponse(
			option.getId(),
			option.getName(),
			option.getAdditionalPrice(),
			option.getStatus(),
			includeSourceMetadata ? option.getSourceOptionCode() : null,
			includeSourceMetadata ? option.getSourceAdditionalPrice() : null,
			includeSourceMetadata ? option.getSourceStockQuantity() : null,
			includeSourceMetadata ? option.getSortOrder() : null
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
