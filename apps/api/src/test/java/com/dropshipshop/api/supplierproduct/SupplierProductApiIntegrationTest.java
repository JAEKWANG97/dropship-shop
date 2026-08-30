package com.dropshipshop.api.supplierproduct;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.dropshipshop.api.auth.JwtAccessTokenService;
import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.PricingPolicy;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductChangeHistory;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductImage;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.catalog.repository.PricingPolicyRepository;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.storage.FileStorage;
import com.dropshipshop.api.common.storage.StoredFile;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = {
	"app.supplier-portal.enabled=true",
	"spring.datasource.url=jdbc:h2:mem:supplier_product_api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SupplierProductApiIntegrationTest {

	private static final String ORIGIN = "http://localhost:3000";
	private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Autowired MockMvc mockMvc;
	@Autowired JwtAccessTokenService jwtAccessTokenService;
	@Autowired UserAccountRepository userRepository;
	@Autowired SupplierRepository supplierRepository;
	@Autowired ProductRepository productRepository;
	@Autowired ProductImageRepository imageRepository;
	@Autowired PricingPolicyRepository pricingPolicyRepository;
	@Autowired ProductChangeHistoryRepository historyRepository;
	@Autowired ObjectMapper objectMapper;
	@MockitoBean FileStorage fileStorage;

	@Test
	void completesMultipartDetailNoticeAndAutoPublishLifecycle() throws Exception {
		Manager manager = manager("complete-lifecycle");
		Cookie token = accessToken(manager);
		UUID productId = UUID.fromString(createProduct(token));
		when(fileStorage.store(anyString(), any()))
			.thenReturn(
				new StoredFile(
					"/uploads/products/lifecycle-thumbnail.png",
					"products/lifecycle-thumbnail.png",
					68,
					"image/png"
				),
				new StoredFile(
					"/uploads/products/lifecycle-detail.png",
					"products/lifecycle-detail.png",
					68,
					"image/png"
				)
			);

		mockMvc.perform(multipart("/api/supplier/products/{productId}/images", productId)
				.file(validPng())
				.param("type", "THUMBNAIL")
				.param("altText", "대표 이미지")
				.param("expectedVersion", "0")
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.version", is(1)))
			.andExpect(jsonPath("$.images[0].type", is("THUMBNAIL")));

		MvcResult detailUpload = mockMvc.perform(multipart("/api/supplier/products/{productId}/images", productId)
				.file(validPng())
				.param("type", "DETAIL")
				.param("altText", "상세 이미지")
				.param("expectedVersion", "1")
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.version", is(2)))
			.andReturn();
		JsonNode detailBody = objectMapper.readTree(detailUpload.getResponse().getContentAsString());
		String detailImageId = null;
		for (JsonNode image : detailBody.path("images")) {
			if ("DETAIL".equals(image.path("type").asText())) {
				detailImageId = image.path("id").asText();
				break;
			}
		}
		assertThat(detailImageId).isNotBlank();

		mockMvc.perform(put("/api/supplier/products/{productId}/detail-blocks", productId)
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedVersion":2,"detailBlocks":[
					  {"type":"IMAGE","productImageId":"%s","sortOrder":0,"altText":"상세 이미지"}
					]}
					""".formatted(detailImageId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(3)))
			.andExpect(jsonPath("$.detailBlocks[0].productImageId", is(detailImageId)));

		mockMvc.perform(put("/api/supplier/products/{productId}/notice", productId)
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedVersion":3,"productInfoNotice":"상품 정보","shippingInfo":"배송 정보",
					"asInfo":"AS 정보","returnExchangeInfo":"반품 교환 정보",
					"noticeRows":[{"label":"재질","value":"면"}]}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(4)))
			.andExpect(jsonPath("$.productNotice.noticeRows[0].label", is("재질")));

		mockMvc.perform(post("/api/supplier/products/{productId}/submit", productId)
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"expectedVersion\":4}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(5)))
			.andExpect(jsonPath("$.supplierDisplayStatus", is("APPROVED")))
			.andExpect(jsonPath("$.nextAction", is("NONE")))
			.andExpect(jsonPath("$.firstSubmittedAt").isNotEmpty());

		mockMvc.perform(get("/api/products/{productId}", productId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id", is(productId.toString())))
			.andExpect(jsonPath("$.sourcePrice").doesNotExist())
			.andExpect(jsonPath("$.supplierId").doesNotExist());
	}

	@Test
	void scopesPortalProductsRejectsPriceSpoofAndEnforcesAggregateVersion() throws Exception {
		Manager manager = manager("primary");
		Cookie token = new Cookie("ACCESS_TOKEN", jwtAccessTokenService.issue(manager.user()));

		mockMvc.perform(post("/api/supplier/products")
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(productRequest("\"basePrice\":999,")))
			.andExpect(status().isBadRequest());

		String productId = mockMvc.perform(post("/api/supplier/products")
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(productRequest("")))
			.andExpect(status().isCreated())
			.andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
			.andExpect(jsonPath("$.version", is(0)))
			.andExpect(jsonPath("$.basePrice").doesNotExist())
			.andExpect(jsonPath("$.status").doesNotExist())
			.andExpect(jsonPath("$.managementChannel").doesNotExist())
			.andExpect(jsonPath("$.options[0].additionalPrice").doesNotExist())
			.andExpect(jsonPath("$.options[0].status").doesNotExist())
			.andReturn().getResponse().getContentAsString()
			.replaceFirst("^\\{\"id\":\"([^\"]+)\".*", "$1");

		mockMvc.perform(patch("/api/supplier/products/{productId}", productId)
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateRequest(0)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(1)));

		mockMvc.perform(patch("/api/supplier/products/{productId}", productId)
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateRequest(0)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code", is("PRODUCT_VERSION_CONFLICT")));

		mockMvc.perform(delete("/api/supplier/products/{productId}", productId)
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN))
			.andExpect(status().isPreconditionRequired())
			.andExpect(jsonPath("$.code", is("PRODUCT_VERSION_REQUIRED")));

		Product coreable = productRepository.saveAndFlush(new Product(
			manager.supplier(), "Coreable", "Summary", 1000, ProductCategory.PPE_WORK_GLOVES, ProductStatus.HIDDEN
		));
		mockMvc.perform(get("/api/supplier/products/{productId}", coreable.getId()).cookie(token))
			.andExpect(status().isNotFound());
	}

	@Test
	void hidesAnotherSuppliersProductFromReadsAndMutations() throws Exception {
		Manager owner = manager("tenant-owner");
		Manager other = manager("tenant-other");
		Cookie ownerToken = accessToken(owner);
		Cookie otherToken = accessToken(other);
		String productId = createProduct(ownerToken);

		mockMvc.perform(get("/api/supplier/products/{productId}", productId).cookie(otherToken))
			.andExpect(status().isNotFound());

		mockMvc.perform(patch("/api/supplier/products/{productId}", productId)
				.cookie(otherToken)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateRequest(0)))
			.andExpect(status().isNotFound());

		assertThat(productRepository.findById(UUID.fromString(productId))).get()
			.extracting(Product::getName)
			.isEqualTo("Portal Product");
	}

	@Test
	void sanitizesSupplierHtmlAndRequiresImagesToUseOwnedDetailBlocks() throws Exception {
		Manager manager = manager("safe-html");
		Cookie token = accessToken(manager);
		String productId = createProduct(token);

		mockMvc.perform(put("/api/supplier/products/{productId}/detail-blocks", productId)
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedVersion":0,"detailBlocks":[{"type":"HTML","sortOrder":0,
					"htmlContent":"<p onclick='steal()'>safe</p><script>alert(1)</script><img src='https://tracker.example/pixel'><a href='javascript:steal()'>link</a>"}]}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(1)))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", containsString("<p>safe</p>")))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("onclick"))))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("<script"))))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("<img"))))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("javascript:"))));
	}

	@Test
	void rejectsOversizedMultipartAltTextBeforeFileOrDatabaseMutation() throws Exception {
		Manager manager = manager("image-alt-limit");
		Cookie token = accessToken(manager);
		UUID productId = UUID.fromString(createProduct(token));
		int historyCount = historyRepository.findAllBySubjectProductIdOrderByCreatedAtAsc(productId).size();

		mockMvc.perform(multipart("/api/supplier/products/{productId}/images", productId)
				.file(validPng())
				.param("type", "THUMBNAIL")
				.param("altText", "x".repeat(201))
				.param("expectedVersion", "0")
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

		verify(fileStorage, never()).store(anyString(), any());
		assertThat(imageRepository.findAllByProduct_IdOrderBySortOrderAsc(productId)).isEmpty();
		assertThat(productRepository.findById(productId)).get()
			.extracting(Product::getVersion)
			.isEqualTo(0L);
		assertThat(historyRepository.findAllBySubjectProductIdOrderByCreatedAtAsc(productId))
			.hasSize(historyCount);
	}

	@Test
	void deletesStoredObjectWhenImageDatabaseFlushFails() throws Exception {
		Manager manager = manager("image-db-failure");
		Cookie token = accessToken(manager);
		UUID productId = UUID.fromString(createProduct(token));
		int historyCount = historyRepository.findAllBySubjectProductIdOrderByCreatedAtAsc(productId).size();
		String storedKey = "products/db-failure/orphan.png";
		when(fileStorage.store(anyString(), any())).thenReturn(new StoredFile(
			"https://cdn.example.com/" + "x".repeat(1001), storedKey, 68, "image/png"
		));

		mockMvc.perform(multipart("/api/supplier/products/{productId}/images", productId)
				.file(validPng())
				.param("type", "THUMBNAIL")
				.param("altText", "Valid alt text")
				.param("expectedVersion", "0")
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN))
			.andExpect(status().isConflict());

		verify(fileStorage).delete(storedKey);
		assertThat(imageRepository.findAllByProduct_IdOrderBySortOrderAsc(productId)).isEmpty();
		assertThat(productRepository.findById(productId)).get()
			.extracting(Product::getVersion)
			.isEqualTo(0L);
		assertThat(historyRepository.findAllBySubjectProductIdOrderByCreatedAtAsc(productId))
			.hasSize(historyCount);
	}

	@Test
	void promotesGalleryBeforeLaterSortedThumbnailWithoutUniqueConflict() throws Exception {
		Manager manager = manager("thumbnail-swap");
		Cookie token = accessToken(manager);
		UUID productId = UUID.fromString(createProduct(token));
		Product product = productRepository.findById(productId).orElseThrow();
		ProductImage gallery = imageRepository.saveAndFlush(new ProductImage(
			product, ProductImageType.GALLERY, "/uploads/products/gallery.png", 0, "Gallery", "gallery.png"
		));
		ProductImage oldThumbnail = imageRepository.saveAndFlush(new ProductImage(
			product, ProductImageType.THUMBNAIL, "/uploads/products/old-thumbnail.png", 1, "Old", "old-thumbnail.png"
		));
		product.updateThumbnailImageUrl(oldThumbnail.getImageUrl());
		productRepository.saveAndFlush(product);

		mockMvc.perform(put("/api/supplier/products/{productId}/images/order", productId)
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedVersion":0,"images":[
					  {"imageId":"%s","type":"THUMBNAIL","sortOrder":0,"altText":"New thumbnail"},
					  {"imageId":"%s","type":"GALLERY","sortOrder":1,"altText":"Old thumbnail"}
					]}
					""".formatted(gallery.getId(), oldThumbnail.getId())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(1)))
			.andExpect(jsonPath("$.images[0].id", is(gallery.getId().toString())))
			.andExpect(jsonPath("$.images[0].type", is("THUMBNAIL")))
			.andExpect(jsonPath("$.images[1].id", is(oldThumbnail.getId().toString())))
			.andExpect(jsonPath("$.images[1].type", is("GALLERY")));

		assertThat(productRepository.findById(productId)).get()
			.satisfies(reordered -> {
				assertThat(reordered.getVersion()).isEqualTo(1);
				assertThat(reordered.getThumbnailImageUrl()).isEqualTo(gallery.getImageUrl());
			});
	}

	@Test
	void preservesTheAppliedCalculatorSnapshotAcrossInPlacePolicyUpdates() throws Exception {
		Manager manager = manager("pricing-snapshot");
		Cookie token = accessToken(manager);
		UUID productId = UUID.fromString(createProduct(token));
		PricingPolicy original = pricingPolicyRepository.findFirstByActiveTrueOrderByCreatedAtAsc().orElseThrow();
		long initialVersion = original.getVersion();
		BigDecimal originalCommission = original.getCommissionRate();
		BigDecimal changedCommission = originalCommission.add(BigDecimal.ONE);

		ProductChangeHistory created = historyRepository.findAllBySubjectProductIdOrderByCreatedAtAsc(productId).get(0);
		assertThat(created.getAfterValue())
			.contains("policyVersion=" + initialVersion)
			.contains("commission=" + originalCommission);

		try {
			updatePricingPolicy(original, changedCommission);

			mockMvc.perform(patch("/api/supplier/products/{productId}", productId)
					.cookie(token)
					.header(HttpHeaders.ORIGIN, ORIGIN)
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateRequest(0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version", is(1)));

			List<ProductChangeHistory> histories = historyRepository
				.findAllBySubjectProductIdOrderByCreatedAtAsc(productId);
			ProductChangeHistory updated = histories.get(histories.size() - 1);
			assertThat(updated.getBeforeValue())
				.contains("policyVersion=" + initialVersion)
				.doesNotContain("commission=");
			assertThat(updated.getAfterValue())
				.contains("policyVersion=" + (initialVersion + 1))
				.contains("commission=" + changedCommission);
		} finally {
			updatePricingPolicy(original, originalCommission);
		}
	}

	@ParameterizedTest(name = "rejects forbidden supplier product field: {0}")
	@MethodSource("forbiddenCreateFields")
	void rejectsForbiddenCreateFieldsWithoutPersistingAnything(String name, String field, String suffix) throws Exception {
		Manager manager = manager("forbidden-" + suffix);
		long productCount = productRepository.count();

		mockMvc.perform(post("/api/supplier/products")
				.cookie(accessToken(manager))
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(productRequest(field)))
			.andExpect(status().isBadRequest());

		assertThat(productRepository.count()).as(name).isEqualTo(productCount);
	}

	@Test
	void rejectsSupplierCostAboveTheSupportedUnitCapWithoutMutation() throws Exception {
		Manager manager = manager("cost-cap");
		Cookie token = accessToken(manager);
		long productCount = productRepository.count();

		mockMvc.perform(post("/api/supplier/products")
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(productRequest(100_000_001L)))
			.andExpect(status().isBadRequest());
		assertThat(productRepository.count()).isEqualTo(productCount);

		String productId = createProduct(token);
		mockMvc.perform(post("/api/supplier/products/{productId}/options", productId)
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expectedVersion":0,"name":"Too expensive","sourceOptionCode":"CAP",
					"sourceAdditionalPrice":100000001,"sortOrder":1}
					"""))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/supplier/products/{productId}", productId).cookie(token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is(0)))
			.andExpect(jsonPath("$.options", org.hamcrest.Matchers.hasSize(1)));
	}

	private static Stream<Arguments> forbiddenCreateFields() {
		return Stream.of(
			Arguments.of("supplierId", "\"supplierId\":\"00000000-0000-0000-0000-000000000099\",", "supplier"),
			Arguments.of("status", "\"status\":\"ACTIVE\",", "status"),
			Arguments.of("complianceStatus", "\"complianceStatus\":\"VERIFIED\",", "compliance"),
			Arguments.of("reviewStatus", "\"reviewStatus\":\"APPROVED\",", "review"),
			Arguments.of("sourceItemNo", "\"sourceItemNo\":\"foreign-source\",", "source"),
			Arguments.of("inventoryMode", "\"inventoryMode\":\"UNTRACKED\",", "inventory-mode"),
			Arguments.of("onHandQuantity", "\"onHandQuantity\":10,", "inventory-quantity")
		);
	}

	private void updatePricingPolicy(PricingPolicy policy, BigDecimal commissionRate) throws Exception {
		mockMvc.perform(put("/api/admin/pricing-policy")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name":"%s",
					  "commissionRate":%s,
					  "taxBufferRate":%s,
					  "overheadRate":%s,
					  "safetyMarginRate":%s,
					  "roundingUnit":%d
					}
					""".formatted(policy.getName(), commissionRate, policy.getTaxBufferRate(),
					policy.getOverheadRate(), policy.getSafetyMarginRate(), policy.getRoundingUnit())))
			.andExpect(status().isOk());
	}

	private String createProduct(Cookie token) throws Exception {
		return mockMvc.perform(post("/api/supplier/products")
				.cookie(token)
				.header(HttpHeaders.ORIGIN, ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content(productRequest("")))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString()
			.replaceFirst("^\\{\"id\":\"([^\"]+)\".*", "$1");
	}

	private Cookie accessToken(Manager manager) {
		return new Cookie("ACCESS_TOKEN", jwtAccessTokenService.issue(manager.user()));
	}

	private MockMultipartFile validPng() {
		return new MockMultipartFile(
			"file",
			"valid.png",
			"image/png",
			Base64.getDecoder().decode(
				"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
			)
		);
	}

	private Manager manager(String suffix) {
		UserAccount user = userRepository.saveAndFlush(new UserAccount(
			SocialProvider.KAKAO, "supplier-product-" + suffix, suffix + "@user.example", suffix, UserRole.CUSTOMER
		));
		Supplier supplier = supplierRepository.saveAndFlush(Supplier.portalApplicant(
			"Supplier " + suffix, suffix, "010-0000-0000", suffix + "@supplier.example", null
		));
		Instant now = Instant.now();
		supplier.verifyPortalContract("contract-" + suffix, now.minusSeconds(60), now.plusSeconds(3600), now, ADMIN_ID);
		supplier.changeSalesStatus(SupplierStatus.ACTIVE, now);
		supplier.bindManager(user.getId(), now);
		supplierRepository.saveAndFlush(supplier);
		return new Manager(user, supplier);
	}

	private String productRequest(String extra) {
		return """
			{%s"name":"Portal Product","summary":"Summary","sourcePrice":1000,
			"minimumOrderQuantity":1,"orderQuantityStep":1,"categoryCode":"PPE_WORK_GLOVES"}
			""".formatted(extra);
	}

	private String productRequest(long sourcePrice) {
		return """
			{"name":"Portal Product","summary":"Summary","sourcePrice":%d,
			"minimumOrderQuantity":1,"orderQuantityStep":1,"categoryCode":"PPE_WORK_GLOVES"}
			""".formatted(sourcePrice);
	}

	private String updateRequest(long version) {
		return """
			{"expectedVersion":%d,"name":"Updated Product","summary":"Summary","sourcePrice":1200,
			"minimumOrderQuantity":1,"orderQuantityStep":1,"categoryCode":"PPE_WORK_GLOVES"}
			""".formatted(version);
	}

	private record Manager(UserAccount user, Supplier supplier) {
	}
}
