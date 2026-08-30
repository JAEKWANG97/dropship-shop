package com.dropshipshop.api.catalog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.cleanup.ProductImageCleanupService;
import com.dropshipshop.api.catalog.domain.ProductImage;
import com.dropshipshop.api.catalog.domain.ProductImageCleanupStatus;
import com.dropshipshop.api.catalog.domain.ProductImageType;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;
import com.dropshipshop.api.catalog.repository.ProductImageCleanupJobRepository;
import com.dropshipshop.api.catalog.repository.ProductImageRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.common.storage.FileStorage;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CatalogApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductChangeHistoryRepository productChangeHistoryRepository;

	@Autowired
	private ProductImageRepository productImageRepository;

	@Autowired
	private ProductImageCleanupJobRepository productImageCleanupJobRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductImageCleanupService productImageCleanupService;

	@Autowired
	private FileStorage fileStorage;

	@Test
	void adminSameSoldOutCommandProtectsAFormerSourceSoldOutFromAutomaticRecovery() throws Exception {
		UUID productId = createProduct(
			createSupplier("Manual sold-out supplier"),
			"Manual sold-out product",
			"Manual sold-out summary",
			"PPE_WORK_GLOVES",
			"SOLD_OUT"
		);
		var product = productRepository.findById(productId).orElseThrow();
		product.markSourceSynced(false, Instant.now());
		product.markSourceAutoSoldOut();
		productRepository.saveAndFlush(product);

		mockMvc.perform(patch("/api/admin/products/{productId}/status", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"status":"SOLD_OUT","reason":"Keep this product manually sold out"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("SOLD_OUT")));

		var manuallyProtected = productRepository.findById(productId).orElseThrow();
		assertFalse(manuallyProtected.isSourceAutoSoldOut());
		assertFalse(manuallyProtected.getSourceAvailable());
		org.assertj.core.api.Assertions.assertThat(productRepository.findSourceSyncTargets(
			org.springframework.data.domain.PageRequest.of(0, 100)
		)).noneMatch(candidate -> candidate.getId().equals(productId));
	}

	@Test
	void rejectsAdminMutationsThatExceedTheAggregateCustomerUnitPriceCap() throws Exception {
		UUID supplierId = createSupplier("Price cap supplier");
		MvcResult created = mockMvc.perform(post("/api/admin/products")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId":"%s",
					  "name":"Price cap product",
					  "summary":"Price cap summary",
					  "sourcePrice":100000000,
					  "basePrice":900000000,
					  "categoryCode":"PPE_SAFETY_HELMET",
					  "status":"HIDDEN"
					}
					""".formatted(supplierId)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID productId = idFrom(created);

		mockMvc.perform(post("/api/admin/products/{productId}/options", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Maximum option","additionalPrice":100000000,"status":"ACTIVE"}
					"""))
			.andExpect(status().isCreated());

		mockMvc.perform(patch("/api/admin/products/{productId}", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId":"%s",
					  "name":"Price cap product",
					  "summary":"Price cap summary",
					  "sourcePrice":100000000,
					  "basePrice":900000001,
					  "categoryCode":"PPE_SAFETY_HELMET",
					  "reason":"cap regression"
					}
					""".formatted(supplierId)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/admin/products/{productId}", productId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.basePrice", is(900000000)));
	}

	@Test
	void managesCatalogAndExposesPublicProducts() throws Exception {
		mockMvc.perform(get("/api/products"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products", hasSize(0)))
			.andExpect(jsonPath("$.totalElements", is(0)))
			.andExpect(jsonPath("$.totalPages", is(0)));

		mockMvc.perform(get("/api/admin/products"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/admin/products")
				.with(authentication(TestAuthentication.customer())))
			.andExpect(status().isForbidden());

		UUID supplierId = createSupplier();

		mockMvc.perform(get("/api/admin/pricing-policy")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalMarkupRate", is(25.0)))
			.andExpect(jsonPath("$.roundingUnit", is(100)));

		mockMvc.perform(put("/api/admin/pricing-policy")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "Default margin",
					  "commissionRate": 5.00,
					  "taxBufferRate": 10.00,
					  "overheadRate": 5.00,
					  "safetyMarginRate": 5.00,
					  "roundingUnit": 100
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalMarkupRate", is(25.0)));

		UUID productId = createProduct(supplierId);
		byte[] pngImage = imageBytes("png");
		MockMultipartFile imageFile = new MockMultipartFile(
			"file",
			"thumbnail.png",
			"image/png",
			pngImage
		);
		MvcResult uploadResult = mockMvc.perform(multipart("/api/admin/products/{productId}/images/upload", productId)
				.file(imageFile)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.imageUrl", containsString("/uploads/products/" + productId)))
			.andExpect(jsonPath("$.objectKey", containsString(productId.toString())))
			.andExpect(jsonPath("$.size", is(pngImage.length)))
			.andReturn();
		String uploadedImageUrl = fieldFrom(uploadResult, "imageUrl");

		MvcResult optionResult = mockMvc.perform(post("/api/admin/products/{productId}/options", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "Black / Large",
					  "additionalPrice": 1000,
					  "status": "ACTIVE",
					  "sourceOptionCode": "00",
					  "sourceAdditionalPrice": 800,
					  "sourceStockQuantity": 120,
					  "sortOrder": 2
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status", is("ACTIVE")))
			.andExpect(jsonPath("$.sourceOptionCode", is("00")))
			.andExpect(jsonPath("$.sourceAdditionalPrice", is(800)))
			.andExpect(jsonPath("$.sourceStockQuantity", is(120)))
			.andExpect(jsonPath("$.sortOrder", is(2)))
			.andReturn();
		UUID optionId = idFrom(optionResult);

		mockMvc.perform(get("/api/admin/products/{productId}", productId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplierId", is(supplierId.toString())))
			.andExpect(jsonPath("$.supplierName", is("Supplier A")))
			.andExpect(jsonPath("$.options[0].sourceOptionCode", is("00")))
			.andExpect(jsonPath("$.options[0].sourceStockQuantity", is(120)))
			.andExpect(jsonPath("$.complianceStatus", is("PENDING")))
			.andExpect(jsonPath("$.sourceUrl", is("https://mobile.domeggook.com/8667274")))
			.andExpect(jsonPath("$.minimumOrderQuantity", is(1)))
			.andExpect(jsonPath("$.orderQuantityStep", is(1)))
			.andExpect(jsonPath("$.saleReady", is(false)))
			.andExpect(jsonPath("$.saleBlockers", hasItem("THUMBNAIL")))
			.andExpect(jsonPath("$.saleBlockers", hasItem("PRODUCT_NOTICE")))
			.andExpect(jsonPath("$.optionCount", is(1)))
			.andExpect(jsonPath("$.hasThumbnail", is(false)))
			.andExpect(jsonPath("$.hasProductNotice", is(false)))
			.andExpect(jsonPath("$.hasDetailContent", is(false)));

		mockMvc.perform(put("/api/admin/products/{productId}/images", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Initial image setup",
					  "images": [
					    {
					      "type": "THUMBNAIL",
					      "imageUrl": "%s",
					      "sortOrder": 0,
					      "altText": "Thumbnail"
					    },
					    {
					      "type": "GALLERY",
					      "imageUrl": "https://cdn.example.com/gallery-1.jpg",
					      "sortOrder": 1,
					      "altText": "Gallery"
					    }
					  ]
					}
					""".formatted(uploadedImageUrl)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.thumbnailImageUrl", is(uploadedImageUrl)))
			.andExpect(jsonPath("$.images", hasSize(2)));

		mockMvc.perform(put("/api/admin/products/{productId}/detail-blocks", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Initial detail setup",
					  "detailBlocks": [
					    {
					      "type": "HTML",
					      "htmlContent": "<p onclick='bad()'>Safe</p><script>alert(1)</script>",
					      "sortOrder": 1
					    }
					  ]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.detailVersion", is(2)))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", containsString("<p>Safe</p>")))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("script"))));

		mockMvc.perform(put("/api/admin/products/{productId}/notice", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Initial notice setup",
					  "productInfoNotice": "Product info notice",
					  "noticeRows": [
					    {"label": "품명 및 모델명", "value": "안전모 A"},
					    {"label": "제조국 또는 원산지", "value": "대한민국"}
					  ],
					  "shippingInfo": "Shipping info",
					  "asInfo": "AS info",
					  "returnExchangeInfo": "Return exchange info"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.productNoticeVersion", is(1)))
			.andExpect(jsonPath("$.productNotice.productInfoNotice", is("Product info notice")))
			.andExpect(jsonPath("$.productNotice.noticeRows", hasSize(2)))
			.andExpect(jsonPath("$.productNotice.noticeRows[0].label", is("품명 및 모델명")))
			.andExpect(jsonPath("$.productNotice.noticeRows[0].value", is("안전모 A")));

		mockMvc.perform(patch("/api/admin/products/{productId}", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId": "%s",
					  "name": "Product A",
					  "summary": "Summary",
					  "sourcePrice": 31200,
					  "sourceUrl": "https://mobile.domeggook.com/8667274",
					  "basePrice": 39000,
					  "minimumOrderQuantity": 6,
					  "orderQuantityStep": 6,
					  "categoryCode": "PPE_SAFETY_HELMET",
					  "complianceStatus": "VERIFIED",
					  "reason": "Certification reviewed"
					}
					""".formatted(supplierId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.complianceStatus", is("VERIFIED")))
			.andExpect(jsonPath("$.minimumOrderQuantity", is(6)))
			.andExpect(jsonPath("$.orderQuantityStep", is(6)));

		mockMvc.perform(patch("/api/admin/products/{productId}/status", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "ACTIVE",
					  "reason": "Sale readiness confirmed"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("ACTIVE")))
			.andExpect(jsonPath("$.saleReady", is(true)))
			.andExpect(jsonPath("$.saleBlockers", hasSize(0)))
			.andExpect(jsonPath("$.hasDetailContent", is(true)));

		mockMvc.perform(get("/api/products"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products", hasSize(1)))
			.andExpect(jsonPath("$.products[0].id", is(productId.toString())))
			.andExpect(jsonPath("$.products[0].minimumOrderQuantity", is(6)))
			.andExpect(jsonPath("$.products[0].orderQuantityStep", is(6)))
			.andExpect(jsonPath("$.products[0].sourcePrice").doesNotExist())
			.andExpect(jsonPath("$.page", is(0)))
			.andExpect(jsonPath("$.size", is(24)))
			.andExpect(jsonPath("$.totalElements", is(1)))
			.andExpect(jsonPath("$.totalPages", is(1)))
			.andExpect(jsonPath("$.categoryCounts.PPE_SAFETY_HELMET", is(1)));

		mockMvc.perform(get("/api/products")
				.param("q", "product")
				.param("category", "PPE_SAFETY_HELMET")
				.param("minPrice", "30000")
				.param("maxPrice", "40000")
				.param("sort", "price-desc")
				.param("page", "0")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products", hasSize(1)))
			.andExpect(jsonPath("$.products[0].id", is(productId.toString())));

		for (String query : List.of("page=-1", "size=101", "sort=unknown", "minPrice=40000&maxPrice=30000")) {
			mockMvc.perform(get("/api/products?" + query))
				.andExpect(status().isBadRequest());
		}

		mockMvc.perform(get("/api/products/{productId}", productId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.supplierId").doesNotExist())
			.andExpect(jsonPath("$.supplierName").doesNotExist())
			.andExpect(jsonPath("$.sourcePrice").doesNotExist())
			.andExpect(jsonPath("$.sourceUrl").doesNotExist())
			.andExpect(jsonPath("$.minimumOrderQuantity", is(6)))
			.andExpect(jsonPath("$.orderQuantityStep", is(6)))
			.andExpect(jsonPath("$.salesEnabled", is(true)))
			.andExpect(jsonPath("$.salesNotice").doesNotExist())
			.andExpect(jsonPath("$.complianceStatus", is("VERIFIED")))
			.andExpect(jsonPath("$.saleReady").doesNotExist())
			.andExpect(jsonPath("$.saleBlockers").doesNotExist())
			.andExpect(jsonPath("$.options", hasSize(1)))
			.andExpect(jsonPath("$.options[0].sourceOptionCode").doesNotExist())
			.andExpect(jsonPath("$.options[0].sourceAdditionalPrice").doesNotExist())
			.andExpect(jsonPath("$.options[0].sourceStockQuantity").doesNotExist())
			.andExpect(jsonPath("$.options[0].sortOrder").doesNotExist())
			.andExpect(jsonPath("$.images", hasSize(2)))
			.andExpect(jsonPath("$.productNoticeVersion", is(1)))
			.andExpect(jsonPath("$.productNotice.noticeRows", hasSize(2)))
			.andExpect(jsonPath("$.productNotice.noticeRows[1].value", is("대한민국")));

		mockMvc.perform(put("/api/admin/products/{productId}/notice", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Update legacy notice fields",
					  "productInfoNotice": "Updated product info notice",
					  "shippingInfo": "Updated shipping info",
					  "asInfo": "Updated AS info",
					  "returnExchangeInfo": "Updated return exchange info"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.productNoticeVersion", is(2)))
			.andExpect(jsonPath("$.productNotice.noticeRows", hasSize(2)))
			.andExpect(jsonPath("$.productNotice.noticeRows[0].value", is("안전모 A")));

		mockMvc.perform(patch("/api/admin/products/{productId}/options/{optionId}/status", productId, optionId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "SOLD_OUT",
					  "reason": "Test last option guard"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", containsString("판매 가능한 옵션")));

		mockMvc.perform(put("/api/admin/products/{productId}/images", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "Test thumbnail guard",
					  "images": []
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", containsString("대표 이미지")));

		mockMvc.perform(patch("/api/admin/products/{productId}", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId": "%s",
					  "name": "Product A",
					  "summary": "Summary",
					  "sourcePrice": 31200,
					  "basePrice": 0,
					  "categoryCode": "PPE_SAFETY_HELMET",
					  "complianceStatus": "PENDING",
					  "reason": "Test price and compliance guard"
					}
					""".formatted(supplierId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", containsString("판매가")));

		mockMvc.perform(get("/api/admin/products/{productId}", productId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.basePrice", is(39000)))
			.andExpect(jsonPath("$.minimumOrderQuantity", is(6)))
			.andExpect(jsonPath("$.orderQuantityStep", is(6)))
			.andExpect(jsonPath("$.complianceStatus", is("VERIFIED")))
			.andExpect(jsonPath("$.options[0].status", is("ACTIVE")))
			.andExpect(jsonPath("$.images", hasSize(2)));

		mockMvc.perform(patch("/api/admin/products/{productId}/status", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "HIDDEN",
					  "reason": "Hide from public list"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("HIDDEN")));

		mockMvc.perform(get("/api/admin/products/{productId}/changes", productId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.changes", hasSize(17)))
			.andExpect(jsonPath("$.changes[?(@.changeType == 'IMAGES')]", hasSize(1)))
			.andExpect(jsonPath("$.changes[?(@.changeType == 'ORDER_QUANTITY')].beforeValue", hasItem("1/1")))
			.andExpect(jsonPath("$.changes[?(@.changeType == 'ORDER_QUANTITY')].afterValue", hasItem("6/6")))
			.andExpect(jsonPath("$.changes[?(@.changeType == 'PRODUCT_STATUS')].beforeValue", hasItem("ACTIVE")))
			.andExpect(jsonPath("$.changes[?(@.changeType == 'PRODUCT_STATUS')].afterValue", hasItem("HIDDEN")))
			.andExpect(jsonPath("$.changes[?(@.changeType == 'PRODUCT_STATUS')].adminUserId", hasItem(TestAuthentication.ADMIN_ID.toString())))
			.andExpect(jsonPath("$.changes[?(@.changeType == 'PRODUCT_STATUS')].actorType", hasItem("ADMIN")))
			.andExpect(jsonPath("$.changes[?(@.changeType == 'PRODUCT_STATUS')].beforeVersion", hasItem(5)));

		mockMvc.perform(get("/api/admin/products/{productId}/changes", productId)
				.with(authentication(TestAuthentication.customer())))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/admin/products/{productId}/changes", UUID.randomUUID())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/products/{productId}", productId))
			.andExpect(status().isNotFound());

		org.assertj.core.api.Assertions.assertThat(productChangeHistoryRepository.count()).isGreaterThanOrEqualTo(4);
	}

	@Test
	void scopesPublicCategoryCountsToSearchConditionsWithoutSelectedCategory() throws Exception {
		UUID supplierId = createSupplier();
		createPublicProduct(supplierId, "Blue work glove", "PPE_WELDING_GLOVES", 39000);
		createPublicProduct(supplierId, "Insulated work glove", "PPE_INSULATED_GLOVES", 39000);
		createPublicProduct(supplierId, "Work safety helmet", "PPE_SAFETY_HELMET", 60000);

		mockMvc.perform(get("/api/products")
				.param("q", "work")
				.param("category", "PPE_WELDING_GLOVES")
				.param("maxPrice", "40000"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products", hasSize(1)))
			.andExpect(jsonPath("$.products[0].categoryCode", is("PPE_WELDING_GLOVES")))
			.andExpect(jsonPath("$.categoryCounts.PPE_WELDING_GLOVES", is(1)))
			.andExpect(jsonPath("$.categoryCounts.PPE_INSULATED_GLOVES", is(1)))
			.andExpect(jsonPath("$.categoryCounts.PPE_SAFETY_HELMET", is(0)));
	}

	@Test
	void pagesAndFiltersAdminProducts() throws Exception {
		UUID alphaSupplier = createSupplier("Alpha Safety");
		UUID betaSupplier = createSupplier("Beta Industrial");
		UUID helmetId = createProduct(
			alphaSupplier,
			"Bright safety helmet",
			"Helmet for field work",
			"PPE_SAFETY_HELMET",
			"HIDDEN"
		);
		createProduct(
			alphaSupplier,
			"Safety shoes",
			"Slip resistant footwear",
			"PPE_SAFETY_SHOES",
			"SOLD_OUT"
		);
		UUID coneId = createProduct(
			betaSupplier,
			"Traffic cone",
			"Bright road control equipment",
			"TRAFFIC_CONE",
			"HIDDEN"
		);
		prepareProductForSale(helmetId, alphaSupplier);

		mockMvc.perform(get("/api/admin/products")
				.param("page", "0")
				.param("size", "2")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products", hasSize(2)))
			.andExpect(jsonPath("$.products[0].id", is(coneId.toString())))
			.andExpect(jsonPath("$.page", is(0)))
			.andExpect(jsonPath("$.size", is(2)))
			.andExpect(jsonPath("$.totalElements", is(3)))
			.andExpect(jsonPath("$.totalPages", is(2)));

		mockMvc.perform(get("/api/admin/products")
				.param("page", "1")
				.param("size", "2")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products", hasSize(1)))
			.andExpect(jsonPath("$.products[0].id", is(helmetId.toString())));

		mockMvc.perform(get("/api/admin/products")
				.param("q", "bright")
				.param("status", "HIDDEN")
				.param("category", "PPE_SAFETY_HELMET")
				.param("supplierId", alphaSupplier.toString())
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products", hasSize(1)))
			.andExpect(jsonPath("$.products[0].id", is(helmetId.toString())))
			.andExpect(jsonPath("$.totalElements", is(1)));

		mockMvc.perform(get("/api/admin/products")
				.param("q", "beta industrial")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products", hasSize(1)))
			.andExpect(jsonPath("$.products[0].id", is(coneId.toString())));

		mockMvc.perform(get("/api/admin/products")
				.param("readiness", "READY")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products", hasSize(1)))
			.andExpect(jsonPath("$.products[0].id", is(helmetId.toString())))
			.andExpect(jsonPath("$.products[0].saleReady", is(true)))
			.andExpect(jsonPath("$.totalElements", is(1)));

		mockMvc.perform(get("/api/admin/products")
				.param("readiness", "BLOCKED")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products", hasSize(2)))
			.andExpect(jsonPath("$.products[*].saleReady", hasItem(false)))
			.andExpect(jsonPath("$.totalElements", is(2)));

		for (String query : List.of("page=-1", "size=101", "category=UNKNOWN", "readiness=UNKNOWN")) {
			mockMvc.perform(get("/api/admin/products?" + query)
					.with(authentication(TestAuthentication.admin())))
				.andExpect(status().isBadRequest());
		}
	}

	@Test
	void rejectsActiveCreationAndReportsMissingSaleRequirements() throws Exception {
		UUID supplierId = createSupplier();

		mockMvc.perform(post("/api/admin/products")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId": "%s",
					  "name": "Unsafe active product",
					  "summary": "Missing review data",
					  "sourcePrice": 0,
					  "basePrice": 0,
					  "categoryCode": "PPE_SAFETY_HELMET",
					  "status": "ACTIVE"
					}
					""".formatted(supplierId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", containsString("HIDDEN")));

		mockMvc.perform(post("/api/admin/products")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId": "%s",
					  "name": "Invalid source URL",
					  "summary": "Invalid source URL",
					  "sourcePrice": 1000,
					  "sourceUrl": "javascript:alert(1)",
					  "basePrice": 1300,
					  "categoryCode": "PPE_SAFETY_HELMET",
					  "status": "HIDDEN"
					}
					""".formatted(supplierId)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/api/admin/products")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId": "%s",
					  "name": "Invalid MOQ product",
					  "summary": "Invalid ordering rules",
					  "sourcePrice": 1000,
					  "basePrice": 1300,
					  "minimumOrderQuantity": 100,
					  "orderQuantityStep": 1,
					  "categoryCode": "PPE_SAFETY_HELMET",
					  "status": "HIDDEN"
					}
					""".formatted(supplierId)))
			.andExpect(status().isBadRequest());

		MvcResult hiddenProduct = mockMvc.perform(post("/api/admin/products")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId": "%s",
					  "name": "Pending product",
					  "summary": "Missing review data",
					  "sourcePrice": 0,
					  "basePrice": 0,
					  "categoryCode": "PPE_SAFETY_HELMET",
					  "status": "HIDDEN"
					}
					""".formatted(supplierId)))
			.andExpect(status().isCreated())
			.andReturn();

		mockMvc.perform(get("/api/admin/products/{productId}", idFrom(hiddenProduct))
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.saleReady", is(false)))
			.andExpect(jsonPath("$.saleBlockers", hasSize(4)))
			.andExpect(jsonPath("$.saleBlockers", hasItem("BASE_PRICE")))
			.andExpect(jsonPath("$.saleBlockers", hasItem("THUMBNAIL")))
			.andExpect(jsonPath("$.saleBlockers", hasItem("ACTIVE_OPTION")))
			.andExpect(jsonPath("$.saleBlockers", hasItem("PRODUCT_NOTICE")));

		mockMvc.perform(patch("/api/admin/products/{productId}/status", idFrom(hiddenProduct))
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "ACTIVE",
					  "reason": "Try incomplete activation"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", containsString("판매가")))
			.andExpect(jsonPath("$.message", containsString("대표 이미지")))
			.andExpect(jsonPath("$.message", containsString("판매 가능한 옵션")))
			.andExpect(jsonPath("$.message", containsString("상품 고시")));
	}

	@Test
	void derivesSourceItemNoAndRejectsDuplicateSupplierProduct() throws Exception {
		UUID supplierId = createSupplier();

		mockMvc.perform(post("/api/admin/products")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(productRequest(supplierId, "First supplier product", "64470251", null)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sourceItemNo", is("64470251")));

		mockMvc.perform(post("/api/admin/products")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(productRequest(supplierId, "Duplicate supplier product", "64470251", null)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message", containsString("64470251")));

		mockMvc.perform(post("/api/admin/products")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(productRequest(supplierId, "First supplier product", "64470252", null)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/admin/products")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(productRequest(supplierId, "Mismatched supplier product", "64470253", "99999999")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", containsString("일치하지 않습니다")));

		mockMvc.perform(post("/api/admin/products")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(productRequest(supplierId, "Missing supplier item number", "items", null)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", containsString("확인할 수 없습니다")));
	}

	@Test
	void allowsOnlyOneConcurrentCreationForSameSourceItemNo() throws Exception {
		UUID supplierId = createSupplier();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			List<Future<Integer>> requests = List.of("Concurrent A", "Concurrent B").stream()
				.map(name -> executor.submit(() -> {
					ready.countDown();
					start.await();
					return mockMvc.perform(post("/api/admin/products")
							.with(authentication(TestAuthentication.admin()))
							.contentType(MediaType.APPLICATION_JSON)
							.content(productRequest(supplierId, name, "64470254", null)))
						.andReturn()
						.getResponse()
						.getStatus();
				}))
				.toList();
			ready.await();
			start.countDown();
			List<Integer> statuses = requests.stream().map(this::futureResult).sorted().toList();
			assertEquals(List.of(201, 409), statuses);
		}
	}

	@Test
	void sanitizesProductDetailHtmlWithSafelist() throws Exception {
		UUID productId = createProduct(createSupplier());

		mockMvc.perform(put("/api/admin/products/{productId}/detail-blocks", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason": "XSS regression",
					  "detailBlocks": [
					    {
					      "type": "HTML",
					      "htmlContent": "<div><p>Safe <b>Bold</b></p><img src=x onerror=alert(1)><svg onload=alert(2)></svg><a href='javascript:alert(3)'>bad-js</a><a href='data:text/html;base64,PHNjcmlwdD4='>bad-data</a><a href='https://example.com/ok'>good-link</a><iframe src='https://evil.example'></iframe><script>alert(4)</script><span onclick='bad()' style='color:red'>plain</span><img src='https://cdn.example.com/ok.png' alt='ok' onload='bad()'></div>",
					      "sortOrder": 1
					    }
					  ]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", containsString("<p>Safe <b>Bold</b></p>")))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", containsString("href=\"https://example.com/ok\"")))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", containsString("src=\"https://cdn.example.com/ok.png\"")))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", containsString("alt=\"ok\"")))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("onerror"))))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("onload"))))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("onclick"))))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("style="))))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("javascript:"))))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("data:"))))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("<svg"))))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("<iframe"))))
			.andExpect(jsonPath("$.detailBlocks[0].htmlContent", not(containsString("<script"))));
	}

	@Test
	void rejectsDisguisedImageUpload() throws Exception {
		UUID productId = createProduct(createSupplier());
		MockMultipartFile fakeImage = new MockMultipartFile(
			"file",
			"fake.png",
			"image/png",
			"<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)
		);

		mockMvc.perform(multipart("/api/admin/products/{productId}/images/upload", productId)
				.file(fakeImage)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isBadRequest());
	}

	@Test
	void uploadsValidPngJpegAndWebpAndServesNosniffHeader() throws Exception {
		UUID productId = createProduct(createSupplier());
		byte[] pngImage = imageBytes("png");
		byte[] jpegImage = imageBytes("jpeg");
		byte[] webpImage = webpImageBytes();

		MvcResult pngUpload = mockMvc.perform(multipart("/api/admin/products/{productId}/images/upload", productId)
				.file(new MockMultipartFile("file", "safe.png", "image/png", pngImage))
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size", is(pngImage.length)))
			.andReturn();

		mockMvc.perform(multipart("/api/admin/products/{productId}/images/upload", productId)
				.file(new MockMultipartFile("file", "safe.jpg", "image/jpeg", jpegImage))
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size", is(jpegImage.length)));

		mockMvc.perform(multipart("/api/admin/products/{productId}/images/upload", productId)
				.file(new MockMultipartFile("file", "safe.webp", "image/webp", webpImage))
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.size", is(webpImage.length)));

		mockMvc.perform(get(fieldFrom(pngUpload, "imageUrl")))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	@Test
	void registersOwnedUploadKeysAndCleansOnlyReplacedImages() throws Exception {
		UUID supplierId = createSupplier("Image ownership supplier");
		UUID productId = createProduct(
			supplierId, "Image ownership A", "Summary", "PPE_SAFETY_HELMET", "HIDDEN"
		);
		UUID otherProductId = createProduct(
			supplierId, "Image ownership B", "Summary", "PPE_SAFETY_HELMET", "HIDDEN"
		);
		MvcResult firstUpload = uploadPng(productId, "first.png");
		String firstUrl = fieldFrom(firstUpload, "imageUrl");
		String firstKey = fieldFrom(firstUpload, "objectKey");

		mockMvc.perform(put("/api/admin/products/{productId}/images", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reason":"Register uploaded image","images":[
					  {"type":"THUMBNAIL","imageUrl":"%s","storageObjectKey":"%s","sortOrder":0}
					]}
					""".formatted(firstUrl, firstKey)))
			.andExpect(status().isOk());
		assertEquals(
			List.of(firstKey),
			productImageRepository.findAllByProduct_IdOrderBySortOrderAsc(productId).stream()
				.map(image -> image.getStorageObjectKey()).toList()
		);
		assertTrue(productImageCleanupJobRepository.findByStorageObjectKey(firstKey).isEmpty());

		mockMvc.perform(put("/api/admin/products/{productId}/images", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reason":"Retain uploaded image","images":[
					  {"type":"THUMBNAIL","imageUrl":"%s","sortOrder":0}
					]}
					""".formatted(firstUrl)))
			.andExpect(status().isOk());
		assertEquals(
			List.of(firstKey),
			productImageRepository.findAllByProduct_IdOrderBySortOrderAsc(productId).stream()
				.map(image -> image.getStorageObjectKey()).toList()
		);
		assertTrue(productImageCleanupJobRepository.findByStorageObjectKey(firstKey).isEmpty());

		MvcResult replacementUpload = uploadPng(productId, "replacement.png");
		String replacementUrl = fieldFrom(replacementUpload, "imageUrl");
		String replacementKey = fieldFrom(replacementUpload, "objectKey");
		mockMvc.perform(put("/api/admin/products/{productId}/images", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reason":"Replace uploaded image","images":[
					  {"type":"THUMBNAIL","imageUrl":"%s","storageObjectKey":"%s","sortOrder":0}
					]}
					""".formatted(replacementUrl, replacementKey)))
			.andExpect(status().isOk());
		assertTrue(productImageCleanupJobRepository.findByStorageObjectKey(firstKey).isPresent());
		assertTrue(productImageCleanupJobRepository.findByStorageObjectKey(replacementKey).isEmpty());

		MvcResult otherUpload = uploadPng(otherProductId, "other.png");
		String otherUrl = fieldFrom(otherUpload, "imageUrl");
		String otherKey = fieldFrom(otherUpload, "objectKey");
		mockMvc.perform(put("/api/admin/products/{productId}/images", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reason":"Reject cross-product image","images":[
					  {"type":"THUMBNAIL","imageUrl":"%s","storageObjectKey":"%s","sortOrder":0}
					]}
					""".formatted(otherUrl, otherKey)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/admin/products/{productId}/images", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reason":"Reject mismatched image reference","images":[
					  {"type":"THUMBNAIL","imageUrl":"%s","storageObjectKey":"%s","sortOrder":0}
					]}
					""".formatted(firstUrl, replacementKey)))
			.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/admin/products/{productId}/images", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reason":"Reject pending cleanup key","images":[
					  {"type":"THUMBNAIL","imageUrl":"%s","storageObjectKey":"%s","sortOrder":0}
					]}
					""".formatted(firstUrl, firstKey)))
			.andExpect(status().isBadRequest());

		ProductImage legacyLiveReference = productImageRepository.saveAndFlush(new ProductImage(
			productRepository.findById(productId).orElseThrow(),
			ProductImageType.GALLERY,
			firstUrl,
			1,
			"Legacy cleanup race",
			firstKey
		));
		Instant liveGuardAt = Instant.now().plusSeconds(5);
		assertEquals(1, productImageCleanupService.processDueJobs(liveGuardAt));
		assertTrue(fileStorage.matchesStoredFile(firstKey, firstUrl));
		assertEquals(
			ProductImageCleanupStatus.COMPLETED,
			productImageCleanupJobRepository.findByStorageObjectKey(firstKey).orElseThrow().getStatus()
		);
		assertEquals(
			"LIVE_REFERENCE",
			productImageCleanupJobRepository.findByStorageObjectKey(firstKey).orElseThrow().getLastErrorCode()
		);

		mockMvc.perform(put("/api/admin/products/{productId}/images", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"reason":"Reject terminal cleanup key","images":[
					  {"type":"THUMBNAIL","imageUrl":"%s","storageObjectKey":"%s","sortOrder":0}
					]}
					""".formatted(firstUrl, firstKey)))
			.andExpect(status().isBadRequest());

		productImageRepository.delete(legacyLiveReference);
		productImageRepository.flush();
		var requeued = productImageCleanupService.enqueueCleanup(firstKey, productId, liveGuardAt.plusSeconds(1));
		var repeated = productImageCleanupService.enqueueCleanup(firstKey, productId, liveGuardAt.plusSeconds(1));
		assertEquals(requeued.getId(), repeated.getId());
		assertEquals(ProductImageCleanupStatus.PENDING, repeated.getStatus());
		assertEquals(1, productImageCleanupService.processDueJobs(liveGuardAt.plusSeconds(2)));
		assertFalse(fileStorage.matchesStoredFile(firstKey, firstUrl));
		var completed = productImageCleanupJobRepository.findByStorageObjectKey(firstKey).orElseThrow();
		assertEquals(ProductImageCleanupStatus.COMPLETED, completed.getStatus());
		assertNull(completed.getLastErrorCode());
	}

	private UUID createSupplier() throws Exception {
		return createSupplier("Supplier A");
	}

	private UUID createSupplier(String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/admin/suppliers")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "%s",
					  "contactName": "Manager",
					  "phone": "010-0000-0000",
					  "email": "supplier@example.com",
					  "memo": "Internal memo"
					}
					""".formatted(name)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status", is("ACTIVE")))
			.andReturn();
		return idFrom(result);
	}

	private UUID createProduct(UUID supplierId) throws Exception {
		return createProduct(supplierId, "Product A", "Summary", "PPE_SAFETY_HELMET", "HIDDEN", "8667274");
	}

	private void prepareProductForSale(UUID productId, UUID supplierId) throws Exception {
		mockMvc.perform(post("/api/admin/products/{productId}/options", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"기본","additionalPrice":0,"status":"ACTIVE"}
					"""))
			.andExpect(status().isCreated());
		mockMvc.perform(put("/api/admin/products/{productId}/images", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason":"readiness test",
					  "images":[{"type":"THUMBNAIL","imageUrl":"https://cdn.example.com/thumbnail.jpg","sortOrder":0}]
					}
					"""))
			.andExpect(status().isOk());
		mockMvc.perform(put("/api/admin/products/{productId}/notice", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "reason":"readiness test",
					  "productInfoNotice":"info",
					  "shippingInfo":"shipping",
					  "asInfo":"as",
					  "returnExchangeInfo":"returns"
					}
					"""))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/admin/products/{productId}", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId":"%s",
					  "name":"Bright safety helmet",
					  "summary":"Helmet for field work",
					  "sourcePrice":31200,
					  "sourceUrl":"https://mobile.domeggook.com/8667274",
					  "basePrice":39000,
					  "categoryCode":"PPE_SAFETY_HELMET",
					  "complianceStatus":"VERIFIED",
					  "reason":"readiness test"
					}
					""".formatted(supplierId)))
			.andExpect(status().isOk());
	}

	private void createPublicProduct(UUID supplierId, String name, String categoryCode, long basePrice) throws Exception {
		UUID productId = createProduct(supplierId, name, name + " summary", categoryCode, "HIDDEN");
		prepareProductForSale(productId, supplierId);
		mockMvc.perform(patch("/api/admin/products/{productId}", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId":"%s",
					  "name":"%s",
					  "summary":"%s summary",
					  "sourcePrice":31200,
					  "sourceUrl":"https://mobile.domeggook.com/%s",
					  "basePrice":%s,
					  "categoryCode":"%s",
					  "complianceStatus":"VERIFIED",
					  "reason":"search facet test"
					}
					""".formatted(supplierId, name, name, Integer.toUnsignedString(name.hashCode()), basePrice, categoryCode)))
			.andExpect(status().isOk());
		mockMvc.perform(patch("/api/admin/products/{productId}/status", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"status":"ACTIVE","reason":"search facet test"}
					"""))
			.andExpect(status().isOk());
	}

	private UUID createProduct(
		UUID supplierId,
		String name,
		String summary,
		String categoryCode,
		String status
	) throws Exception {
		return createProduct(supplierId, name, summary, categoryCode, status, Integer.toUnsignedString(name.hashCode()));
	}

	private UUID createProduct(
		UUID supplierId,
		String name,
		String summary,
		String categoryCode,
		String status,
		String sourceItemNo
	) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/admin/products")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId": "%s",
					  "name": "%s",
					  "summary": "%s",
					  "sourcePrice": 31200,
					  "sourceUrl": "https://mobile.domeggook.com/%s",
					  "basePrice": 39000,
					  "categoryCode": "%s",
					  "status": "%s"
					}
					""".formatted(supplierId, name, summary, sourceItemNo, categoryCode, status)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sourcePrice", is(31200)))
			.andExpect(jsonPath("$.basePrice", is(39000)))
			.andExpect(jsonPath("$.categoryCode", is(categoryCode)))
			.andExpect(jsonPath("$.status", is(status)))
			.andReturn();
		return idFrom(result);
	}

	private String productRequest(UUID supplierId, String name, String pathItemNo, String sourceItemNo) {
		String sourceItemNoJson = sourceItemNo == null ? "" : ",\n  \"sourceItemNo\": \"" + sourceItemNo + "\"";
		return """
			{
			  "supplierId": "%s",
			  "name": "%s",
			  "summary": "Supplier product",
			  "sourcePrice": 1000,
			  "sourceUrl": "https://mobile.domeggook.com/%s",
			  "basePrice": 1300,
			  "categoryCode": "PPE_SAFETY_HELMET",
			  "status": "HIDDEN"%s
			}
			""".formatted(supplierId, name, pathItemNo, sourceItemNoJson);
	}

	private int futureResult(Future<Integer> future) {
		try {
			return future.get();
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	private UUID idFrom(MvcResult result) throws Exception {
		return UUID.fromString(fieldFrom(result, "id"));
	}

	private String fieldFrom(MvcResult result, String fieldName) throws Exception {
		String json = result.getResponse().getContentAsString();
		String marker = "\"" + fieldName + "\":\"";
		int idKeyIndex = json.indexOf(marker);
		int idStart = idKeyIndex + marker.length();
		int idEnd = json.indexOf('"', idStart);
		return json.substring(idStart, idEnd);
	}

	private byte[] imageBytes(String formatName) throws Exception {
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, Color.WHITE.getRGB());
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		if (!ImageIO.write(image, formatName, output)) {
			throw new IllegalStateException("Unsupported test image format: " + formatName);
		}
		return output.toByteArray();
	}

	private byte[] webpImageBytes() {
		return Base64.getDecoder().decode("UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAgA0JaQAA3AA/vuUAAA=");
	}

	private MvcResult uploadPng(UUID productId, String filename) throws Exception {
		return mockMvc.perform(multipart("/api/admin/products/{productId}/images/upload", productId)
				.file(new MockMultipartFile("file", filename, "image/png", imageBytes("png")))
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andReturn();
	}
}
