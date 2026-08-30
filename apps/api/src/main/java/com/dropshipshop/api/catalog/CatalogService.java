package com.dropshipshop.api.catalog;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductChangeHistory;
import com.dropshipshop.api.catalog.domain.ProductChangeActor;
import com.dropshipshop.api.catalog.domain.ProductChangeType;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductComplianceStatus;
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
import com.dropshipshop.api.catalog.domain.PricingPolicy;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.cleanup.ProductImageCleanupService;
import com.dropshipshop.api.catalog.repository.PricingPolicyRepository;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;
import com.dropshipshop.api.catalog.repository.ProductDetailBlockRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.catalog.pricing.CatalogPriceCalculator;
import com.dropshipshop.api.catalog.pricing.PricingCalculatorSnapshot;
import com.dropshipshop.api.catalog.pricing.ProductPriceCalculation;
import com.dropshipshop.api.common.StorefrontSalesProperties;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.common.money.MoneyMath;
import com.dropshipshop.api.common.storage.FileStorage;
import com.dropshipshop.api.common.storage.ImageFileValidator;
import com.dropshipshop.api.common.storage.StoredFile;
import com.dropshipshop.api.policy.CustomerPolicyLinkService;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;
import com.dropshipshop.api.supplierportal.SupplierPortalInputPolicy;

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
	private static final Pattern DOMEGGOOK_PATH_ITEM_NO = Pattern.compile("(?:^|/)(\\d+)(?:/|$)");
	private static final Pattern DOMEGGOOK_QUERY_ITEM_NO = Pattern.compile("(?:^|&)(?:itemNo|item_no|no)=(\\d+)(?:&|$)", Pattern.CASE_INSENSITIVE);

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
	private final StorefrontSalesProperties salesProperties;
	private final SupplierPortalFeatureGate supplierPortalFeatureGate;
	private final SupplierPortalInputPolicy supplierPortalInputPolicy;
	private final ProductImageCleanupService productImageCleanupService;
	private final CatalogPriceCalculator catalogPriceCalculator;

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
		ImageFileValidator imageFileValidator,
		StorefrontSalesProperties salesProperties,
		SupplierPortalFeatureGate supplierPortalFeatureGate,
		SupplierPortalInputPolicy supplierPortalInputPolicy,
		ProductImageCleanupService productImageCleanupService,
		CatalogPriceCalculator catalogPriceCalculator
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
		this.salesProperties = salesProperties;
		this.supplierPortalFeatureGate = supplierPortalFeatureGate;
		this.supplierPortalInputPolicy = supplierPortalInputPolicy;
		this.productImageCleanupService = productImageCleanupService;
		this.catalogPriceCalculator = catalogPriceCalculator;
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
	public CatalogDtos.ProductReviewQueueResponse listProductReviews(int page, int size) {
		Page<Product> products = productRepository.findReviewQueue(
			List.of(ProductReviewStatus.REVIEW_REQUIRED),
			PageRequest.of(page, size, Sort.by(Sort.Order.asc("firstSubmittedAt"), Sort.Order.asc("id")))
		);
		return new CatalogDtos.ProductReviewQueueResponse(
			products.getContent().stream().map(this::toProductReviewSummary).toList(),
			products.getNumber(),
			products.getSize(),
			products.getTotalElements(),
			products.getTotalPages()
		);
	}

	@Transactional(readOnly = true)
	public CatalogDtos.ProductReviewDetailResponse getProductReview(UUID productId) {
		return toProductReviewDetail(findReviewProduct(productId));
	}

	@Transactional
	public CatalogDtos.ProductReviewDetailResponse approveProductReview(
		UUID productId,
		CatalogDtos.ProductReviewActionRequest request,
		UUID adminUserId
	) {
		String internalReason = supplierPortalInputPolicy.requirePiiFreeReason(request.internalReason(), 500);
		ReviewLock lock = lockReviewProduct(productId);
		requireReviewVersion(lock.product(), request.expectedVersion());
		validateSaleReadiness(lock.product());
		long beforeVersion = lock.product().getVersion();
		String before = reviewSnapshot(lock.product());
		lock.product().updateReview(ProductReviewStatus.APPROVED, null, null);
		if (lock.product().getStatus() != ProductStatus.STOPPED) {
			lock.product().updateStatus(ProductStatus.ACTIVE);
		}
		lock.product().incrementVersion();
		recordAdminReviewChange(lock.product(), adminUserId, beforeVersion, before, internalReason);
		return toProductReviewDetail(lock.product());
	}

	@Transactional
	public CatalogDtos.ProductReviewDetailResponse supplementProductReview(
		UUID productId,
		CatalogDtos.ProductReviewFeedbackRequest request,
		UUID adminUserId
	) {
		if (request.reviewReasonCode() != ProductReviewReasonCode.SUPPLEMENT_REQUIRED) {
			throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
				"Supplement requires SUPPLEMENT_REQUIRED reason code");
		}
		return applyReviewFeedback(productId, request, adminUserId, ProductReviewStatus.SUPPLEMENT_REQUESTED);
	}

	@Transactional
	public CatalogDtos.ProductReviewDetailResponse rejectProductReview(
		UUID productId,
		CatalogDtos.ProductReviewFeedbackRequest request,
		UUID adminUserId
	) {
		if (request.reviewReasonCode() != ProductReviewReasonCode.REJECTED_POLICY) {
			throw new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED,
				"Rejection requires REJECTED_POLICY reason code");
		}
		return applyReviewFeedback(productId, request, adminUserId, ProductReviewStatus.REJECTED);
	}

	private CatalogDtos.ProductReviewDetailResponse applyReviewFeedback(
		UUID productId,
		CatalogDtos.ProductReviewFeedbackRequest request,
		UUID adminUserId,
		ProductReviewStatus nextStatus
	) {
		String message = supplierPortalInputPolicy.requirePiiFreeReason(request.supplierReviewMessage(), 500);
		String internalReason = supplierPortalInputPolicy.requirePiiFreeReason(request.internalReason(), 500);
		ReviewLock lock = lockReviewProduct(productId);
		requireReviewVersion(lock.product(), request.expectedVersion());
		long beforeVersion = lock.product().getVersion();
		String before = reviewSnapshot(lock.product());
		lock.product().updateReview(nextStatus, request.reviewReasonCode(), message);
		if (lock.product().getStatus() != ProductStatus.STOPPED) {
			lock.product().updateStatus(ProductStatus.HIDDEN);
		}
		lock.product().incrementVersion();
		recordAdminReviewChange(lock.product(), adminUserId, beforeVersion, before, internalReason);
		return toProductReviewDetail(lock.product());
	}

	private ReviewLock lockReviewProduct(UUID productId) {
		UUID discoveredSupplierId = productRepository.findSupplierIdById(productId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
		Supplier supplier = supplierRepository.findByIdForUpdate(discoveredSupplierId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
		Product product = productRepository.findByIdForUpdate(productId)
			.filter(candidate -> candidate.getManagementChannel() == ProductManagementChannel.SUPPLIER_PORTAL)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
		List<ProductOption> options = productOptionRepository.findAllByProductIdForUpdate(productId);
		if (product.getReviewStatus() != ProductReviewStatus.REVIEW_REQUIRED) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT,
				"Product is not awaiting review");
		}
		return new ReviewLock(supplier, product, options);
	}

	private void requireReviewVersion(Product product, long expectedVersion) {
		if (!product.hasVersion(expectedVersion)) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.PRODUCT_VERSION_CONFLICT,
				"Product version is stale");
		}
	}

	private void recordAdminReviewChange(
		Product product,
		UUID adminUserId,
		long beforeVersion,
		String before,
		String internalReason
	) {
		productChangeHistoryRepository.save(new ProductChangeHistory(
			product,
			null,
			ProductChangeActor.admin(adminUserId),
			beforeVersion,
			product.getVersion(),
			ProductChangeType.REVIEW_STATUS,
			before,
			reviewSnapshot(product),
			internalReason
		));
	}

	@Transactional(readOnly = true)
	public CatalogDtos.PricingPolicyResponse getPricingPolicy() {
		return toPricingPolicyResponse(activePricingPolicy());
	}

	@Transactional
	public CatalogDtos.PricingPolicyResponse updatePricingPolicy(CatalogDtos.PricingPolicyRequest request) {
		PricingPolicy policy = pricingPolicyRepository.findActiveForUpdate()
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
		String sourceItemNo = sourceItemNo(request.sourceUrl(), request.sourceItemNo(), null);
		requireUniqueSourceItemNo(sourceItemNo, null);
		Product product = new Product(
			supplier,
			request.name(),
			request.summary(),
			sourcePrice(request.sourcePrice(), request.basePrice()),
			request.basePrice(),
			request.categoryCode(),
			request.status()
		);
		product.updateOrderQuantityRules(
			valueOrDefault(request.minimumOrderQuantity(), 1),
			valueOrDefault(request.orderQuantityStep(), 1)
		);
		product.updateSourceItemNo(sourceItemNo);
		product.updateSourceUrl(request.sourceUrl());
		try {
			return toAdminProductResponse(productRepository.saveAndFlush(product));
		} catch (DataIntegrityViolationException exception) {
			throw duplicateSourceItemNo(sourceItemNo);
		}
	}

	@Transactional(readOnly = true)
	public CatalogDtos.ProductDetailResponse getAdminProduct(UUID productId) {
		return toProductDetailResponse(findProduct(productId), true);
	}

	@Transactional(readOnly = true)
	public CatalogDtos.ProductChangeHistoryListResponse listProductChanges(UUID productId) {
		if (!productRepository.existsById(productId)
			&& !productChangeHistoryRepository.existsBySubjectProductId(productId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
		}
		return new CatalogDtos.ProductChangeHistoryListResponse(
			productChangeHistoryRepository.findAllBySubjectProductIdOrderByCreatedAtAsc(productId)
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
		AdminProductLock lock = lockAdminProduct(productId, request.supplierId());
		Product product = lock.product();
		requireOptionalVersion(product, request.expectedVersion());
		Supplier supplier = findSupplier(request.supplierId());
		String sourceItemNo = sourceItemNo(request.sourceUrl(), request.sourceItemNo(), product.getSourceItemNo());
		requireUniqueSourceItemNo(sourceItemNo, productId);
		requireReason(request.reason());
		long effectiveSourcePrice = sourcePrice(product, request);
		PricingPolicy portalPolicy = product.getManagementChannel() == ProductManagementChannel.SUPPLIER_PORTAL
			? activePricingPolicyForUpdate() : null;
		ProductPriceCalculation portalCalculation = portalPolicy != null
			? calculatePortalPrices(effectiveSourcePrice, sourceAdditionalPrices(lock.options()), portalPolicy)
			: null;
		long effectiveBasePrice = portalCalculation == null ? request.basePrice() : portalCalculation.basePrice();
		if (portalCalculation == null) {
			requireCustomerUnitPriceLimit(effectiveBasePrice, lock.options());
		}
		String beforePortalPrices = portalCalculation == null ? null : portalPriceValuesState(product, lock.options());
		recordProductBaseChanges(
			product, supplier, request, effectiveSourcePrice, effectiveBasePrice, adminUserId
		);
		product.updateBase(
			supplier, request.name(), request.summary(), effectiveSourcePrice, effectiveBasePrice, request.categoryCode()
		);
		if (portalCalculation != null) {
			applyPortalPrices(product, lock.options(), portalPolicy, portalCalculation);
			recordChange(product, null, adminUserId, ProductChangeType.PRICE,
				beforePortalPrices, portalPriceState(product, lock.options(), portalCalculation.snapshot()), request.reason());
		}
		product.updateOrderQuantityRules(
			valueOrDefault(request.minimumOrderQuantity(), product.getMinimumOrderQuantity()),
			valueOrDefault(request.orderQuantityStep(), product.getOrderQuantityStep())
		);
		product.updateSourceItemNo(sourceItemNo);
		product.updateSourceUrl(request.sourceUrl());
		product.updateComplianceStatus(valueOrDefault(request.complianceStatus(), product.getComplianceStatus()));
		validateIfActive(product);
		completeAdminMutation(product, adminUserId, request.reason());
		return toAdminProductResponse(product);
	}

	private String sourceItemNo(String sourceUrl, String requestedSourceItemNo, String fallbackSourceItemNo) {
		String requested = normalized(requestedSourceItemNo);
		String fallback = normalized(fallbackSourceItemNo);
		if (sourceUrl == null || sourceUrl.isBlank()) {
			return requested != null ? requested : fallback;
		}

		URI uri;
		try {
			uri = URI.create(sourceUrl);
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "공급처 상품 주소가 올바르지 않습니다");
		}
		String host = uri.getHost();
		if (host == null || !(host.equalsIgnoreCase("domeggook.com") || host.toLowerCase(Locale.ROOT).endsWith(".domeggook.com"))) {
			return requested != null ? requested : fallback;
		}

		String derived = firstMatch(DOMEGGOOK_PATH_ITEM_NO, uri.getPath());
		if (derived == null) {
			derived = firstMatch(DOMEGGOOK_QUERY_ITEM_NO, uri.getRawQuery());
		}
		if (derived == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "도매꾹 상품번호를 공급처 상품 주소에서 확인할 수 없습니다");
		}
		if (requested != null && !requested.equals(derived)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "공급처 상품번호가 상품 주소와 일치하지 않습니다");
		}
		return derived;
	}

	private String firstMatch(Pattern pattern, String value) {
		if (value == null) {
			return null;
		}
		Matcher matcher = pattern.matcher(value);
		return matcher.find() ? matcher.group(1) : null;
	}

	private String normalized(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void requireUniqueSourceItemNo(String sourceItemNo, UUID excludedProductId) {
		if (sourceItemNo == null) {
			return;
		}
		productRepository.findBySourceItemNo(sourceItemNo)
			.filter(product -> !product.getId().equals(excludedProductId))
			.ifPresent(product -> {
				throw duplicateSourceItemNo(sourceItemNo);
			});
	}

	private ResponseStatusException duplicateSourceItemNo(String sourceItemNo) {
		return new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 공급처 상품번호입니다: " + sourceItemNo);
	}

	@Transactional
	public CatalogDtos.AdminProductResponse updateProductStatus(
		UUID productId,
		CatalogDtos.ProductStatusRequest request,
		UUID adminUserId
	) {
		Product product = lockAdminProduct(productId, null).product();
		requireOptionalVersion(product, request.expectedVersion());
		requireReason(request.reason());
		product.clearSourceAutoSoldOut();
		if (product.getManagementChannel() == ProductManagementChannel.SUPPLIER_PORTAL
			&& request.status() == ProductStatus.ACTIVE
			&& product.getReviewStatus() != ProductReviewStatus.AUTO_APPROVED
			&& product.getReviewStatus() != ProductReviewStatus.APPROVED) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT,
				"Portal product must pass review before activation");
		}
		if (product.getStatus() != request.status()) {
			if (request.status() == ProductStatus.ACTIVE) {
				validateSaleReadiness(product);
			}
			recordChange(product, null, adminUserId, ProductChangeType.PRODUCT_STATUS,
				product.getStatus().name(), request.status().name(), request.reason());
			product.updateStatus(request.status());
		}
		completeAdminStatusMutation(product, adminUserId, request.reason());
		return toAdminProductResponse(product);
	}

	@Transactional
	public CatalogDtos.ProductOptionResponse createOption(
		UUID productId,
		CatalogDtos.ProductOptionRequest request,
		UUID adminUserId
	) {
		AdminProductLock lock = lockAdminProduct(productId, null);
		Product product = lock.product();
		requireOptionalVersion(product, request.expectedVersion());
		ProductOptionStatus status = request.status() == null ? ProductOptionStatus.ACTIVE : request.status();
		Long sourceAdditionalPrice = request.sourceAdditionalPrice();
		ProductPriceCalculation portalCalculation = null;
		PricingPolicy portalPolicy = null;
		String beforePortalPrices = null;
		long additionalPrice = request.additionalPrice();
		if (product.getManagementChannel() == ProductManagementChannel.SUPPLIER_PORTAL) {
			sourceAdditionalPrice = valueOrDefault(sourceAdditionalPrice, 0L);
			portalPolicy = activePricingPolicyForUpdate();
			List<Long> sourcePrices = new ArrayList<>(sourceAdditionalPrices(lock.options()));
			sourcePrices.add(sourceAdditionalPrice);
			portalCalculation = calculatePortalPrices(product.getSourcePrice(), sourcePrices, portalPolicy);
			beforePortalPrices = portalPriceValuesState(product, lock.options());
			additionalPrice = portalCalculation.options().get(sourcePrices.size() - 1).additionalPrice();
		}
		ProductOption option = new ProductOption(
			product,
			request.name(),
			additionalPrice,
			status,
			request.sourceOptionCode(),
			sourceAdditionalPrice,
			request.sourceStockQuantity(),
			valueOrDefault(request.sortOrder(), 0)
		);
		productOptionRepository.saveAndFlush(option);
		if (portalCalculation != null) {
			List<ProductOption> options = new ArrayList<>(lock.options());
			options.add(option);
			applyPortalPrices(product, options, portalPolicy, portalCalculation);
			recordChange(product, option, adminUserId, ProductChangeType.PRICE,
				beforePortalPrices, portalPriceState(product, options, portalCalculation.snapshot()), "OPTION_CREATED");
		}
		recordChange(product, option, adminUserId, ProductChangeType.OPTION_BASE, null, request.name(), "OPTION_CREATED");
		completeAdminMutation(product, adminUserId, "OPTION_CREATED");
		return toOptionResponse(option, true);
	}

	@Transactional
	public CatalogDtos.ProductOptionResponse updateOption(
		UUID productId,
		UUID optionId,
		CatalogDtos.ProductOptionRequest request,
		UUID adminUserId
	) {
		AdminProductLock lock = lockAdminProduct(productId, null);
		requireOptionalVersion(lock.product(), request.expectedVersion());
		ProductOption option = lockedOption(lock, optionId);
		requireReason(request.reason());
		long effectiveAdditionalPrice = request.additionalPrice();
		Long effectiveSourceAdditionalPrice = valueOrDefault(
			request.sourceAdditionalPrice(), option.getSourceAdditionalPrice()
		);
		ProductPriceCalculation portalCalculation = null;
		PricingPolicy portalPolicy = null;
		String beforePortalPrices = null;
		if (lock.product().getManagementChannel() == ProductManagementChannel.SUPPLIER_PORTAL) {
			portalPolicy = activePricingPolicyForUpdate();
			effectiveSourceAdditionalPrice = valueOrDefault(effectiveSourceAdditionalPrice, 0L);
			List<Long> sourcePrices = new ArrayList<>();
			int targetIndex = -1;
			for (int index = 0; index < lock.options().size(); index++) {
				ProductOption candidate = lock.options().get(index);
				if (candidate.getId().equals(optionId)) {
					targetIndex = index;
					sourcePrices.add(effectiveSourceAdditionalPrice);
				} else {
					sourcePrices.add(valueOrDefault(candidate.getSourceAdditionalPrice(), 0L));
				}
			}
			portalCalculation = calculatePortalPrices(lock.product().getSourcePrice(), sourcePrices, portalPolicy);
			beforePortalPrices = portalPriceValuesState(lock.product(), lock.options());
			effectiveAdditionalPrice = portalCalculation.options().get(targetIndex).additionalPrice();
		}
		if (option.getAdditionalPrice() != effectiveAdditionalPrice) {
			recordChange(option.getProduct(), option, adminUserId, ProductChangeType.PRICE,
				String.valueOf(option.getAdditionalPrice()), String.valueOf(effectiveAdditionalPrice), request.reason());
		}
		if (!Objects.equals(option.getName(), request.name())) {
			recordChange(option.getProduct(), option, adminUserId, ProductChangeType.OPTION_BASE,
				option.getName(), request.name(), request.reason());
		}
		option.update(
			request.name(),
			effectiveAdditionalPrice,
			valueOrDefault(request.sourceOptionCode(), option.getSourceOptionCode()),
			effectiveSourceAdditionalPrice,
			valueOrDefault(request.sourceStockQuantity(), option.getSourceStockQuantity()),
			valueOrDefault(request.sortOrder(), option.getSortOrder())
		);
		if (portalCalculation != null) {
			applyPortalPrices(lock.product(), lock.options(), portalPolicy, portalCalculation);
			recordChange(lock.product(), option, adminUserId, ProductChangeType.PRICE,
				beforePortalPrices,
				portalPriceState(lock.product(), lock.options(), portalCalculation.snapshot()),
				request.reason());
		}
		completeAdminMutation(lock.product(), adminUserId, request.reason());
		return toOptionResponse(option, true);
	}

	@Transactional
	public CatalogDtos.ProductOptionResponse updateOptionStatus(
		UUID productId,
		UUID optionId,
		CatalogDtos.ProductOptionStatusRequest request,
		UUID adminUserId
	) {
		AdminProductLock lock = lockAdminProduct(productId, null);
		requireOptionalVersion(lock.product(), request.expectedVersion());
		ProductOption option = lockedOption(lock, optionId);
		requireReason(request.reason());
		if (option.getStatus() != request.status()) {
			recordChange(option.getProduct(), option, adminUserId, ProductChangeType.OPTION_STATUS,
				option.getStatus().name(), request.status().name(), request.reason());
			option.updateStatus(request.status());
			validateIfActive(option.getProduct());
		}
		completeAdminMutation(lock.product(), adminUserId, request.reason());
		return toOptionResponse(option, true);
	}

	@Transactional
	public CatalogDtos.ProductDetailResponse replaceImages(
		UUID productId,
		CatalogDtos.ProductImagesRequest request,
		UUID adminUserId
	) {
		Product product = lockAdminProduct(productId, null).product();
		requireOptionalVersion(product, request.expectedVersion());
		requireReason(request.reason());
		validateImages(request.images());
		List<ProductImage> existingImages = productImageRepository.findAllByProduct_IdOrderBySortOrderAsc(productId);
		List<ProductImage> replacedImages = product.getManagementChannel() == ProductManagementChannel.SUPPLIER_PORTAL
			? existingImages.stream().filter(image -> image.getType() != ProductImageType.DETAIL).toList()
			: existingImages;
		Map<String, ProductImage> existingByUrl = replacedImages.stream()
			.collect(Collectors.toMap(ProductImage::getImageUrl, image -> image, (first, ignored) -> first));
		List<ProductImage> images = request.images().stream()
			.map(item -> new ProductImage(
				product,
				item.type(),
				item.imageUrl(),
				item.sortOrder(),
				item.altText(),
				adminStorageObjectKey(productId, item, existingByUrl)
			))
			.toList();
		Set<String> retainedStorageKeys = images.stream()
			.map(ProductImage::getStorageObjectKey)
			.filter(key -> key != null && !key.isBlank())
			.collect(Collectors.toSet());
		productImageRepository.deleteAll(replacedImages);
		productImageRepository.flush();
		Instant cleanupRequestedAt = Instant.now();
		replacedImages.stream()
			.map(ProductImage::getStorageObjectKey)
			.filter(key -> key != null && !key.isBlank())
			.filter(key -> !retainedStorageKeys.contains(key))
			.forEach(key -> productImageCleanupService.enqueueCleanup(key, productId, cleanupRequestedAt));
		productImageRepository.saveAll(images);
		product.updateThumbnailImageUrl(thumbnailUrl(images));
		validateIfActive(product);
		recordChange(product, null, adminUserId, ProductChangeType.IMAGES, null, "replaced", request.reason());
		completeAdminMutation(product, adminUserId, request.reason());
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
		Product product = lockAdminProduct(productId, null).product();
		requireOptionalVersion(product, request.expectedVersion());
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
		completeAdminMutation(product, adminUserId, request.reason());
		return toProductDetailResponse(product, true);
	}

	@Transactional
	public CatalogDtos.ProductDetailResponse updateNotice(
		UUID productId,
		CatalogDtos.ProductNoticeRequest request,
		UUID adminUserId
	) {
		Product product = lockAdminProduct(productId, null).product();
		requireOptionalVersion(product, request.expectedVersion());
		requireReason(request.reason());
		int nextVersion = productNoticeRepository.countByProduct_Id(productId) + 1;
		List<ProductNoticeRow> noticeRows = request.noticeRows() == null
			? productNoticeRepository
				.findFirstByProduct_IdAndStatusOrderByVersionDesc(productId, ProductNoticeStatus.ACTIVE)
				.map(ProductNotice::getNoticeRows)
				.orElseGet(List::of)
			: request.noticeRows().stream()
				.map(row -> new ProductNoticeRow(row.label(), row.value()))
				.toList();
		ProductNotice notice = new ProductNotice(
			product,
			nextVersion,
			request.productInfoNotice(),
			request.shippingInfo(),
			request.asInfo(),
			request.returnExchangeInfo(),
			noticeRows
		);
		productNoticeRepository.save(notice);
		recordChange(product, null, adminUserId, ProductChangeType.NOTICE, null,
			"productNoticeVersion=" + nextVersion, request.reason());
		completeAdminMutation(product, adminUserId, request.reason());
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
			supplierPortalFeatureGate.isEnabled(),
			Instant.now(),
			PageRequest.of(page, size, publicProductSort(sort))
		);
		Map<ProductCategory, Long> categoryCounts = new EnumMap<>(ProductCategory.class);
		Arrays.stream(ProductCategory.values()).forEach(value -> categoryCounts.put(value, 0L));
		productRepository.countPublicProductsByCategory(
			keyword, minPrice, maxPrice, supplierPortalFeatureGate.isEnabled(), Instant.now()
		)
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
		Product product = productRepository.findPublicProductById(
			productId, supplierPortalFeatureGate.isEnabled(), Instant.now()
		).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
		return toProductDetailResponse(product, false);
	}

	private void recordProductBaseChanges(
		Product product,
		Supplier newSupplier,
		CatalogDtos.ProductUpdateRequest request,
		long effectiveSourcePrice,
		long effectiveBasePrice,
		UUID adminUserId
	) {
		if (!Objects.equals(product.getSupplier().getId(), newSupplier.getId())) {
			recordChange(product, null, adminUserId, ProductChangeType.SUPPLIER,
				product.getSupplier().getId().toString(), newSupplier.getId().toString(), request.reason());
		}
		if (product.getBasePrice() != effectiveBasePrice) {
			recordChange(product, null, adminUserId, ProductChangeType.PRICE,
				String.valueOf(product.getBasePrice()), String.valueOf(effectiveBasePrice), request.reason());
		}
		if (product.getSourcePrice() != effectiveSourcePrice) {
			recordChange(product, null, adminUserId, ProductChangeType.PRICE,
				"source=" + product.getSourcePrice(), "source=" + effectiveSourcePrice, request.reason());
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
		int minimumOrderQuantity = valueOrDefault(request.minimumOrderQuantity(), product.getMinimumOrderQuantity());
		int orderQuantityStep = valueOrDefault(request.orderQuantityStep(), product.getOrderQuantityStep());
		if (product.getMinimumOrderQuantity() != minimumOrderQuantity || product.getOrderQuantityStep() != orderQuantityStep) {
			recordChange(product, null, adminUserId, ProductChangeType.ORDER_QUANTITY,
				product.getMinimumOrderQuantity() + "/" + product.getOrderQuantityStep(),
				minimumOrderQuantity + "/" + orderQuantityStep,
				request.reason());
		}
	}

	private void validateIfActive(Product product) {
		if (product.getStatus() == ProductStatus.ACTIVE) {
			validateSaleReadiness(product);
		}
	}

	private void requireCustomerUnitPriceLimit(long basePrice, List<ProductOption> options) {
		for (ProductOption option : options) {
			MoneyMath.requireCustomerUnitPrice(
				MoneyMath.addNonNegative(basePrice, option.getAdditionalPrice()),
				"unitPrice"
			);
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

	private AdminProductLock lockAdminProduct(UUID productId, UUID additionalSupplierId) {
		UUID discoveredSupplierId = productRepository.findSupplierIdById(productId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
		Set<UUID> supplierIds = new TreeSet<>();
		supplierIds.add(discoveredSupplierId);
		if (additionalSupplierId != null) {
			supplierIds.add(additionalSupplierId);
		}
		for (UUID supplierId : supplierIds) {
			supplierRepository.findByIdForUpdate(supplierId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
		}
		Product product = productRepository.findByIdForUpdate(productId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
		if (!discoveredSupplierId.equals(product.getSupplier().getId())) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.PRODUCT_VERSION_CONFLICT,
				"Product supplier changed while the product was being locked");
		}
		return new AdminProductLock(product, productOptionRepository.findAllByProductIdForUpdate(productId));
	}

	private ProductOption lockedOption(AdminProductLock lock, UUID optionId) {
		return lock.options().stream()
			.filter(option -> option.getId().equals(optionId))
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product option not found"));
	}

	private void requireOptionalVersion(Product product, Long expectedVersion) {
		if (expectedVersion != null && !product.hasVersion(expectedVersion)) {
			throw new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.PRODUCT_VERSION_CONFLICT,
				"Product version is stale");
		}
	}

	private void completeAdminMutation(Product product, UUID adminUserId, String reason) {
		if (product.getManagementChannel() == ProductManagementChannel.SUPPLIER_PORTAL) {
			ProductStatus postMutationStatus = product.getStatus() == ProductStatus.STOPPED
				? ProductStatus.STOPPED : ProductStatus.HIDDEN;
			if (product.getReviewStatus() != ProductReviewStatus.DRAFT) {
				recordChange(product, null, adminUserId, ProductChangeType.REVIEW_STATUS,
					reviewSnapshot(product),
					"reviewStatus=DRAFT;productStatus=" + postMutationStatus,
					reason);
				product.updateReview(ProductReviewStatus.DRAFT, null, null);
			}
			product.updateStatus(postMutationStatus);
		}
		completeAdminStatusMutation(product, adminUserId, reason);
	}

	private void completeAdminStatusMutation(Product product, UUID adminUserId, String reason) {
		recordChange(product, null, adminUserId, ProductChangeType.PRODUCT_BASE,
			"aggregateVersion=" + product.getVersion(), "aggregateVersion=" + (product.getVersion() + 1), reason);
		product.incrementVersion();
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
			ProductChangeActor.admin(adminUserId),
			product.getVersion(),
			product.getVersion() + 1,
			changeType,
			beforeValue,
			afterValue,
			reason
		));
	}

	private void validateImages(List<CatalogDtos.ProductImageItem> images) {
		if (images.stream().anyMatch(image -> image.type() == ProductImageType.DETAIL)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Detail images must be managed through product detail blocks");
		}
		long thumbnailCount = images.stream().filter(image -> image.type() == ProductImageType.THUMBNAIL).count();
		long galleryCount = images.stream().filter(image -> image.type() == ProductImageType.GALLERY).count();
		if (thumbnailCount > 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only one thumbnail image is allowed");
		}
		if (galleryCount > 10) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ten gallery images are allowed");
		}
		if (images.stream().map(CatalogDtos.ProductImageItem::imageUrl).distinct().count() != images.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product image URLs must be unique");
		}
		images.forEach(image -> validateImageUrl(image.imageUrl()));
	}

	private String adminStorageObjectKey(
		UUID productId,
		CatalogDtos.ProductImageItem item,
		Map<String, ProductImage> existingByUrl
	) {
		String requestedKey = normalized(item.storageObjectKey());
		if (requestedKey == null) {
			ProductImage existing = existingByUrl.get(item.imageUrl());
			return existing == null ? null : existing.getStorageObjectKey();
		}
		String prefix = productId + "/";
		String suffix = requestedKey.startsWith(prefix) ? requestedKey.substring(prefix.length()) : "";
		if (suffix.isBlank() || suffix.contains("/") || suffix.contains("..")
			|| productImageCleanupService.hasCleanupJob(requestedKey)
			|| !fileStorage.matchesStoredFile(requestedKey, item.imageUrl())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product image storage reference");
		}
		return requestedKey;
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

	private PricingPolicy activePricingPolicyForUpdate() {
		return pricingPolicyRepository.findActiveForUpdate()
			.orElseGet(() -> pricingPolicyRepository.saveAndFlush(defaultPricingPolicy()));
	}

	private ProductPriceCalculation calculatePortalPrices(
		long sourcePrice,
		List<Long> sourceAdditionalPrices,
		PricingPolicy policy
	) {
		return catalogPriceCalculator.calculate(sourcePrice, sourceAdditionalPrices, 0, policy);
	}

	private List<Long> sourceAdditionalPrices(List<ProductOption> options) {
		return options.stream()
			.map(option -> valueOrDefault(option.getSourceAdditionalPrice(), 0L))
			.toList();
	}

	private void applyPortalPrices(
		Product product,
		List<ProductOption> options,
		PricingPolicy policy,
		ProductPriceCalculation calculation
	) {
		product.updateSourcePricing(calculation.sourcePrice(), calculation.basePrice());
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
	}

	private String portalPriceValuesState(Product product, List<ProductOption> options) {
		PricingPolicy policy = product.getPricingPolicyApplied();
		String policyReference = policy == null ? "policy=null" :
			"policyId=%s;policyVersion=%d".formatted(policy.getId(), product.getPricingPolicyVersionApplied());
		return portalPriceValues(policyReference, product, options);
	}

	private String portalPriceState(
		Product product,
		List<ProductOption> options,
		PricingCalculatorSnapshot snapshot
	) {
		String policySnapshot =
			"policyId=%s;policyVersion=%d;commission=%s;taxBuffer=%s;overhead=%s;safetyMargin=%s;rounding=%d;minimumResale=%d"
				.formatted(snapshot.pricingPolicyId(), snapshot.pricingPolicyVersion(), snapshot.commissionRate(),
					snapshot.taxBufferRate(), snapshot.overheadRate(), snapshot.safetyMarginRate(),
					snapshot.roundingUnit(), snapshot.minimumResalePrice());
		return portalPriceValues(policySnapshot, product, options);
	}

	private String portalPriceValues(String policyState, Product product, List<ProductOption> options) {
		String optionPrices = options.stream()
			.map(option -> "%s:%s:%d".formatted(
				option.getId(), valueOrDefault(option.getSourceAdditionalPrice(), 0L), option.getAdditionalPrice()
			))
			.collect(Collectors.joining(","));
		return "%s;basePrice=%d;optionPrices=[%s]".formatted(policyState, product.getBasePrice(), optionPrices);
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
			supplier.getStatus(),
			supplier.getId(),
			supplier.getEmail(),
			supplier.getManagerUserId(),
			supplier.getPortalStatus(),
			supplier.getStatus(),
			supplier.getPortalContractStatus(),
			supplier.getPortalContractVersion(),
			supplier.getPortalContractEffectiveAt(),
			supplier.getPortalContractExpiresAt(),
			supplier.getContactEmailVerifiedAt()
		);
	}

	private CatalogDtos.AdminProductResponse toAdminProductResponse(Product product) {
		return toAdminProductResponse(product, saleReadiness(product));
	}

	private CatalogDtos.AdminProductResponse toAdminProductResponse(Product product, SaleReadiness readiness) {
		return new CatalogDtos.AdminProductResponse(
			product.getId(),
			product.getVersion(),
			product.getSupplier().getId(),
			product.getSupplier().getName(),
			product.getName(),
			product.getSummary(),
			product.getSourcePrice(),
			product.getSourceItemNo(),
			product.getSourceUrl(),
			product.getSourceAvailable(),
			product.getSourceSyncedAt(),
			product.getSourceSyncError(),
			product.getBasePrice(),
			product.getMinimumOrderQuantity(),
			product.getOrderQuantityStep(),
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
			product.getMinimumOrderQuantity(),
			product.getOrderQuantityStep(),
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
			product.getVersion(),
			includeSourcePrice ? product.getSupplier().getId() : null,
			includeSourcePrice ? product.getSupplier().getName() : null,
			product.getName(),
			product.getSummary(),
			includeSourcePrice ? product.getSourcePrice() : null,
			includeSourcePrice ? product.getSourceItemNo() : null,
			includeSourcePrice ? product.getSourceUrl() : null,
			includeSourcePrice ? product.getSourceAvailable() : null,
			includeSourcePrice ? product.getSourceSyncedAt() : null,
			includeSourcePrice ? product.getSourceSyncError() : null,
			product.getBasePrice(),
			product.getMinimumOrderQuantity(),
			product.getOrderQuantityStep(),
			product.getCategoryCode(),
			product.getStatus(),
			salesProperties.enabled(),
			salesProperties.enabled() ? null : salesProperties.closedNotice(),
			product.getComplianceStatus(),
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

	private record ReviewLock(Supplier supplier, Product product, List<ProductOption> options) {
	}

	private record AdminProductLock(Product product, List<ProductOption> options) {
	}

	private Product findReviewProduct(UUID productId) {
		return productRepository.findReviewProductById(productId)
			.filter(product -> product.getManagementChannel() == ProductManagementChannel.SUPPLIER_PORTAL)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product review not found"));
	}

	private CatalogDtos.ProductReviewSummaryResponse toProductReviewSummary(Product product) {
		return new CatalogDtos.ProductReviewSummaryResponse(
			product.getId(),
			product.getVersion(),
			product.getSupplier().getId(),
			product.getSupplier().getName(),
			product.getName(),
			product.getCategoryCode(),
			product.getReviewStatus(),
			product.getReviewReasonCode(),
			product.getFirstSubmittedAt()
		);
	}

	private CatalogDtos.ProductReviewDetailResponse toProductReviewDetail(Product product) {
		ProductNotice notice = productNoticeRepository
			.findFirstByProduct_IdAndStatusOrderByVersionDesc(product.getId(), ProductNoticeStatus.ACTIVE)
			.orElse(null);
		return new CatalogDtos.ProductReviewDetailResponse(
			product.getId(),
			product.getVersion(),
			product.getSupplier().getId(),
			product.getSupplier().getName(),
			product.getName(),
			product.getSummary(),
			product.getSourcePrice(),
			product.getBasePrice(),
			product.getMinimumOrderQuantity(),
			product.getOrderQuantityStep(),
			product.getCategoryCode(),
			product.getStatus(),
			product.getComplianceStatus(),
			product.getReviewStatus(),
			product.getReviewReasonCode(),
			product.getSupplierReviewMessage(),
			productOptionRepository.findAllByProduct_IdOrderBySortOrderAscCreatedAtAsc(product.getId()).stream()
				.map(option -> toOptionResponse(option, true))
				.toList(),
			productImageRepository.findAllByProduct_IdOrderBySortOrderAsc(product.getId()).stream()
				.map(this::toImageResponse)
				.toList(),
			productDetailBlockRepository.findAllByProduct_IdOrderBySortOrderAsc(product.getId()).stream()
				.map(this::toDetailBlockResponse)
				.toList(),
			notice == null ? null : toNoticeResponse(notice)
		);
	}

	private String reviewSnapshot(Product product) {
		return "reviewStatus=%s;reviewReasonCode=%s;productStatus=%s"
			.formatted(product.getReviewStatus(), product.getReviewReasonCode(), product.getStatus());
	}

	private CatalogDtos.ProductOptionResponse toOptionResponse(ProductOption option, boolean includeSourceMetadata) {
		return new CatalogDtos.ProductOptionResponse(
			option.getId(),
			option.getProduct().getVersion(),
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
			totalMarkupRate(policy),
			policy.getVersion()
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
			history.getSubjectProductOptionId(),
			history.getAdminUserId(),
			history.getActorType(),
			history.getActorUserId(),
			history.getActorSupplierId(),
			history.getActorSystemCode(),
			history.getBeforeVersion(),
			history.getAfterVersion(),
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
			notice.getReturnExchangeInfo(),
			notice.getNoticeRows().stream()
				.map(row -> new CatalogDtos.ProductNoticeRowItem(row.label(), row.value()))
				.toList()
		);
	}
}
