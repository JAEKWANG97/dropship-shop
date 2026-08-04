package com.dropshipshop.api.account;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
class AccountAgreementApiIntegrationTest {

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
	void getsAgreementStateAndStoresRequiredAgreementIdempotently() throws Exception {
		UserAccount customer = createCustomer("agreement-customer-1");

		mockMvc.perform(get("/api/me/agreements")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requiredAgreed", is(false)))
			.andExpect(jsonPath("$.requiredTermsVersion", is("2026-08-02")))
			.andExpect(jsonPath("$.requiredPrivacyVersion", is("2026-08-04")));

		MvcResult firstResult = mockMvc.perform(post("/api/me/agreements")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(requiredAgreementRequest()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.requiredAgreed", is(true)))
			.andExpect(jsonPath("$.termsVersion", is("2026-08-02")))
			.andExpect(jsonPath("$.privacyVersion", is("2026-08-04")))
			.andExpect(jsonPath("$.agreedAt").exists())
			.andReturn();

		String agreementId = fieldFrom(firstResult, "agreementId");

		mockMvc.perform(post("/api/me/agreements")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(requiredAgreementRequest()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.agreementId", is(agreementId)));

		mockMvc.perform(get("/api/me/agreements")
				.with(authentication(TestAuthentication.customer(customer.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requiredAgreed", is(true)))
			.andExpect(jsonPath("$.agreedTermsVersion", is("2026-08-02")))
			.andExpect(jsonPath("$.agreedPrivacyVersion", is("2026-08-04")));

		org.assertj.core.api.Assertions.assertThat(userPolicyAgreementRepository.findByUser_IdAndTermsVersionAndPrivacyVersion(
			customer.getId(),
			"2026-08-02",
			"2026-08-04"
		)).isPresent();
	}

	@Test
	void rejectsMissingConsentAndOutdatedVersions() throws Exception {
		UserAccount customer = createCustomer("agreement-customer-2");

		mockMvc.perform(post("/api/me/agreements")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "termsAgreed": false,
					  "privacyAgreed": true,
					  "termsVersion": "2026-08-02",
					  "privacyVersion": "2026-08-04"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code", is("VALIDATION_FAILED")));

		mockMvc.perform(post("/api/me/agreements")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "termsAgreed": true,
					  "privacyAgreed": true,
					  "termsVersion": "terms-old",
					  "privacyVersion": "2026-08-04"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", is("Agreement versions are not current")));
	}

	@Test
	void checkoutCreationRequiresAccountAgreement() throws Exception {
		UserAccount customer = createCustomer("agreement-customer-3");
		ProductOption option = createOption("Agreement Product");
		addCartItem(customer, option);

		mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCheckoutRequest()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", is("Required account agreements are missing")));

		agree(customer);

		mockMvc.perform(post("/api/checkouts")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(validCheckoutRequest()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.checkoutNumber").exists());
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
		return userAccountRepository.save(customer);
	}

	private ProductOption createOption(String productName) {
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
			10000,
			ProductStatus.ACTIVE
		));
		return productOptionRepository.saveAndFlush(new ProductOption(product, "Default", 0, ProductOptionStatus.ACTIVE));
	}

	private void addCartItem(UserAccount customer, ProductOption option) throws Exception {
		mockMvc.perform(post("/api/cart/items")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "productOptionId": "%s",
					  "quantity": 1
					}
					""".formatted(option.getId())))
			.andExpect(status().isCreated());
	}

	private void agree(UserAccount customer) throws Exception {
		mockMvc.perform(post("/api/me/agreements")
				.with(authentication(TestAuthentication.customer(customer.getId())))
				.contentType(MediaType.APPLICATION_JSON)
				.content(requiredAgreementRequest()))
			.andExpect(status().isCreated());
	}

	private String requiredAgreementRequest() {
		return """
			{
			  "termsAgreed": true,
			  "privacyAgreed": true,
			  "termsVersion": "2026-08-02",
			  "privacyVersion": "2026-08-04"
			}
			""";
	}

	private String validCheckoutRequest() {
		return """
			{
			  "recipientName": "Receiver",
			  "recipientPhone": "010-1111-2222",
			  "postalCode": "12345",
			  "address1": "Seoul test road",
			  "address2": "101"
			}
			""";
	}

	private String fieldFrom(MvcResult result, String fieldName) throws Exception {
		String json = result.getResponse().getContentAsString();
		String key = "\"" + fieldName + "\":\"";
		int keyIndex = json.indexOf(key);
		int start = keyIndex + key.length();
		int end = json.indexOf('"', start);
		return json.substring(start, end);
	}
}
