package com.dropshipshop.api.catalog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductCategory;
import com.dropshipshop.api.catalog.domain.ProductComplianceStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PublicCatalogComplianceIntegrationTest {

	@Autowired MockMvc mockMvc;
	@Autowired SupplierRepository supplierRepository;
	@Autowired ProductRepository productRepository;

	@Test
	void excludesComplianceRejectedCoreableProductFromListAndDetail() throws Exception {
		Supplier supplier = supplierRepository.saveAndFlush(new Supplier(
			"Rejected product supplier", null, null, null, null
		));
		Product product = new Product(
			supplier,
			"Rejected public catalog fixture",
			"Must remain hidden even if a stale writer left it ACTIVE",
			1_000,
			1_300,
			ProductCategory.PPE_WORK_GLOVES,
			ProductStatus.ACTIVE
		);
		product.updateComplianceStatus(ProductComplianceStatus.REJECTED);
		product = productRepository.saveAndFlush(product);

		mockMvc.perform(get("/api/products"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString("Rejected public catalog fixture"))));
		mockMvc.perform(get("/api/products/{productId}", product.getId()))
			.andExpect(status().isNotFound());
	}
}
