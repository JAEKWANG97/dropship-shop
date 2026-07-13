package com.dropshipshop.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.dropshipshop.api.auth.security.TestAuthentication;
import com.dropshipshop.api.notification.NotificationLogRepository;
import com.dropshipshop.api.notification.domain.NotificationChannel;
import com.dropshipshop.api.notification.domain.NotificationLog;
import com.dropshipshop.api.notification.domain.NotificationStatus;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.support.domain.CustomerInquiry;
import com.dropshipshop.api.support.domain.CustomerInquiryStatus;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CustomerInquiryApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CustomerInquiryRepository customerInquiryRepository;

	@Autowired
	private NotificationLogRepository notificationLogRepository;

	@Autowired
	private InquiryLookupTokenService lookupTokenService;

	@Autowired
	private CustomerInquiryService customerInquiryService;

	@Test
	void recordsConsentAndAllowsAdminOnlyList() throws Exception {
		create("consent@example.com", "배송 문의").andExpect(status().isCreated())
			.andExpect(jsonPath("$.status", is("RECEIVED")))
			.andExpect(jsonPath("$.lookupToken").isNotEmpty());

		CustomerInquiry inquiry = findByEmail("consent@example.com");
		assertThat(inquiry.getConsentPolicyVersion()).isEqualTo("support-inquiry-privacy-2026-07-13");
		assertThat(inquiry.getConsentedAt()).isNotNull();
		assertThat(inquiry.getRetentionExpiresAt()).isAfter(inquiry.getCreatedAt().plusSeconds(365L * 2 * 24 * 60 * 60));

		mockMvc.perform(get("/api/admin/customer-inquiries"))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/admin/customer-inquiries").with(authentication(TestAuthentication.customer())))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/customer-inquiries")
				.param("status", "RECEIVED")
				.with(authentication(TestAuthentication.admin())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.inquiries[?(@.email == 'consent@example.com')]", hasSize(1)));
	}

	@Test
	void requiresPrivacyConsentAndRateLimitsRepeatedEmail() throws Exception {
		mockMvc.perform(post("/api/customer-inquiries")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody("missing-consent@example.com", "동의 누락", false)))
			.andExpect(status().isBadRequest());

		for (int index = 0; index < 3; index++) {
			create("rate-limit@example.com", "반복 문의 " + index).andExpect(status().isCreated());
		}
		create("rate-limit@example.com", "네 번째 문의")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code", is("RATE_LIMITED")));
		create("other-customer@example.com", "다른 고객 문의").andExpect(status().isCreated());
	}

	@Test
	void lookupRequiresValidTokenAndDoesNotExposePersonalData() throws Exception {
		create("lookup@example.com", "조회 문의").andExpect(status().isCreated());
		CustomerInquiry inquiry = findByEmail("lookup@example.com");

		MvcResult result = mockMvc.perform(post("/api/customer-inquiries/{inquiryId}/lookup", inquiry.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"lookupToken":"%s"}
					""".formatted(lookupTokenService.token(inquiry.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.subject", is("조회 문의")))
			.andExpect(jsonPath("$.status", is("RECEIVED")))
			.andReturn();

		assertThat(result.getResponse().getContentAsString())
			.doesNotContain("lookup@example.com", "customerName", "phone", "adminMemo", "consentPolicyVersion");

		mockMvc.perform(post("/api/customer-inquiries/{inquiryId}/lookup", inquiry.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lookupToken\":\"invalid\"}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void adminProcessesAnswersAndEmailFailureDoesNotRollbackAnswer() throws Exception {
		create("answer@example.com", "답변 문의").andExpect(status().isCreated());
		CustomerInquiry inquiry = findByEmail("answer@example.com");

		mockMvc.perform(patch("/api/admin/customer-inquiries/{inquiryId}/status", inquiry.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"IN_PROGRESS\",\"adminMemo\":\"확인 중\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("IN_PROGRESS")));

		mockMvc.perform(post("/api/admin/customer-inquiries/{inquiryId}/answer", inquiry.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"answer\":\"내일 출고 예정입니다.\",\"adminMemo\":\"공급처 확인 완료\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("ANSWERED")))
			.andExpect(jsonPath("$.latestAnswerNotification.status", is("PENDING")));

		CustomerInquiry answered = customerInquiryRepository.findById(inquiry.getId()).orElseThrow();
		assertThat(answered.getAnswer()).isEqualTo("내일 출고 예정입니다.");
		assertThat(answered.getStatus()).isEqualTo(CustomerInquiryStatus.ANSWERED);
		assertThat(notificationLogRepository.findFirstByCustomerInquiryIdOrderByCreatedAtDesc(inquiry.getId()))
			.get()
			.extracting(log -> log.getStatus())
			.isEqualTo(NotificationStatus.SKIPPED);

		mockMvc.perform(patch("/api/admin/customer-inquiries/{inquiryId}/status", inquiry.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"CLOSED\",\"adminMemo\":\"처리 완료\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status", is("CLOSED")));

		mockMvc.perform(post("/api/admin/customer-inquiries/{inquiryId}/answer", inquiry.getId())
				.with(authentication(TestAuthentication.admin()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"answer\":\"종료 후 답변\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void deletesExpiredInquiry() {
		CustomerInquiry expired = customerInquiryRepository.saveAndFlush(new CustomerInquiry(
			"만료 고객",
			"expired@example.com",
			null,
			"만료 문의",
			"삭제 대상",
			"privacy-legacy",
			Instant.now().minusSeconds(100),
			Instant.now().minusSeconds(1)
		));
		NotificationLog notification = notificationLogRepository.saveAndFlush(new NotificationLog(
			null,
			null,
			null,
			null,
			null,
			expired.getId(),
			NotificationType.CUSTOMER_INQUIRY_ANSWERED,
			NotificationChannel.EMAIL,
			"expired@example.com",
			"customer_inquiry_answered",
			"message=삭제할 답변"
		));

		assertThat(customerInquiryService.deleteExpired()).isGreaterThanOrEqualTo(1);
		assertThat(customerInquiryRepository.findById(expired.getId())).isEmpty();
		assertThat(notificationLogRepository.findById(notification.getId()).orElseThrow())
			.satisfies(log -> {
				assertThat(log.getCustomerInquiryId()).isNull();
				assertThat(log.getRecipient()).isEqualTo("retention_cleanup");
				assertThat(log.getPayloadSnapshot()).isEqualTo("retention_cleanup");
			});
	}

	private org.springframework.test.web.servlet.ResultActions create(String email, String subject) throws Exception {
		return mockMvc.perform(post("/api/customer-inquiries")
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestBody(email, subject, true)));
	}

	private String requestBody(String email, String subject, boolean privacyConsent) {
		return """
			{
			  "customerName": "김고객",
			  "email": "%s",
			  "phone": "010-1111-2222",
			  "subject": "%s",
			  "message": "문의 내용입니다.",
			  "privacyConsent": %s
			}
			""".formatted(email, subject, privacyConsent);
	}

	private CustomerInquiry findByEmail(String email) {
		return customerInquiryRepository.findAllByOrderByCreatedAtDesc().stream()
			.filter(inquiry -> email.equals(inquiry.getEmail()))
			.findFirst()
			.orElseThrow();
	}
}
