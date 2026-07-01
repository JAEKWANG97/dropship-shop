package com.dropshipshop.api.catalog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.repository.ProductChangeHistoryRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductChangeHistoryRepository productChangeHistoryRepository;

	@Test
	void managesCatalogAndExposesPublicProducts() throws Exception {
		mockMvc.perform(get("/api/products"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));

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
		MockMultipartFile imageFile = new MockMultipartFile(
			"file",
			"thumbnail.webp",
			"image/webp",
			"fake-image".getBytes()
		);
		MvcResult uploadResult = mockMvc.perform(multipart("/api/admin/products/{productId}/images/upload", productId)
				.file(imageFile)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.imageUrl", containsString("/uploads/products/" + productId)))
			.andExpect(jsonPath("$.objectKey", containsString(productId.toString())))
			.andExpect(jsonPath("$.size", is(10)))
			.andReturn();
		String uploadedImageUrl = fieldFrom(uploadResult, "imageUrl");

		mockMvc.perform(post("/api/admin/products/{productId}/options", productId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "Black / Large",
					  "additionalPrice": 1000,
					  "status": "ACTIVE"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status", is("ACTIVE")));

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
					  "shippingInfo": "Shipping info",
					  "asInfo": "AS info",
					  "returnExchangeInfo": "Return exchange info"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.productNoticeVersion", is(1)))
			.andExpect(jsonPath("$.productNotice.productInfoNotice", is("Product info notice")));

		mockMvc.perform(get("/api/products"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id", is(productId.toString())))
			.andExpect(jsonPath("$[0].sourcePrice").doesNotExist());

		mockMvc.perform(get("/api/products/{productId}", productId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sourcePrice").doesNotExist())
			.andExpect(jsonPath("$.options", hasSize(1)))
			.andExpect(jsonPath("$.images", hasSize(2)))
			.andExpect(jsonPath("$.productNoticeVersion", is(1)));

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
			.andExpect(jsonPath("$.changes", hasSize(4)))
			.andExpect(jsonPath("$.changes[?(@.changeType == 'IMAGES')]", hasSize(1)))
			.andExpect(jsonPath("$.changes[?(@.changeType == 'PRODUCT_STATUS')].beforeValue", hasItem("ACTIVE")))
			.andExpect(jsonPath("$.changes[?(@.changeType == 'PRODUCT_STATUS')].afterValue", hasItem("HIDDEN")))
			.andExpect(jsonPath("$.changes[?(@.changeType == 'PRODUCT_STATUS')].adminUserId", hasItem(TestAuthentication.ADMIN_ID.toString())));

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

	private UUID createSupplier() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/admin/suppliers")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "Supplier A",
					  "contactName": "Manager",
					  "phone": "010-0000-0000",
					  "email": "supplier@example.com",
					  "memo": "Internal memo"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status", is("ACTIVE")))
			.andReturn();
		return idFrom(result);
	}

	private UUID createProduct(UUID supplierId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/admin/products")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "supplierId": "%s",
					  "name": "Product A",
					  "summary": "Summary",
					  "sourcePrice": 31200,
					  "basePrice": 39000,
					  "categoryCode": "PPE_SAFETY_HELMET",
					  "status": "ACTIVE"
					}
					""".formatted(supplierId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sourcePrice", is(31200)))
			.andExpect(jsonPath("$.basePrice", is(39000)))
			.andExpect(jsonPath("$.categoryCode", is("PPE_SAFETY_HELMET")))
			.andExpect(jsonPath("$.status", is("ACTIVE")))
			.andReturn();
		return idFrom(result);
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
}
