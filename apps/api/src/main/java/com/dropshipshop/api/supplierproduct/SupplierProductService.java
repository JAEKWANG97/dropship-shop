package com.dropshipshop.api.supplierproduct;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dropshipshop.api.catalog.cleanup.ProductImageCleanupService;
import com.dropshipshop.api.catalog.domain.PricingPolicy;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductChangeActor;
import com.dropshipshop.api.catalog.domain.ProductChangeHistory;
import com.dropshipshop.api.catalog.domain.ProductChangeType;
import com.dropshipshop.api.catalog.domain.ProductDetailBlock;
import com.dropshipshop.api.catalog.domain.ProductDetailBlockType;
import com.dropshipshop.api.catalog.domain.ProductImage;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductNotice;
import com.dropshipshop.api.catalog.domain.ProductNoticeRow;
import com.dropshipshop.api.catalog.domain.ProductNoticeStatus;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductReviewReasonCode;
import com.dropshipshop.api.catalog.domain.ProductReviewStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.pricing.CatalogPriceCalculator;
import com.dropshipshop.api.catalog.pricing.PricingCalculatorSnapshot;
import com.dropshipshop.api.catalog.pricing.ProductPriceCalculation;
import com.dropshipshop.api.catalog.repository.PricingPolicyRepository;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;
import com.dropshipshop.api.catalog.repository.ProductDetailBlockRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.common.storage.FileStorage;
import com.dropshipshop.api.common.storage.ImageFileValidator;
import com.dropshipshop.api.common.storage.StoredFile;
import com.dropshipshop.api.supplierproduct.repository.SupplierInventoryChangeHistoryRepository;

@Service
public class SupplierProductService {

	private static final Safelist DETAIL_HTML_SAFELIST = Safelist.none()
		.addTags("p", "br", "b", "strong", "i", "em", "ul", "ol", "li", "h1", "h2", "h3", "h4",
			"span", "div", "table", "tr", "td", "th", "a")
		.addAttributes("a", "href")
		.addProtocols("a", "href", "http", "https")
		.preserveRelativeLinks(true);
	private static final Document.OutputSettings DETAIL_HTML_OUTPUT = new Document.OutputSettings().prettyPrint(false);
	private static final int MAX_PRESENTATION_IMAGES = 11;
	private static final int MAX_DETAIL_IMAGES = 50;

	private final SupplierRepository supplierRepository;
	private final ProductRepository productRepository;
	private final ProductOptionRepository optionRepository;
	private final ProductImageRepository imageRepository;
	private final ProductDetailBlockRepository detailBlockRepository;
	private final ProductNoticeRepository noticeRepository;
	private final ProductChangeHistoryRepository historyRepository;
	private final SupplierInventoryChangeHistoryRepository inventoryHistoryRepository;
	private final PricingPolicyRepository pricingPolicyRepository;
	private final CatalogPriceCalculator priceCalculator;
	private final SupplierProductClassifier classifier;
	private final ImageFileValidator imageFileValidator;
	private final FileStorage fileStorage;
	private final ProductImageCleanupService cleanupService;
	private final Clock clock;

	public SupplierProductService(
		SupplierRepository supplierRepository,
		ProductRepository productRepository,
		ProductOptionRepository optionRepository,
		ProductImageRepository imageRepository,
		ProductDetailBlockRepository detailBlockRepository,
		ProductNoticeRepository noticeRepository,
		ProductChangeHistoryRepository historyRepository,
		SupplierInventoryChangeHistoryRepository inventoryHistoryRepository,
		PricingPolicyRepository pricingPolicyRepository,
		CatalogPriceCalculator priceCalculator,
		SupplierProductClassifier classifier,
		ImageFileValidator imageFileValidator,
		FileStorage fileStorage,
		ProductImageCleanupService cleanupService
	) {
		this.supplierRepository = supplierRepository;
		this.productRepository = productRepository;
		this.optionRepository = optionRepository;
		this.imageRepository = imageRepository;
		this.detailBlockRepository = detailBlockRepository;
		this.noticeRepository = noticeRepository;
		this.historyRepository = historyRepository;
		this.inventoryHistoryRepository = inventoryHistoryRepository;
		this.pricingPolicyRepository = pricingPolicyRepository;
		this.priceCalculator = priceCalculator;
		this.classifier = classifier;
		this.imageFileValidator = imageFileValidator;
		this.fileStorage = fileStorage;
		this.cleanupService = cleanupService;
		this.clock = Clock.systemUTC();
	}

	@Transactional(readOnly = true)
	public SupplierProductDtos.ProductListResponse list(UUID userId) {
		Supplier supplier = currentSupplier(userId);
		List<SupplierProductDtos.ProductResponse> products = productRepository
			.findAllBySupplier_IdAndManagementChannel(
				supplier.getId(),
				ProductManagementChannel.SUPPLIER_PORTAL,
				Pageable.unpaged(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
			)
			.getContent().stream()
			.map(this::toResponse)
			.toList();
		return new SupplierProductDtos.ProductListResponse(products);
	}

	@Transactional(readOnly = true)
	public SupplierProductDtos.ProductResponse get(UUID userId, UUID productId) {
		Supplier supplier = currentSupplier(userId);
		Product product = productRepository.findByIdAndSupplier_IdAndManagementChannel(
			productId, supplier.getId(), ProductManagementChannel.SUPPLIER_PORTAL
		).orElseThrow(this::notFound);
		return toResponse(product);
	}

	@Transactional
	public SupplierProductDtos.ProductResponse create(
		UUID userId,
		SupplierProductDtos.ProductCreateRequest request
	) {
		Supplier supplier = lockCurrentSupplier(userId);
		PricingPolicy policy = activePricingPolicyForUpdate();
		ProductPriceCalculation calculation = priceCalculator.calculate(
			request.sourcePrice(), List.of(0L), 0, policy
		);
		long basePrice = calculation.basePrice();
		Product product = productRepository.saveAndFlush(new Product(
			supplier,
			request.name().trim(),
			request.summary().trim(),
			request.sourcePrice(),
			basePrice,
			request.categoryCode(),
			ProductStatus.HIDDEN,
			ProductManagementChannel.SUPPLIER_PORTAL
		));
		product.applyPricing(policy, basePrice);
		ProductOption defaultOption = optionRepository.saveAndFlush(new ProductOption(
			product,
			"기본",
			calculation.options().get(0).additionalPrice(),
			ProductOptionStatus.ACTIVE,
			null,
			0L,
			null,
			0
		));
		historyRepository.save(new ProductChangeHistory(
			product,
			defaultOption,
			ProductChangeActor.supplier(userId, supplier.getId()),
			null,
			product.getVersion(),
			ProductChangeType.PRODUCT_BASE,
			null,
			productSnapshot(product) + ";" + priceState(product, List.of(defaultOption), calculation.snapshot()),
			"SUPPLIER_DRAFT_CREATED"
		));
		return toResponse(product);
	}

	@Transactional
	public SupplierProductDtos.ProductResponse update(
		UUID userId,
		UUID productId,
		SupplierProductDtos.ProductUpdateRequest request
	) {
		LockedProduct locked = lockOwnedProduct(userId, productId);
		Product product = locked.product();
		requireVersion(product, request.expectedVersion());
		requireSupplierEditable(product);
		PricingPolicy policy = activePricingPolicyForUpdate();
		String before = productSnapshot(product) + ";" + priceValuesState(product, locked.options());
		long beforeVersion = product.getVersion();
		invalidateReviewForEdit(product);
		product.updateBase(
			product.getSupplier(),
			request.name().trim(),
			request.summary().trim(),
			request.sourcePrice(),
			product.getBasePrice(),
			request.categoryCode()
		);
		product.updateOrderQuantityRules(request.minimumOrderQuantity(), request.orderQuantityStep());
		ProductPriceCalculation calculation = reprice(product, locked.options(), policy);
		product.incrementVersion();
		recordChange(product, null, userId, beforeVersion, product.getVersion(), ProductChangeType.PRODUCT_BASE,
			before, productSnapshot(product) + ";" + priceState(product, locked.options(), calculation.snapshot()),
			"SUPPLIER_PRODUCT_UPDATED");
		return toResponse(product);
	}

	@Transactional
	public SupplierProductDtos.ProductResponse submit(UUID userId, UUID productId, long expectedVersion) {
		LockedProduct locked = lockOwnedProduct(userId, productId);
		Product product = locked.product();
		requireVersion(product, expectedVersion);
		if (product.getReviewStatus() != ProductReviewStatus.DRAFT
			&& product.getReviewStatus() != ProductReviewStatus.SUPPLEMENT_REQUESTED) {
			throw conflict(ApiErrorCode.CONFLICT, "Product cannot be submitted in its current review state");
		}
		boolean supplementation = product.getReviewStatus() == ProductReviewStatus.SUPPLEMENT_REQUESTED;
		long beforeVersion = product.getVersion();
		String before = reviewSnapshot(product);
		SupplierProductClassifier.Classification result = classifier.classify(
			product,
			imageRepository.existsByProduct_IdAndType(productId, ProductImageType.THUMBNAIL),
			locked.options().stream().anyMatch(option -> option.getStatus() == ProductOptionStatus.ACTIVE),
			noticeRepository.existsByProduct_IdAndStatus(productId, ProductNoticeStatus.ACTIVE),
			supplementation
		);
		product.markFirstSubmitted(Instant.now(clock));
		product.updateReview(result.status(), result.reasonCode(), reviewMessage(result.reasonCode()));
		if (product.getStatus() != ProductStatus.STOPPED) {
			product.updateStatus(result.status() == ProductReviewStatus.AUTO_APPROVED
				? ProductStatus.ACTIVE : ProductStatus.HIDDEN);
		}
		product.incrementVersion();
		recordChange(product, null, userId, beforeVersion, product.getVersion(), ProductChangeType.REVIEW_STATUS,
			before, reviewSnapshot(product), "SUPPLIER_PRODUCT_SUBMITTED");
		return toResponse(product);
	}

	@Transactional
	public SupplierProductDtos.ProductResponse createOption(
		UUID userId,
		UUID productId,
		SupplierProductDtos.OptionRequest request
	) {
		LockedProduct locked = lockOwnedProduct(userId, productId);
		Product product = locked.product();
		requireVersion(product, request.expectedVersion());
		requireSupplierEditable(product);
		PricingPolicy policy = activePricingPolicyForUpdate();
		long beforeVersion = product.getVersion();
		String beforePrices = priceValuesState(product, locked.options());
		invalidateReviewForEdit(product);
		int sortOrder = request.sortOrder() == null ? locked.options().size() : request.sortOrder();
		ProductOption option = optionRepository.saveAndFlush(new ProductOption(
			product,
			request.name().trim(),
			0,
			ProductOptionStatus.ACTIVE,
			normalize(request.sourceOptionCode()),
			request.sourceAdditionalPrice(),
			null,
			sortOrder
		));
		List<ProductOption> options = new java.util.ArrayList<>(locked.options());
		options.add(option);
		ProductPriceCalculation calculation = reprice(product, options, policy);
		product.incrementVersion();
		recordChange(product, option, userId, beforeVersion, product.getVersion(), ProductChangeType.OPTION_BASE,
			beforePrices, optionSnapshot(option) + ";" + priceState(product, options, calculation.snapshot()),
			"SUPPLIER_OPTION_CREATED");
		return toResponse(product);
	}

	@Transactional
	public SupplierProductDtos.ProductResponse updateOption(
		UUID userId,
		UUID productId,
		UUID optionId,
		SupplierProductDtos.OptionRequest request
	) {
		LockedProduct locked = lockOwnedProduct(userId, productId);
		Product product = locked.product();
		requireVersion(product, request.expectedVersion());
		requireSupplierEditable(product);
		ProductOption option = locked.options().stream()
			.filter(candidate -> candidate.getId().equals(optionId))
			.findFirst()
			.orElseThrow(this::notFound);
		PricingPolicy policy = activePricingPolicyForUpdate();
		long beforeVersion = product.getVersion();
		String before = optionSnapshot(option) + ";" + priceValuesState(product, locked.options());
		invalidateReviewForEdit(product);
		option.update(
			request.name().trim(),
			option.getAdditionalPrice(),
			normalize(request.sourceOptionCode()),
			request.sourceAdditionalPrice(),
			option.getSourceStockQuantity(),
			request.sortOrder() == null ? option.getSortOrder() : request.sortOrder()
		);
		ProductPriceCalculation calculation = reprice(product, locked.options(), policy);
		product.incrementVersion();
		recordChange(product, option, userId, beforeVersion, product.getVersion(), ProductChangeType.OPTION_BASE,
			before, optionSnapshot(option) + ";" + priceState(product, locked.options(), calculation.snapshot()),
			"SUPPLIER_OPTION_UPDATED");
		return toResponse(product);
	}

	@Transactional
	public long deleteOption(UUID userId, UUID productId, UUID optionId, long expectedVersion) {
		LockedProduct locked = lockOwnedProduct(userId, productId);
		Product product = locked.product();
		requireVersion(product, expectedVersion);
		requireDraftDeletionState(product);
		if (locked.options().size() <= 1) {
			throw conflict(ApiErrorCode.LAST_OPTION_REQUIRED, "At least one option is required");
		}
		ProductOption option = locked.options().stream()
			.filter(candidate -> candidate.getId().equals(optionId))
			.findFirst()
			.orElseThrow(this::notFound);
		if (productRepository.existsCartReferenceByOptionId(optionId)
			|| productRepository.existsOrderReferenceByOptionId(optionId)) {
			throw conflict(ApiErrorCode.OPTION_REFERENCED, "Option is referenced and cannot be deleted");
		}
		long beforeVersion = product.getVersion();
		product.incrementVersion();
		recordChange(product, option, userId, beforeVersion, product.getVersion(), ProductChangeType.OPTION_DELETED,
			optionSnapshot(option), null, "DRAFT_OPTION_REMOVED");
		historyRepository.flush();
		historyRepository.clearLiveOptionReference(optionId);
		inventoryHistoryRepository.clearLiveOptionReferences(List.of(optionId));
		optionRepository.deleteById(optionId);
		return product.getVersion();
	}

	@Transactional
	public void delete(UUID userId, UUID productId, long expectedVersion) {
		LockedProduct locked = lockOwnedProduct(userId, productId);
		Product product = locked.product();
		requireVersion(product, expectedVersion);
		requireDraftDeletionState(product);
		if (productRepository.existsCartReferenceByProductId(productId)
			|| productRepository.existsOrderReferenceByProductId(productId)
			|| locked.options().stream().anyMatch(option ->
				productRepository.existsCartReferenceByOptionId(option.getId())
					|| productRepository.existsOrderReferenceByOptionId(option.getId()))) {
			throw conflict(ApiErrorCode.PRODUCT_REFERENCED, "Product is referenced and cannot be deleted");
		}
		List<ProductImage> images = imageRepository.findAllByProduct_IdOrderBySortOrderAsc(productId);
		recordChange(product, null, userId, product.getVersion(), null, ProductChangeType.PRODUCT_DELETED,
			productSnapshot(product), null, "DRAFT_ABANDONED");
		historyRepository.flush();
		historyRepository.clearLiveProductReferences(productId);
		inventoryHistoryRepository.clearLiveOptionReferences(
			locked.options().stream().map(ProductOption::getId).toList()
		);
		Instant now = Instant.now(clock);
		images.stream()
			.map(ProductImage::getStorageObjectKey)
			.filter(key -> key != null && !key.isBlank())
			.forEach(key -> cleanupService.enqueueCleanup(key, productId, now));
		detailBlockRepository.deleteAllByProduct_Id(productId);
		imageRepository.deleteAllByProduct_Id(productId);
		optionRepository.deleteAllById(locked.options().stream().map(ProductOption::getId).toList());
		noticeRepository.deleteAllByProduct_Id(productId);
		productRepository.deleteById(productId);
	}

	@Transactional
	public SupplierProductDtos.ProductResponse uploadImage(
		UUID userId,
		UUID productId,
		long expectedVersion,
		ProductImageType type,
		String altText,
		MultipartFile file
	) {
		LockedProduct locked = lockOwnedProduct(userId, productId);
		Product product = locked.product();
		requireVersion(product, expectedVersion);
		requireSupplierEditable(product);
		if (altText != null && altText.length() > 200) {
			throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
				"altText must be at most 200 characters");
		}
		List<ProductImage> existing = imageRepository.findAllByProduct_IdOrderBySortOrderAsc(productId);
		long sameGroupCount = existing.stream()
			.filter(image -> (image.getType() == ProductImageType.DETAIL) == (type == ProductImageType.DETAIL))
			.count();
		if (sameGroupCount >= (type == ProductImageType.DETAIL ? MAX_DETAIL_IMAGES : MAX_PRESENTATION_IMAGES)) {
			throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.BUSINESS_RULE_VIOLATION,
				"Product image count exceeds its type limit");
		}
		if (type == ProductImageType.THUMBNAIL
			&& existing.stream().anyMatch(image -> image.getType() == ProductImageType.THUMBNAIL)) {
			throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.BUSINESS_RULE_VIOLATION,
				"Product must have exactly one thumbnail");
		}
		String extension = imageFileValidator.validateUpload(file);
		String objectKey = "products/%s/%s%s".formatted(productId, UUID.randomUUID(), extension);
		StoredFile stored = fileStorage.store(objectKey, file);
		try {
			invalidateReviewForEdit(product);
			ProductImage image = imageRepository.saveAndFlush(new ProductImage(
				product, type, stored.url(), existing.size(), normalize(altText), stored.objectKey()
			));
			if (type == ProductImageType.THUMBNAIL) {
				product.updateThumbnailImageUrl(image.getImageUrl());
			}
			long beforeVersion = product.getVersion();
			product.incrementVersion();
			recordChange(product, null, userId, beforeVersion, product.getVersion(), ProductChangeType.IMAGES,
				null, imageSnapshot(image), "SUPPLIER_IMAGE_UPLOADED");
			return toResponse(product);
		} catch (RuntimeException exception) {
			fileStorage.delete(stored.objectKey());
			throw exception;
		}
	}

	@Transactional
	public long deleteImage(UUID userId, UUID productId, UUID imageId, long expectedVersion) {
		LockedProduct locked = lockOwnedProduct(userId, productId);
		Product product = locked.product();
		requireVersion(product, expectedVersion);
		requireSupplierEditable(product);
		ProductImage image = imageRepository.findByIdAndProduct_Id(imageId, productId).orElseThrow(this::notFound);
		if (detailBlockRepository.existsByProductImage_Id(imageId)) {
			throw conflict(ApiErrorCode.DETAIL_IMAGE_REFERENCED, "Detail image is referenced");
		}
		List<ProductImage> images = imageRepository.findAllByProduct_IdOrderBySortOrderAsc(productId);
		long presentationImageCount = images.stream()
			.filter(candidate -> candidate.getType() != ProductImageType.DETAIL)
			.count();
		if (image.getType() == ProductImageType.THUMBNAIL && presentationImageCount > 1) {
			throw conflict(ApiErrorCode.CONFLICT, "Select another thumbnail before deleting this image");
		}
		invalidateReviewForEdit(product);
		if (image.getStorageObjectKey() != null) {
			cleanupService.enqueueCleanup(image.getStorageObjectKey(), productId, Instant.now(clock));
		}
		imageRepository.delete(image);
		if (image.getType() == ProductImageType.THUMBNAIL) {
			product.updateThumbnailImageUrl(null);
		}
		long beforeVersion = product.getVersion();
		product.incrementVersion();
		recordChange(product, null, userId, beforeVersion, product.getVersion(), ProductChangeType.IMAGES,
			imageSnapshot(image), null, "SUPPLIER_IMAGE_DELETED");
		return product.getVersion();
	}

	@Transactional
	public SupplierProductDtos.ProductResponse reorderImages(
		UUID userId,
		UUID productId,
		SupplierProductDtos.ImageOrderRequest request
	) {
		LockedProduct locked = lockOwnedProduct(userId, productId);
		Product product = locked.product();
		requireVersion(product, request.expectedVersion());
		requireSupplierEditable(product);
		List<ProductImage> images = imageRepository.findAllByProduct_IdOrderBySortOrderAsc(productId);
		List<ProductImage> presentationImages = images.stream()
			.filter(image -> image.getType() != ProductImageType.DETAIL)
			.toList();
		Map<UUID, ProductImage> byId = new HashMap<>();
		presentationImages.forEach(image -> byId.put(image.getId(), image));
		Set<UUID> requestedIds = new HashSet<>();
		for (SupplierProductDtos.ImageOrderItem item : request.images()) {
			if (item.type() == ProductImageType.DETAIL
				|| !requestedIds.add(item.imageId()) || !byId.containsKey(item.imageId())) {
				throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
					"Image order must contain each owned image exactly once");
			}
		}
		if (requestedIds.size() != presentationImages.size()
			|| request.images().stream().filter(item -> item.type() == ProductImageType.THUMBNAIL).count() != 1) {
			throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
				"Image order must contain each owned image and exactly one thumbnail");
		}
		ProductImage thumbnail = request.images().stream()
			.filter(item -> item.type() == ProductImageType.THUMBNAIL)
			.map(item -> byId.get(item.imageId()))
			.findFirst().orElseThrow();
		ProductImage currentThumbnail = presentationImages.stream()
			.filter(image -> image.getType() == ProductImageType.THUMBNAIL)
			.findFirst().orElse(null);
		if (currentThumbnail != null && !currentThumbnail.getId().equals(thumbnail.getId())) {
			SupplierProductDtos.ImageOrderItem demoted = request.images().stream()
				.filter(item -> item.imageId().equals(currentThumbnail.getId()))
				.findFirst().orElseThrow();
			currentThumbnail.updatePresentation(demoted.type(), demoted.sortOrder(), normalize(demoted.altText()));
			imageRepository.flush();
		}
		for (SupplierProductDtos.ImageOrderItem item : request.images()) {
			ProductImage image = byId.get(item.imageId());
			image.updatePresentation(item.type(), item.sortOrder(), normalize(item.altText()));
		}
		invalidateReviewForEdit(product);
		product.updateThumbnailImageUrl(thumbnail.getImageUrl());
		long beforeVersion = product.getVersion();
		product.incrementVersion();
		recordChange(product, null, userId, beforeVersion, product.getVersion(), ProductChangeType.IMAGES,
			null, "imageCount=" + presentationImages.size(), "SUPPLIER_IMAGES_REORDERED");
		return toResponse(product);
	}

	@Transactional
	public SupplierProductDtos.ProductResponse replaceDetailBlocks(
		UUID userId,
		UUID productId,
		SupplierProductDtos.DetailBlocksRequest request
	) {
		LockedProduct locked = lockOwnedProduct(userId, productId);
		Product product = locked.product();
		requireVersion(product, request.expectedVersion());
		requireSupplierEditable(product);
		List<ProductDetailBlock> replacements = request.detailBlocks().stream()
			.map(item -> detailBlock(product, item))
			.toList();
		invalidateReviewForEdit(product);
		detailBlockRepository.deleteAllByProduct_Id(productId);
		detailBlockRepository.saveAll(replacements);
		product.bumpDetailVersion();
		long beforeVersion = product.getVersion();
		product.incrementVersion();
		recordChange(product, null, userId, beforeVersion, product.getVersion(), ProductChangeType.DETAIL_BLOCKS,
			null, "detailBlockCount=" + replacements.size(), "SUPPLIER_DETAIL_UPDATED");
		return toResponse(product);
	}

	@Transactional
	public SupplierProductDtos.ProductResponse updateNotice(
		UUID userId,
		UUID productId,
		SupplierProductDtos.NoticeRequest request
	) {
		LockedProduct locked = lockOwnedProduct(userId, productId);
		Product product = locked.product();
		requireVersion(product, request.expectedVersion());
		requireSupplierEditable(product);
		int noticeVersion = noticeRepository.countByProduct_Id(productId) + 1;
		ProductNotice notice = new ProductNotice(
			product,
			noticeVersion,
			request.productInfoNotice(),
			request.shippingInfo(),
			request.asInfo(),
			request.returnExchangeInfo(),
			request.noticeRows().stream().map(row -> new ProductNoticeRow(row.label(), row.value())).toList()
		);
		invalidateReviewForEdit(product);
		noticeRepository.save(notice);
		long beforeVersion = product.getVersion();
		product.incrementVersion();
		recordChange(product, null, userId, beforeVersion, product.getVersion(), ProductChangeType.NOTICE,
			null, "productNoticeVersion=" + noticeVersion, "SUPPLIER_NOTICE_UPDATED");
		return toResponse(product);
	}

	private LockedProduct lockOwnedProduct(UUID userId, UUID productId) {
		Supplier supplier = lockCurrentSupplier(userId);
		Product product = productRepository.findByIdAndSupplierIdAndManagementChannelForUpdate(
			productId, supplier.getId(), ProductManagementChannel.SUPPLIER_PORTAL
		).orElseThrow(this::notFound);
		List<ProductOption> options = optionRepository.findAllByProductIdForUpdate(productId);
		return new LockedProduct(supplier, product, options, userId);
	}

	private Supplier currentSupplier(UUID userId) {
		return supplierRepository.findByManagerUserId(userId)
			.filter(supplier -> supplier.isPortalAuthorityActive(Instant.now(clock)))
			.orElseThrow(this::notFound);
	}

	private Supplier lockCurrentSupplier(UUID userId) {
		return supplierRepository.findByManagerUserIdForUpdate(userId)
			.filter(supplier -> supplier.isPortalAuthorityActive(Instant.now(clock)))
			.orElseThrow(this::notFound);
	}

	private PricingPolicy activePricingPolicyForUpdate() {
		return pricingPolicyRepository.findActiveForUpdate()
			.orElseGet(() -> pricingPolicyRepository.saveAndFlush(new PricingPolicy(
				"Default Pricing Policy",
				new BigDecimal("5.00"),
				new BigDecimal("10.00"),
				new BigDecimal("5.00"),
				new BigDecimal("5.00"),
				100
			)));
	}

	private ProductPriceCalculation reprice(Product product, List<ProductOption> options, PricingPolicy policy) {
		List<Long> sourceAdditionalPrices = options.stream()
			.map(option -> option.getSourceAdditionalPrice() == null ? 0L : option.getSourceAdditionalPrice())
			.toList();
		ProductPriceCalculation calculation = priceCalculator.calculate(
			product.getSourcePrice(), sourceAdditionalPrices, 0, policy
		);
		product.applyPricing(policy, calculation.basePrice());
		for (int index = 0; index < options.size(); index++) {
			ProductOption option = options.get(index);
			option.update(
				option.getName(),
				calculation.options().get(index).additionalPrice(),
				option.getSourceOptionCode(),
				option.getSourceAdditionalPrice(),
				option.getSourceStockQuantity(),
				option.getSortOrder()
			);
		}
		return calculation;
	}

	private ProductDetailBlock detailBlock(Product product, SupplierProductDtos.DetailBlockItem item) {
		if (item.type() == ProductDetailBlockType.IMAGE) {
			if (item.productImageId() == null || item.htmlContent() != null) {
				throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
					"IMAGE detail block requires only productImageId");
			}
			ProductImage image = imageRepository.findByIdAndProduct_Id(item.productImageId(), product.getId())
				.filter(candidate -> candidate.getType() == ProductImageType.DETAIL)
				.orElseThrow(this::notFound);
			return new ProductDetailBlock(product, image, item.sortOrder(), normalize(item.altText()));
		}
		if (item.productImageId() != null || item.htmlContent() == null || item.htmlContent().isBlank()) {
			throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
				"HTML detail block requires only htmlContent");
		}
		return new ProductDetailBlock(
			product,
			ProductDetailBlockType.HTML,
			null,
			sanitizeHtml(item.htmlContent()),
			item.sortOrder(),
			normalize(item.altText())
		);
	}

	private String sanitizeHtml(String html) {
		return Jsoup.clean(html, "", DETAIL_HTML_SAFELIST, DETAIL_HTML_OUTPUT);
	}

	private void requireVersion(Product product, long expectedVersion) {
		if (!product.hasVersion(expectedVersion)) {
			throw conflict(ApiErrorCode.PRODUCT_VERSION_CONFLICT, "Product version is stale");
		}
	}

	private void requireSupplierEditable(Product product) {
		if (product.getReviewStatus() == ProductReviewStatus.REJECTED) {
			throw conflict(ApiErrorCode.CONFLICT, "Rejected product must be handled by Coreable");
		}
		if ((product.getReviewStatus() == ProductReviewStatus.AUTO_APPROVED
			|| product.getReviewStatus() == ProductReviewStatus.APPROVED)
			&& product.getStatus() != ProductStatus.ACTIVE) {
			throw conflict(ApiErrorCode.CONFLICT, "Coreable-paused product cannot be edited by the supplier");
		}
	}

	private void requireDraftDeletionState(Product product) {
		if (product.getFirstSubmittedAt() != null) {
			throw conflict(ApiErrorCode.PRODUCT_ALREADY_SUBMITTED, "Submitted product cannot be deleted");
		}
		if (product.getReviewStatus() != ProductReviewStatus.DRAFT) {
			throw conflict(ApiErrorCode.PRODUCT_NOT_DRAFT, "Only a draft product can be deleted");
		}
	}

	private void invalidateReviewForEdit(Product product) {
		if (product.getReviewStatus() == ProductReviewStatus.REJECTED) {
			throw conflict(ApiErrorCode.CONFLICT, "Rejected product must be handled by Coreable");
		}
		if (product.getReviewStatus() != ProductReviewStatus.DRAFT
			&& product.getReviewStatus() != ProductReviewStatus.SUPPLEMENT_REQUESTED) {
			product.updateReview(ProductReviewStatus.DRAFT, null, null);
		}
		if (product.getStatus() != ProductStatus.STOPPED) {
			product.updateStatus(ProductStatus.HIDDEN);
		}
	}

	private void recordChange(
		Product product,
		ProductOption option,
		UUID userId,
		Long beforeVersion,
		Long afterVersion,
		ProductChangeType type,
		String before,
		String after,
		String reason
	) {
		historyRepository.save(new ProductChangeHistory(
			product,
			option,
			ProductChangeActor.supplier(userId, product.getSupplier().getId()),
			beforeVersion,
			afterVersion,
			type,
			before,
			after,
			reason
		));
	}

	private SupplierProductDtos.ProductResponse toResponse(Product product) {
		List<ProductOption> options = optionRepository.findAllByProduct_IdOrderBySortOrderAscCreatedAtAsc(product.getId());
		List<ProductImage> images = imageRepository.findAllByProduct_IdOrderBySortOrderAsc(product.getId());
		List<ProductDetailBlock> blocks = detailBlockRepository.findAllByProduct_IdOrderBySortOrderAsc(product.getId());
		ProductNotice notice = noticeRepository
			.findFirstByProduct_IdAndStatusOrderByVersionDesc(product.getId(), ProductNoticeStatus.ACTIVE)
			.orElse(null);
		boolean draftDeletable = product.getReviewStatus() == ProductReviewStatus.DRAFT
			&& product.getFirstSubmittedAt() == null;
		boolean productDeletable = draftDeletable
			&& !productRepository.existsCartReferenceByProductId(product.getId())
			&& !productRepository.existsOrderReferenceByProductId(product.getId())
				&& options.stream().noneMatch(option -> productRepository.existsCartReferenceByOptionId(option.getId())
					|| productRepository.existsOrderReferenceByOptionId(option.getId()));
		long presentationImageCount = images.stream()
			.filter(image -> image.getType() != ProductImageType.DETAIL)
			.count();
		Set<UUID> referencedImageIds = blocks.stream()
			.filter(block -> block.getProductImage() != null)
			.map(block -> block.getProductImage().getId())
			.collect(java.util.stream.Collectors.toSet());
		return new SupplierProductDtos.ProductResponse(
			product.getId(),
			product.getVersion(),
			product.getName(),
			product.getSummary(),
			product.getSourcePrice(),
			product.getMinimumOrderQuantity(),
			product.getOrderQuantityStep(),
			product.getCategoryCode(),
			displayStatus(product),
			product.getReviewReasonCode(),
			product.getSupplierReviewMessage(),
			nextAction(product),
			productDeletable,
			product.getFirstSubmittedAt(),
			options.stream().map(option -> new SupplierProductDtos.OptionResponse(
				option.getId(),
				option.getName(),
				option.getSourceOptionCode(),
					option.getSourceAdditionalPrice() == null ? 0 : option.getSourceAdditionalPrice(),
					option.getSortOrder(),
					draftDeletable && options.size() > 1
						&& !productRepository.existsCartReferenceByOptionId(option.getId())
						&& !productRepository.existsOrderReferenceByOptionId(option.getId()),
					option.getInventoryVersion(),
					option.getSupplierAvailability(),
					option.getInventoryMode(),
					option.getOnHandQuantity(),
					option.getReservedQuantity(),
					option.isTracked() ? option.getAvailableQuantity() : null
					)).toList(),
				images.stream().map(image -> new SupplierProductDtos.ImageResponse(
					image.getId(), image.getType(), image.getImageUrl(), image.getSortOrder(), image.getAltText(),
					!referencedImageIds.contains(image.getId())
						&& (image.getType() != ProductImageType.THUMBNAIL || presentationImageCount == 1)
				)).toList(),
			blocks.stream().map(block -> new SupplierProductDtos.DetailBlockResponse(
				block.getId(),
				block.getType(),
				block.getProductImage() == null ? null : block.getProductImage().getId(),
				block.getHtmlContent(),
				block.getSortOrder(),
				block.getAltText()
			)).toList(),
			notice == null ? null : new SupplierProductDtos.NoticeResponse(
				notice.getId(),
				notice.getVersion(),
				notice.getProductInfoNotice(),
				notice.getShippingInfo(),
				notice.getAsInfo(),
				notice.getReturnExchangeInfo(),
				notice.getNoticeRows().stream()
					.map(row -> new SupplierProductDtos.NoticeRowItem(row.label(), row.value()))
					.toList()
			)
		);
	}

	private SupplierProductDtos.SupplierDisplayStatus displayStatus(Product product) {
		if (product.getReviewStatus() == null) {
			return SupplierProductDtos.SupplierDisplayStatus.PAUSED_BY_COREABLE;
		}
		if ((product.getReviewStatus() == ProductReviewStatus.AUTO_APPROVED
			|| product.getReviewStatus() == ProductReviewStatus.APPROVED)
			&& product.getStatus() != ProductStatus.ACTIVE) {
			return SupplierProductDtos.SupplierDisplayStatus.PAUSED_BY_COREABLE;
		}
		return switch (product.getReviewStatus()) {
			case DRAFT -> SupplierProductDtos.SupplierDisplayStatus.EDITING;
			case AUTO_APPROVED, APPROVED -> SupplierProductDtos.SupplierDisplayStatus.APPROVED;
			case REVIEW_REQUIRED -> SupplierProductDtos.SupplierDisplayStatus.UNDER_REVIEW;
			case SUPPLEMENT_REQUESTED -> SupplierProductDtos.SupplierDisplayStatus.CHANGES_REQUESTED;
			case REJECTED -> SupplierProductDtos.SupplierDisplayStatus.REJECTED;
		};
	}

	private SupplierProductDtos.SupplierNextAction nextAction(Product product) {
		if (displayStatus(product) == SupplierProductDtos.SupplierDisplayStatus.PAUSED_BY_COREABLE) {
			return SupplierProductDtos.SupplierNextAction.CONTACT_COREABLE;
		}
		return switch (product.getReviewStatus()) {
			case DRAFT, SUPPLEMENT_REQUESTED -> SupplierProductDtos.SupplierNextAction.EDIT_AND_RESUBMIT;
			case REVIEW_REQUIRED -> SupplierProductDtos.SupplierNextAction.WAIT;
			case REJECTED -> SupplierProductDtos.SupplierNextAction.CONTACT_COREABLE;
			case AUTO_APPROVED, APPROVED -> SupplierProductDtos.SupplierNextAction.NONE;
		};
	}

	private String reviewMessage(ProductReviewReasonCode reasonCode) {
		if (reasonCode == null) {
			return null;
		}
		return switch (reasonCode) {
			case REQUIRED_INFO_MISSING -> "필수 상품 정보를 확인해 주세요.";
			case CERTIFICATION_REVIEW -> "상품 인증 정보를 Coreable에서 확인하고 있습니다.";
			case CATEGORY_REVIEW, SAFETY_REVIEW -> "상품 판매 가능 여부를 Coreable에서 확인하고 있습니다.";
			case SUPPLEMENT_REQUIRED -> "요청된 상품 정보를 보완해 주세요.";
			case REJECTED_POLICY -> "현재 판매 정책상 등록할 수 없는 상품입니다.";
		};
	}

	private String productSnapshot(Product product) {
		return "name=%s;sourcePrice=%d;basePrice=%d;category=%s;moq=%d;step=%d;status=%s;review=%s"
			.formatted(product.getName(), product.getSourcePrice(), product.getBasePrice(), product.getCategoryCode(),
				product.getMinimumOrderQuantity(), product.getOrderQuantityStep(), product.getStatus(), product.getReviewStatus());
	}

	private String optionSnapshot(ProductOption option) {
		return "name=%s;sourceOptionCode=%s;sourceAdditionalPrice=%s;additionalPrice=%d;sortOrder=%d;status=%s"
			.formatted(option.getName(), option.getSourceOptionCode(), option.getSourceAdditionalPrice(),
				option.getAdditionalPrice(), option.getSortOrder(), option.getStatus());
	}

	private String imageSnapshot(ProductImage image) {
		return "imageId=%s;type=%s;sortOrder=%d".formatted(image.getId(), image.getType(), image.getSortOrder());
	}

	private String reviewSnapshot(Product product) {
		return "status=%s;reason=%s;productStatus=%s"
			.formatted(product.getReviewStatus(), product.getReviewReasonCode(), product.getStatus());
	}

	private String priceValuesState(Product product, List<ProductOption> options) {
		PricingPolicy policy = product.getPricingPolicyApplied();
		String policyReference = policy == null ? "policy=null" :
			"policyId=%s;policyVersion=%d".formatted(policy.getId(), product.getPricingPolicyVersionApplied());
		return priceValues(policyReference, product, options);
	}

	private String priceState(
		Product product,
		List<ProductOption> options,
		PricingCalculatorSnapshot snapshot
	) {
		String policySnapshot =
			"policyId=%s;policyVersion=%d;commission=%s;taxBuffer=%s;overhead=%s;safetyMargin=%s;rounding=%d;minimumResale=%d"
				.formatted(snapshot.pricingPolicyId(), snapshot.pricingPolicyVersion(), snapshot.commissionRate(),
					snapshot.taxBufferRate(), snapshot.overheadRate(), snapshot.safetyMarginRate(), snapshot.roundingUnit(),
					snapshot.minimumResalePrice());
		return priceValues(policySnapshot, product, options);
	}

	private String priceValues(String policyState, Product product, List<ProductOption> options) {
		String optionPrices = options.stream()
			.map(option -> "%s:%s:%d".formatted(
				option.getId(),
				option.getSourceAdditionalPrice() == null ? 0 : option.getSourceAdditionalPrice(),
				option.getAdditionalPrice()
			))
			.collect(java.util.stream.Collectors.joining(","));
		return "%s;basePrice=%d;optionPrices=[%s]".formatted(policyState, product.getBasePrice(), optionPrices);
	}

	private String normalize(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private ApiErrorException notFound() {
		return new ApiErrorException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, "Resource not found");
	}

	private ApiErrorException conflict(ApiErrorCode code, String message) {
		return new ApiErrorException(HttpStatus.CONFLICT, code, message);
	}

	private record LockedProduct(Supplier supplier, Product product, List<ProductOption> options, UUID userId) {
	}
}
