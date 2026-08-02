package com.dropshipshop.api.policy;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MvcResult;

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
import com.dropshipshop.api.policy.domain.BusinessProfile;
import com.dropshipshop.api.policy.domain.PolicyDocument;
import com.dropshipshop.api.policy.domain.PolicyDocumentStatus;
import com.dropshipshop.api.policy.domain.PolicyDocumentType;
import com.dropshipshop.api.policy.domain.PrivacyProcessingItem;
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

	@Autowired
	private PolicyDocumentRepository policyDocumentRepository;

	@Autowired
	private BusinessProfileRepository businessProfileRepository;

	@Autowired
	private PrivacyProcessingItemRepository privacyProcessingItemRepository;

	@Test
	void exposesPublicPolicyPagesFromActivePolicyDocuments() throws Exception {
		seedPublicPolicyDocuments();

		mockMvc.perform(get("/api/policies"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policies", hasSize(3)))
			.andExpect(jsonPath("$.policies[0].policyType", is("SHIPPING_POLICY")))
			.andExpect(jsonPath("$.policies[0].title", is("배송 정책")))
			.andExpect(jsonPath("$.policies[0].href", is("/api/policies/shipping")))
			.andExpect(jsonPath("$.policies[1].policyType", is("CANCELLATION_REFUND_POLICY")))
			.andExpect(jsonPath("$.policies[2].policyType", is("OUT_OF_STOCK_NOTICE")));

		mockMvc.perform(get("/api/policies/shipping"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policyType", is("SHIPPING_POLICY")))
			.andExpect(jsonPath("$.version", is("2026-06-28")))
			.andExpect(jsonPath("$.summary", containsString("배송비는 상품 가격에 포함")))
			.andExpect(jsonPath("$.sections[0].paragraphs[0]", containsString("별도 배송비를 청구하지 않습니다")))
			.andExpect(jsonPath("$.sections[0].paragraphs[1]", containsString("택배사와 송장번호")));

		mockMvc.perform(get("/api/policies/cancellation-refund"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policyType", is("CANCELLATION_REFUND_POLICY")))
			.andExpect(jsonPath("$.summary", containsString("공급처 발주 전에는 고객 직접 취소")))
			.andExpect(jsonPath("$.sections[0].paragraphs[0]", containsString("배송 완료일로부터 7일 이내")))
			.andExpect(jsonPath("$.sections[0].paragraphs[1]", containsString("고객 부담")))
			.andExpect(jsonPath("$.sections[0].paragraphs[2]", containsString("PG 취소/환불 성공")));

		mockMvc.perform(get("/api/policies/stock-risk"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policyType", is("OUT_OF_STOCK_NOTICE")))
			.andExpect(jsonPath("$.summary", containsString("결제 후 공급처 확인 과정에서 품절")))
			.andExpect(jsonPath("$.sections[0].paragraphs[0]", containsString("품절된 배송 그룹 주문 금액만 환불")))
			.andExpect(jsonPath("$.sections[0].paragraphs[1]", containsString("일부 상품, 옵션, 수량만 따로 환불")));

		mockMvc.perform(get("/api/policies/not-found"))
			.andExpect(status().isNotFound());
	}

	@Test
	void exposesBusinessProfileAndPrivacyProcessingItemsPublicly() throws Exception {
		seedLegalDisclosures();

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
			.andExpect(jsonPath("$.items[?(@.category == 'PAYMENT')]", hasSize(1)))
			.andExpect(jsonPath("$.items[?(@.category == 'SHIPPING_ADDRESS')].purpose").value(hasItem("상품 배송")));
	}

	@Test
	void managesPolicyDocumentVersions() throws Exception {
		String policyId = fieldFrom(mockMvc.perform(post("/api/admin/policies")
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "type": "TERMS_OF_SERVICE",
					  "version": "terms-2026-07-01",
					  "title": "Terms draft",
					  "content": "Draft terms content",
					  "effectiveFrom": "2026-07-01T00:00:00Z"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status", is("DRAFT")))
			.andReturn(), "policyId");

		mockMvc.perform(patch("/api/admin/policies/{policyId}", policyId)
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "type": "TERMS_OF_SERVICE",
					  "version": "terms-2026-07-01",
					  "title": "Terms updated",
					  "content": "Updated terms content",
					  "effectiveFrom": "2026-07-01T00:00:00Z"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title", is("Terms updated")));

		mockMvc.perform(post("/api/admin/policies/{policyId}/activate", policyId)
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("ACTIVE")));

		mockMvc.perform(get("/api/policies/TERMS_OF_SERVICE/current"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version", is("terms-2026-07-01")))
			.andExpect(jsonPath("$.content", is("Updated terms content")));

		mockMvc.perform(get("/api/policies/TERMS_OF_SERVICE/versions/terms-2026-07-01"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("ACTIVE")));

		mockMvc.perform(get("/api/admin/policies")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policies[?(@.policyId == '%s')]".formatted(policyId), hasSize(1)));
	}

	@Test
	void exposesPolicyLinksOnProductDetailAndCheckout() throws Exception {
		seedPublicPolicyDocuments();

		UserAccount customer = createCustomer("policy-link-customer");
		ProductOption option = createOption("Policy Link Product", 32000);

		mockMvc.perform(get("/api/products/{productId}", option.getProduct().getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policyLinks", hasSize(3)))
			.andExpect(jsonPath("$.policyLinks[0].label", is("배송 정책")))
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
			.andExpect(jsonPath("$.policyLinks[0].label", is("배송 정책")))
			.andExpect(jsonPath("$.policyLinks[0].policyType", is("SHIPPING_POLICY")))
			.andExpect(jsonPath("$.policyLinks[1].policyType", is("CANCELLATION_REFUND_POLICY")))
			.andExpect(jsonPath("$.policyLinks[2].policyType", is("OUT_OF_STOCK_NOTICE")));
	}

	@Test
	void usesCustomerFacingPolicyLinkLabelsWithoutActivePolicyDocuments() throws Exception {
		policyDocumentRepository.deleteAll();
		ProductOption option = createOption("Fallback Policy Link Product", 10000);

		mockMvc.perform(get("/api/products/{productId}", option.getProduct().getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.policyLinks", hasSize(3)))
			.andExpect(jsonPath("$.policyLinks[0].label", is("배송 정책")))
			.andExpect(jsonPath("$.policyLinks[1].label", is("취소/환불 정책")))
			.andExpect(jsonPath("$.policyLinks[2].label", is("결제 후 품절 안내")));
	}

	private void seedPublicPolicyDocuments() {
		seedPublicPolicy(
			PolicyDocumentType.SHIPPING_POLICY,
			"배송 정책",
			"""
				배송비는 상품 가격에 포함되며, 공급처 출고 후 송장번호로 배송 상태를 확인할 수 있습니다.
				본 쇼핑몰은 고객에게 별도 배송비를 청구하지 않습니다.
				관리자가 택배사와 송장번호를 입력하면 고객 주문 상세에서 배송 정보를 확인할 수 있습니다.
				"""
		);
		seedPublicPolicy(
			PolicyDocumentType.CANCELLATION_REFUND_POLICY,
			"취소/환불 정책",
			"""
				공급처 발주 전에는 고객 직접 취소가 가능하며, 발주 작업 이후 취소는 관리자 검토를 거칩니다.
				단순 변심 반품/교환은 배송 완료일로부터 7일 이내 접수된 건만 심사합니다.
				단순 변심의 반환 또는 재배송 비용은 고객 부담을 기본으로 합니다.
				결제 승인 완료 주문은 PG 취소/환불 성공이 확인된 뒤에만 환불 완료로 표시됩니다.
				"""
		);
		seedPublicPolicy(
			PolicyDocumentType.OUT_OF_STOCK_NOTICE,
			"결제 후 품절 안내",
			"""
				공급처 출고형 상품은 결제 후 공급처 확인 과정에서 품절이 확인될 수 있습니다.
				품절된 배송 그룹 주문 금액만 환불할 수 있습니다.
				배송 그룹 주문 내부의 일부 상품, 옵션, 수량만 따로 환불하는 기능은 MVP에서 지원하지 않습니다.
				"""
		);
	}

	private void seedPublicPolicy(PolicyDocumentType type, String title, String content) {
		if (policyDocumentRepository.findByTypeAndStatus(type, PolicyDocumentStatus.ACTIVE).isPresent()) {
			return;
		}
		PolicyDocument policy = new PolicyDocument(type, "2026-06-28", title, content, Instant.parse("2026-06-28T00:00:00Z"));
		policy.activate();
		policyDocumentRepository.save(policy);
	}

	private void seedLegalDisclosures() {
		if (businessProfileRepository.count() == 0) {
			businessProfileRepository.save(new BusinessProfile(
				"Dropship Shop",
				"대표자명",
				"000-00-00000",
				"통신판매업 신고 준비중",
				"신고 기관 확정 전",
				"서울특별시 사업장 주소",
				"010-0000-0000",
				"support@dropship-shop.example",
				"평일 10:00-17:00",
				"개인정보 보호책임자",
				"privacy@dropship-shop.example",
				"010-0000-0000",
				"Cloud provider",
				true,
				Instant.parse("2026-06-28T00:00:00Z")
			));
		}
		if (privacyProcessingItemRepository.count() > 0) {
			return;
		}
		privacyProcessingItemRepository.save(new PrivacyProcessingItem(
			"SOCIAL_LOGIN",
			"제공자, 제공자 user id, 이메일, 표시 이름",
			"회원 식별과 로그인",
			"회원 탈퇴 시까지",
			null,
			null,
			null,
			null,
			1,
			true
		));
		privacyProcessingItemRepository.save(new PrivacyProcessingItem(
			"PAYMENT",
			"주문 상품, 결제 금액, 결제 수단, 입금자명, 입금 확인 시각",
			"계약 이행, 결제, 환불, 분쟁 대응",
			"법정 보존 기간까지",
			null,
			null,
			null,
			null,
			2,
			true
		));
		privacyProcessingItemRepository.save(new PrivacyProcessingItem(
			"SHIPPING_ADDRESS",
			"수령인, 전화번호, 주소",
			"상품 배송",
			"주문 처리 완료 후 법정 보존 기간까지",
			"택배/배송조회 처리자",
			"배송 및 배송조회",
			null,
			null,
			3,
			true
		));
		privacyProcessingItemRepository.save(new PrivacyProcessingItem(
			"ORDER_CONTACT",
			"이름, 전화번호, 이메일",
			"주문, 배송, 클레임 안내",
			"회원 탈퇴 시까지 또는 법정 보존 기간까지",
			"이메일 발송 처리자",
			"거래 알림 이메일 발송",
			null,
			null,
			4,
			true
		));
		privacyProcessingItemRepository.save(new PrivacyProcessingItem(
			"CLAIM",
			"클레임 사유, 사진, 운송장, 고객 메모, 관리자 처리 이력",
			"취소, 반품, 교환, 분쟁 대응",
			"법정 보존 기간까지",
			null,
			null,
			null,
			null,
			5,
			true
		));
	}

	private UserAccount createCustomer(String providerUserId) {
		UserAccount customer = userAccountRepository.save(new UserAccount(
			SocialProvider.GOOGLE,
			providerUserId,
			providerUserId + "@example.com",
			providerUserId,
			UserRole.CUSTOMER
		));
		customer.verifyPhone("01011112222", Instant.now());
		userAccountRepository.save(customer);
		userPolicyAgreementRepository.save(new UserPolicyAgreement(
			customer,
			"2026-08-02",
			"2026-08-02",
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

	private String fieldFrom(MvcResult result, String fieldName) throws Exception {
		String body = result.getResponse().getContentAsString();
		String marker = "\"" + fieldName + "\":\"";
		int start = body.indexOf(marker);
		if (start < 0) {
			throw new IllegalStateException("Field not found: " + fieldName + " in " + body);
		}
		int valueStart = start + marker.length();
		int valueEnd = body.indexOf("\"", valueStart);
		return body.substring(valueStart, valueEnd);
	}
}
