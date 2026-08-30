package com.dropshipshop.api.checkout;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;

@SpringBootTest(properties = "app.sales.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StorefrontSalesClosedApiIntegrationTest {

	private static final String SALES_NOTICE = "판매 준비 중입니다.";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductOptionRepository productOptionRepository;

	@Test
	void blocksCartAddAndCheckoutCreation() throws Exception {
		UUID customerId = UUID.randomUUID();

		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer(customerId)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "productOptionId": "%s",
					  "quantity": 1
					}
					""".formatted(UUID.randomUUID())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message", is(SALES_NOTICE)));

		mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(customerId)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "recipientName": "구매자",
					  "recipientPhone": "01012345678",
					  "postalCode": "05555",
					  "address1": "서울특별시",
					  "address2": "",
					  "depositorName": "구매자",
					  "clientSubmittedTotalAmount": 10000
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message", is(SALES_NOTICE)));
	}

	@Test
	void publicCatalogNeverClaimsPurchasableWhileStorefrontIsClosed() throws Exception {
		Supplier supplier = supplierRepository.save(new Supplier(
			"Closed Supplier", null, null, "closed@example.com", null
		));
		Product product = productRepository.save(new Product(
			supplier, "Closed Product", "Summary", 10_000, ProductStatus.ACTIVE
		));
		productOptionRepository.saveAndFlush(new ProductOption(
			product, "Default", 0, ProductOptionStatus.ACTIVE
		));

		mockMvc.perform(get("/api/products").param("q", "Closed Product"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.products[0].purchasable", is(false)));
		mockMvc.perform(get("/api/products/{productId}", product.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.salesEnabled", is(false)))
			.andExpect(jsonPath("$.purchasable", is(false)))
			.andExpect(jsonPath("$.options[0].purchasable", is(false)));
	}
}
