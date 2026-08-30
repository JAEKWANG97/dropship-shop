package com.dropshipshop.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tools.jackson.databind.ObjectMapper;

import com.dropshipshop.api.auth.security.AuthenticatedUser;
import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductComplianceStatus;
import com.dropshipshop.api.catalog.domain.ProductDetailBlock;
import com.dropshipshop.api.catalog.domain.ProductDetailBlockType;
import com.dropshipshop.api.catalog.domain.ProductImage;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.domain.ProductManagementChannel;
import com.dropshipshop.api.catalog.domain.ProductNotice;
import com.dropshipshop.api.catalog.domain.ProductNoticeRow;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductReviewReasonCode;
import com.dropshipshop.api.catalog.domain.ProductReviewStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.ProductDetailBlockRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductImageCleanupJobRepository;
import com.dropshipshop.api.catalog.repository.ProductNoticeRepository;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.PricingPolicyRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.user.domain.UserRole;

@SpringBootTest(properties = {
	"app.supplier-portal.enabled=true",
	"app.cors.allowed-origins=http://localhost:3000",
	"spring.datasource.url=jdbc:h2:mem:admin_product_review_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AdminProductReviewApiIntegrationTest {

	private static final String ORIGIN = "http://localhost:3000";
	private static final String REVIEW_MESSAGE = "상품 판매 가능 여부를 Coreable에서 확인하고 있습니다.";

	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired SupplierRepository supplierRepository;
	@Autowired ProductRepository productRepository;
	@Autowired ProductOptionRepository optionRepository;
	@Autowired PricingPolicyRepository pricingPolicyRepository;
	@Autowired ProductImageRepository imageRepository;
	@Autowired ProductImageCleanupJobRepository cleanupJobRepository;
	@Autowired ProductDetailBlockRepository detailBlockRepository;
	@Autowired ProductNoticeRepository noticeRepository;

	@Test
	void listsAndReadsOnlyReviewRequiredProductsWithStructuredContent() throws Exception {
		Fixture fixture = reviewRequiredFixture();

		mockMvc.perform(get("/api/admin/product-reviews")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products", hasSize(1)))
			.andExpect(jsonPath("$.products[0].productId", is(fixture.productId().toString())))
			.andExpect(jsonPath("$.products[0].version", is((int) fixture.version())))
			.andExpect(jsonPath("$.products[0].supplierName", is("Review Supplier")))
			.andExpect(jsonPath("$.products[0].reviewStatus", is("REVIEW_REQUIRED")))
			.andExpect(jsonPath("$.products[0].reviewReasonCode", is("CATEGORY_REVIEW")))
			.andExpect(jsonPath("$.totalElements", is(1)));

		mockMvc.perform(get("/api/admin/product-reviews/{productId}", fixture.productId())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.productId", is(fixture.productId().toString())))
			.andExpect(jsonPath("$.version", is((int) fixture.version())))
			.andExpect(jsonPath("$.reviewStatus", is("REVIEW_REQUIRED")))
			.andExpect(jsonPath("$.options", hasSize(1)))
			.andExpect(jsonPath("$.options[0].sourceOptionCode", is("BASE")))
			.andExpect(jsonPath("$.images", hasSize(2)))
			.andExpect(jsonPath("$.images[0].type", is("THUMBNAIL")))
			.andExpect(jsonPath("$.images[0].storageObjectKey").doesNotExist())
			.andExpect(jsonPath("$.detailBlocks", hasSize(2)))
			.andExpect(jsonPath("$.detailBlocks[0].type", is("HTML")))
			.andExpect(jsonPath("$.detailBlocks[1].type", is("IMAGE")))
			.andExpect(jsonPath("$.productNotice.noticeRows[0].label", is("인증번호")))
			.andExpect(jsonPath("$.productNotice.noticeRows[0].value", is("SAFE-123")));

		mockMvc.perform(get("/api/supplier/products/{productId}", fixture.productId())
				.with(authentication(supplierAuthentication(fixture.managerUserId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplierDisplayStatus", is("UNDER_REVIEW")))
			.andExpect(jsonPath("$.reviewReasonCode", is("CATEGORY_REVIEW")))
			.andExpect(jsonPath("$.reviewMessage", is(REVIEW_MESSAGE)))
			.andExpect(jsonPath("$.basePrice").doesNotExist())
			.andExpect(jsonPath("$.status").doesNotExist())
			.andExpect(jsonPath("$.managementChannel").doesNotExist())
			.andExpect(jsonPath("$.internalReason").doesNotExist())
			.andExpect(jsonPath("$.reviewedByAdminId").doesNotExist());
	}

	@Test
	void rejectsStaleApprovalWithoutMutationThenApprovesTheExactVersion() throws Exception {
		Fixture fixture = reviewRequiredFixture();

		mockMvc.perform(post("/api/admin/product-reviews/{productId}/approve", fixture.productId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
					"expectedVersion", fixture.version() - 1,
					"internalReason", "Required evidence verified"
				))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("PRODUCT_VERSION_CONFLICT")));

		assertReviewState(fixture.productId(), fixture.version(), ProductReviewStatus.REVIEW_REQUIRED);

		mockMvc.perform(post("/api/admin/product-reviews/{productId}/approve", fixture.productId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
					"expectedVersion", fixture.version(),
					"internalReason", "Required evidence verified"
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is((int) fixture.version() + 1)))
			.andExpect(jsonPath("$.reviewStatus", is("APPROVED")))
			.andExpect(jsonPath("$.status", is("ACTIVE")))
			.andExpect(jsonPath("$.internalReason").doesNotExist());

		assertReviewState(fixture.productId(), fixture.version() + 1, ProductReviewStatus.APPROVED);
		assertThat(productRepository.findById(fixture.productId()).orElseThrow().getStatus())
			.isEqualTo(ProductStatus.ACTIVE);
	}

	@Test
	void approvalRejectsMissingSaleReadinessAndRejectedComplianceWithoutMutation() throws Exception {
		Fixture fixture = reviewRequiredFixture();
		ProductImage thumbnail = imageRepository.findAllByProduct_IdOrderBySortOrderAsc(fixture.productId()).stream()
			.filter(image -> image.getType() == ProductImageType.THUMBNAIL)
			.findFirst()
			.orElseThrow();
		imageRepository.delete(thumbnail);
		imageRepository.flush();

		approve(fixture).andExpect(status().isBadRequest());
		assertReviewState(fixture.productId(), fixture.version(), ProductReviewStatus.REVIEW_REQUIRED);

		imageRepository.saveAndFlush(new ProductImage(
			productRepository.findById(fixture.productId()).orElseThrow(),
			ProductImageType.THUMBNAIL,
			"/uploads/products/restored-thumbnail.png",
			0,
			"복구 대표 이미지",
			"review/restored-thumbnail.png"
		));
		Product product = productRepository.findById(fixture.productId()).orElseThrow();
		product.updateComplianceStatus(ProductComplianceStatus.REJECTED);
		productRepository.saveAndFlush(product);

		approve(fixture).andExpect(status().isBadRequest());
		assertReviewState(fixture.productId(), fixture.version(), ProductReviewStatus.REVIEW_REQUIRED);
	}

	@Test
	void supplementRequiresItsReasonAndRejectsEmailPhoneOrUrlWithoutMutation() throws Exception {
		Fixture fixture = reviewRequiredFixture();

		mockMvc.perform(post("/api/admin/product-reviews/{productId}/supplement", fixture.productId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(feedback(fixture.version(), "CATEGORY_REVIEW", "인증 정보를 보완해 주세요.", "Structured information missing")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
		assertReviewState(fixture.productId(), fixture.version(), ProductReviewStatus.REVIEW_REQUIRED);

		List<PiiCase> piiCases = List.of(
			new PiiCase("contact supplier@example.com", "Structured information missing"),
			new PiiCase("call 010-1234-5678", "Structured information missing"),
			new PiiCase("see https://example.com", "Structured information missing"),
			new PiiCase("인증 정보를 보완해 주세요.", "contact reviewer@example.com"),
			new PiiCase("인증 정보를 보완해 주세요.", "call 010-1234-5678"),
			new PiiCase("인증 정보를 보완해 주세요.", "see https://example.com")
		);
		for (PiiCase pii : piiCases) {
			mockMvc.perform(post("/api/admin/product-reviews/{productId}/supplement", fixture.productId())
					.with(authentication(TestAuthentication.admin()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(feedback(fixture.version(), "SUPPLEMENT_REQUIRED", pii.supplierMessage(), pii.internalReason())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));
			assertReviewState(fixture.productId(), fixture.version(), ProductReviewStatus.REVIEW_REQUIRED);
		}

		String internalReason = "Structured certification information missing";
		mockMvc.perform(post("/api/admin/product-reviews/{productId}/supplement", fixture.productId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(feedback(fixture.version(), "SUPPLEMENT_REQUIRED", "필수 인증번호를 보완해 주세요.", internalReason)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus", is("SUPPLEMENT_REQUESTED")))
			.andExpect(jsonPath("$.reviewReasonCode", is("SUPPLEMENT_REQUIRED")))
			.andExpect(jsonPath("$.supplierReviewMessage", is("필수 인증번호를 보완해 주세요.")));

		mockMvc.perform(get("/api/supplier/products/{productId}", fixture.productId())
				.with(authentication(supplierAuthentication(fixture.managerUserId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplierDisplayStatus", is("CHANGES_REQUESTED")))
			.andExpect(jsonPath("$.reviewReasonCode", is("SUPPLEMENT_REQUIRED")))
			.andExpect(jsonPath("$.reviewMessage", is("필수 인증번호를 보완해 주세요.")))
			.andExpect(jsonPath("$.internalReason").doesNotExist())
			.andExpect(jsonPath("$.reviewedByAdminId").doesNotExist())
			.andExpect(content().string(not(containsString(internalReason))));
	}

	@Test
	void rejectedProductCannotBeEditedOrSubmittedByTheSupplier() throws Exception {
		Fixture fixture = reviewRequiredFixture();

		mockMvc.perform(post("/api/admin/product-reviews/{productId}/reject", fixture.productId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(feedback(fixture.version(), "REJECTED_POLICY", "현재 판매 정책상 등록할 수 없는 상품입니다.", "Prohibited catalog category")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is((int) fixture.version() + 1)))
			.andExpect(jsonPath("$.reviewStatus", is("REJECTED")))
			.andExpect(jsonPath("$.status", is("HIDDEN")));

		long rejectedVersion = fixture.version() + 1;
		Authentication supplier = supplierAuthentication(fixture.managerUserId());
		mockMvc.perform(patch("/api/supplier/products/{productId}", fixture.productId())
				.with(authentication(supplier))
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
					"expectedVersion", rejectedVersion,
					"name", "Rejected edit",
					"summary", "Summary",
					"sourcePrice", 1000,
					"minimumOrderQuantity", 1,
					"orderQuantityStep", 1,
					"categoryCode", "SMART_WATCH"
				))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("CONFLICT")));

		mockMvc.perform(post("/api/supplier/products/{productId}/submit", fixture.productId())
				.with(authentication(supplierAuthentication(fixture.managerUserId())))
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("expectedVersion", rejectedVersion))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("CONFLICT")));

		assertReviewState(fixture.productId(), rejectedVersion, ProductReviewStatus.REJECTED);
		mockMvc.perform(get("/api/supplier/products/{productId}", fixture.productId())
				.with(authentication(supplierAuthentication(fixture.managerUserId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplierDisplayStatus", is("REJECTED")))
			.andExpect(jsonPath("$.nextAction", is("CONTACT_COREABLE")))
			.andExpect(jsonPath("$.reviewReasonCode", is("REJECTED_POLICY")))
			.andExpect(jsonPath("$.reviewMessage", is("현재 판매 정책상 등록할 수 없는 상품입니다.")))
			.andExpect(jsonPath("$.internalReason").doesNotExist())
			.andExpect(jsonPath("$.reviewedByAdminId").doesNotExist());
	}

	@Test
	void coreableSaleStopRemainsSeparateFromApprovalAndCannotBeOverriddenBySupplierEdit() throws Exception {
		Fixture fixture = reviewRequiredFixture();
		mockMvc.perform(post("/api/admin/product-reviews/{productId}/approve", fixture.productId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
					"expectedVersion", fixture.version(),
					"internalReason", "Required evidence verified"
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.reviewStatus", is("APPROVED")))
			.andExpect(jsonPath("$.status", is("ACTIVE")));

		long approvedVersion = fixture.version() + 1;
		mockMvc.perform(patch("/api/admin/products/{productId}/status", fixture.productId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
					"status", "STOPPED",
					"reason", "Coreable sale hold",
					"expectedVersion", approvedVersion
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is((int) approvedVersion + 1)))
			.andExpect(jsonPath("$.status", is("STOPPED")));

		long stoppedVersion = approvedVersion + 1;
		mockMvc.perform(get("/api/supplier/products/{productId}", fixture.productId())
				.with(authentication(supplierAuthentication(fixture.managerUserId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplierDisplayStatus", is("PAUSED_BY_COREABLE")))
			.andExpect(jsonPath("$.nextAction", is("CONTACT_COREABLE")));

		mockMvc.perform(patch("/api/supplier/products/{productId}", fixture.productId())
				.with(authentication(supplierAuthentication(fixture.managerUserId())))
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
					"expectedVersion", stoppedVersion,
					"name", "Supplier cannot resume",
					"summary", "Summary",
					"sourcePrice", 1000,
					"minimumOrderQuantity", 1,
					"orderQuantityStep", 1,
					"categoryCode", "PPE_WORK_GLOVES"
				))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("CONFLICT")));

		Product stopped = productRepository.findById(fixture.productId()).orElseThrow();
		assertThat(stopped.getVersion()).isEqualTo(stoppedVersion);
		assertThat(stopped.getStatus()).isEqualTo(ProductStatus.STOPPED);
		assertThat(stopped.getReviewStatus()).isEqualTo(ProductReviewStatus.APPROVED);
	}

	@Test
	void adminPresentationImageReplacementPreservesSupplierDetailImageAndQueuesOwnedThumbnailCleanup() throws Exception {
		Fixture fixture = reviewRequiredFixture();

		mockMvc.perform(put("/api/admin/products/{productId}/images", fixture.productId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
					"expectedVersion", fixture.version(),
					"reason", "Replace presentation image",
					"images", List.of(Map.of(
						"type", "THUMBNAIL",
						"imageUrl", "https://cdn.example.com/replacement.png",
						"sortOrder", 0,
						"altText", "교체 대표 이미지"
					))
				))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.images", hasSize(2)))
			.andExpect(jsonPath("$.images[0].type", is("THUMBNAIL")))
			.andExpect(jsonPath("$.images[0].imageUrl", is("https://cdn.example.com/replacement.png")))
			.andExpect(jsonPath("$.images[1].type", is("DETAIL")))
			.andExpect(jsonPath("$.images[1].imageUrl", is("/uploads/products/detail.png")));

		assertThat(imageRepository.findAllByProduct_IdOrderBySortOrderAsc(fixture.productId()))
			.extracting(ProductImage::getStorageObjectKey)
			.containsExactly(null, "review/detail.png");
		assertThat(cleanupJobRepository.findByStorageObjectKey("review/thumbnail.png"))
			.isPresent()
			.get()
			.extracting(job -> job.getSubjectProductId())
			.isEqualTo(fixture.productId());
		assertThat(cleanupJobRepository.findByStorageObjectKey("review/detail.png")).isEmpty();
	}

	@Test
	void legacyAdminMutationsRepricePortalProductsAndInvalidateReview() throws Exception {
		Fixture fixture = reviewRequiredFixture();
		UUID supplierId = productRepository.findById(fixture.productId()).orElseThrow().getSupplier().getId();
		UUID optionId = optionRepository.findAllByProduct_IdOrderBySortOrderAscCreatedAtAsc(fixture.productId())
			.get(0).getId();

		mockMvc.perform(put("/api/admin/pricing-policy")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name":"Portal regression policy",
					  "commissionRate":5.00,
					  "taxBufferRate":5.00,
					  "overheadRate":5.00,
					  "safetyMarginRate":5.00,
					  "roundingUnit":100
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(2)));

		mockMvc.perform(patch("/api/admin/products/{productId}/options/{optionId}", fixture.productId(), optionId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name":"기본",
					  "additionalPrice":999999,
					  "status":"ACTIVE",
					  "reason":"Portal option repricing regression",
					  "sourceOptionCode":"BASE",
					  "sourceAdditionalPrice":50,
					  "sortOrder":0,
					  "expectedVersion":1
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.productVersion", is(2)))
			.andExpect(jsonPath("$.additionalPrice", is(100)));

		Product afterOptionMutation = productRepository.findById(fixture.productId()).orElseThrow();
		assertThat(afterOptionMutation.getBasePrice()).isEqualTo(1200);
		assertThat(afterOptionMutation.getPricingPolicyVersionApplied()).isEqualTo(2);
		assertThat(afterOptionMutation.getReviewStatus()).isEqualTo(ProductReviewStatus.DRAFT);
		assertThat(afterOptionMutation.getStatus()).isEqualTo(ProductStatus.HIDDEN);

		mockMvc.perform(post("/api/supplier/products/{productId}/submit", fixture.productId())
				.with(authentication(supplierAuthentication(fixture.managerUserId())))
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"expectedVersion\":2}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(3)))
			.andExpect(jsonPath("$.supplierDisplayStatus", is("UNDER_REVIEW")));

		mockMvc.perform(patch("/api/admin/products/{productId}", fixture.productId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId":"%s",
					  "name":"Review Product repriced",
					  "summary":"Structured review summary",
					  "sourcePrice":2050,
					  "basePrice":1,
					  "categoryCode":"SMART_WATCH",
					  "reason":"Portal product repricing regression",
					  "expectedVersion":3
					}
					""".formatted(supplierId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(4)))
			.andExpect(jsonPath("$.basePrice", is(2500)));

		Product repriced = productRepository.findById(fixture.productId()).orElseThrow();
		assertThat(repriced.getPricingPolicyVersionApplied())
			.isEqualTo(pricingPolicyRepository.findFirstByActiveTrueOrderByCreatedAtAsc().orElseThrow().getVersion());
		assertThat(repriced.getReviewStatus()).isEqualTo(ProductReviewStatus.DRAFT);
		assertThat(repriced.getStatus()).isEqualTo(ProductStatus.HIDDEN);
		assertThat(optionRepository.findById(optionId)).get()
			.extracting(ProductOption::getAdditionalPrice)
			.isEqualTo(0L);
	}

	private Fixture reviewRequiredFixture() {
		UUID managerUserId = UUID.randomUUID();
		Instant now = Instant.now();
		Supplier supplier = Supplier.portalApplicant(
			"Review Supplier", "Manager", "010-0000-0000", "review@supplier.example", null
		);
		supplier.verifyPortalContract(
			"review-contract", now.minusSeconds(60), now.plusSeconds(3600), now, TestAuthentication.ADMIN_ID
		);
		supplier.changeSalesStatus(SupplierStatus.ACTIVE, now);
		supplier.bindManager(managerUserId, now);
		supplier = supplierRepository.saveAndFlush(supplier);

		Product product = new Product(
			supplier,
			"Review Product",
			"Structured review summary",
			1000,
			1300,
			ProductCategory.SMART_WATCH,
			ProductStatus.HIDDEN,
			ProductManagementChannel.SUPPLIER_PORTAL
		);
		product.updateReview(ProductReviewStatus.REVIEW_REQUIRED, ProductReviewReasonCode.CATEGORY_REVIEW, REVIEW_MESSAGE);
		product.markFirstSubmitted(now);
		product.incrementVersion();
		product = productRepository.saveAndFlush(product);

		optionRepository.saveAndFlush(new ProductOption(
			product, "기본", 0, ProductOptionStatus.ACTIVE, "BASE", 0L, null, 0
		));
		ProductImage thumbnail = imageRepository.saveAndFlush(new ProductImage(
			product, ProductImageType.THUMBNAIL, "/uploads/products/thumbnail.png", 0, "대표 이미지", "review/thumbnail.png"
		));
		ProductImage detailImage = imageRepository.saveAndFlush(new ProductImage(
			product, ProductImageType.DETAIL, "/uploads/products/detail.png", 1, "상세 이미지", "review/detail.png"
		));
		assertThat(thumbnail.getId()).isNotNull();
		detailBlockRepository.saveAndFlush(new ProductDetailBlock(
			product, ProductDetailBlockType.HTML, null, "<p>안전 상세</p>", 0, null
		));
		detailBlockRepository.saveAndFlush(new ProductDetailBlock(product, detailImage, 1, "상세 이미지"));
		noticeRepository.saveAndFlush(new ProductNotice(
			product,
			1,
			"상품정보 고시",
			"배송 안내",
			"A/S 안내",
			"반품·교환 안내",
			List.of(new ProductNoticeRow("인증번호", "SAFE-123"))
		));
		return new Fixture(product.getId(), product.getVersion(), managerUserId);
	}

	private void assertReviewState(UUID productId, long version, ProductReviewStatus status) {
		Product product = productRepository.findById(productId).orElseThrow();
		assertThat(product.getVersion()).isEqualTo(version);
		assertThat(product.getReviewStatus()).isEqualTo(status);
	}

	private Authentication supplierAuthentication(UUID userId) {
		return new UsernamePasswordAuthenticationToken(
			new AuthenticatedUser(userId, UserRole.CUSTOMER),
			null,
			List.of(new SimpleGrantedAuthority("ROLE_SUPPLIER"))
		);
	}

	private String feedback(long version, String reasonCode, String supplierMessage, String internalReason) throws Exception {
		return json(Map.of(
			"expectedVersion", version,
			"reviewReasonCode", reasonCode,
			"supplierReviewMessage", supplierMessage,
			"internalReason", internalReason
		));
	}

	private ResultActions approve(Fixture fixture) throws Exception {
		return mockMvc.perform(post("/api/admin/product-reviews/{productId}/approve", fixture.productId())
			.with(authentication(TestAuthentication.admin()))
			.contentType(MediaType.APPLICATION_JSON)
			.content(json(Map.of(
				"expectedVersion", fixture.version(),
				"internalReason", "Required evidence verified"
			))));
	}

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}

	private record Fixture(UUID productId, long version, UUID managerUserId) {
	}

	private record PiiCase(String supplierMessage, String internalReason) {
	}
}
