package com.dropshipshop.api.policy;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.dropshipshop.api.account.domain.UserPolicyAgreement;
import com.dropshipshop.api.account.repository.UserPolicyAgreementRepository;
import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.ProductOptionStatus;
import com.dropshipshop.api.catalog.domain.ProductStatus;
import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.repository.ProductOptionRepository;
import com.dropshipshop.api.catalog.repository.ProductRepository;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.user.domain.SocialProvider;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PolicyPageApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private UserPolicyAgreementRepository userPolicyAgreementRepository;

	@Autowired
	private SupplierRepository supplierRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductOptionRepository productOptionRepository;

	@Test
	void exposesPublicPolicyPagesWithCustomerFacingPolicyText() throws Exception {
		mockMvc.perform(get("/api/policies"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policies", hasSize(3)))
			.andExpect(jsonPath("$.policies[0].policyType", is("SHIPPING_POLICY")))
			.andExpect(jsonPath("$.policies[0].href", is("/api/policies/shipping")))
			.andExpect(jsonPath("$.policies[1].policyType", is("CANCELLATION_REFUND_POLICY")))
			.andExpect(jsonPath("$.policies[2].policyType", is("OUT_OF_STOCK_NOTICE")));

		mockMvc.perform(get("/api/policies/shipping"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policyType", is("SHIPPING_POLICY")))
			.andExpect(jsonPath("$.version", is("2026-06-28")))
			.andExpect(jsonPath("$.summary", containsString("배송비는 상품 가격에 포함")))
			.andExpect(jsonPath("$.sections[0].paragraphs[0]", containsString("별도 배송비를 청구하지 않습니다")))
			.andExpect(jsonPath("$.sections[1].paragraphs[1]", containsString("택배사와 송장번호")));

		mockMvc.perform(get("/api/policies/cancellation-refund"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policyType", is("CANCELLATION_REFUND_POLICY")))
			.andExpect(jsonPath("$.summary", containsString("공급처 발주 전에는 고객 직접 취소")))
			.andExpect(jsonPath("$.sections[2].paragraphs[0]", containsString("배송 완료일로부터 7일 이내")))
			.andExpect(jsonPath("$.sections[2].paragraphs[2]", containsString("고객 부담")))
			.andExpect(jsonPath("$.sections[3].paragraphs[0]", containsString("PG 취소/환불 성공")));

		mockMvc.perform(get("/api/policies/stock-risk"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policyType", is("OUT_OF_STOCK_NOTICE")))
			.andExpect(jsonPath("$.summary", containsString("결제 후 공급처 확인 과정에서 품절")))
			.andExpect(jsonPath("$.sections[1].paragraphs[0]", containsString("품절된 배송 그룹 주문 금액만 환불")))
			.andExpect(jsonPath("$.sections[1].paragraphs[1]", containsString("일부 상품, 옵션, 수량만 따로 환불")));

		mockMvc.perform(get("/api/policies/not-found"))
			.andExpect(status().isNotFound());
	}

	@Test
	void exposesBusinessProfileAndPrivacyProcessingItemsPublicly() throws Exception {
		mockMvc.perform(get("/api/business-profile"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.companyName", is("Dropship Shop")))
			.andExpect(jsonPath("$.representativeName").exists())
			.andExpect(jsonPath("$.businessRegistrationNumber").exists())
			.andExpect(jsonPath("$.mailOrderSalesRegistrationNumber").exists())
			.andExpect(jsonPath("$.customerCenterEmail", is("support@dropship-shop.example")))
			.andExpect(jsonPath("$.privacyOfficerEmail", is("privacy@dropship-shop.example")))
			.andExpect(jsonPath("$.hostingProvider").exists());

		mockMvc.perform(get("/api/privacy-processing-items"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items", hasSize(5)))
			.andExpect(jsonPath("$.items[?(@.category == 'SOCIAL_LOGIN')]", hasSize(1)))
			.andExpect(jsonPath("$.items[?(@.category == 'PAYMENT')].processorName").value(hasItem("Toss Payments")))
			.andExpect(jsonPath("$.items[?(@.category == 'SHIPPING_ADDRESS')].purpose").value(hasItem("상품 배송")));
	}

	@Test
	void exposesPolicyLinksOnProductDetailAndCheckout() throws Exception {
		UserAccount customer = createCustomer("policy-link-customer");
		ProductOption option = createOption("Policy Link Product", 32000);

		mockMvc.perform(get("/api/products/{productId}", option.getProduct().getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policyLinks", hasSize(3)))
			.andExpect(jsonPath("$.policyLinks[0].href", is("/api/policies/shipping")))
			.andExpect(jsonPath("$.policyLinks[1].href", is("/api/policies/cancellation-refund")))
			.andExpect(jsonPath("$.policyLinks[2].href", is("/api/policies/stock-risk")));

		addCartItem(customer.getId(), option.getId(), 1);

		mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "recipientName": "Receiver",
					  "recipientPhone": "010-1111-2222",
					  "postalCode": "12345",
					  "address1": "Seoul test road",
					  "address2": "101",
					  "clientSubmittedTotalAmount": 32000
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.policyLinks", hasSize(3)))
			.andExpect(jsonPath("$.policyLinks[0].policyType", is("SHIPPING_POLICY")))
			.andExpect(jsonPath("$.policyLinks[1].policyType", is("CANCELLATION_REFUND_POLICY")))
			.andExpect(jsonPath("$.policyLinks[2].policyType", is("OUT_OF_STOCK_NOTICE")));
	}

	private UserAccount createCustomer(String providerUserId) {
		UserAccount customer = userAccountRepository.save(new UserAccount(
			SocialProvider.GOOGLE,
			providerUserId,
			providerUserId + "@example.com",
			providerUserId,
			UserRole.CUSTOMER
		));
		userPolicyAgreementRepository.save(new UserPolicyAgreement(
			customer,
			"terms-2026-06-01",
			"privacy-2026-06-01",
			Instant.now()
		));
		return customer;
	}

	private ProductOption createOption(String productName, long basePrice) {
		Supplier supplier = supplierRepository.save(new Supplier(
			productName + " Supplier",
			"Manager",
			"010-0000-0000",
			productName + "@supplier.example",
			null
		));
		Product product = productRepository.save(new Product(
			supplier,
			productName,
			productName + " Summary",
			basePrice,
			ProductStatus.ACTIVE
		));
		return productOptionRepository.saveAndFlush(new ProductOption(product, "Default", 0, ProductOptionStatus.ACTIVE));
	}

	private void addCartItem(UUID userId, UUID productOptionId, int quantity) throws Exception {
		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer(userId)))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "productOptionId": "%s",
					  "quantity": %d
					}
					""".formatted(productOptionId, quantity)))
			.andExpect(status().isCreated());
	}
}
