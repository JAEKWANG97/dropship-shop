package com.dropshipshop.api.policy;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.policy.domain.PolicyDocument;
import com.dropshipshop.api.policy.domain.PolicyDocumentStatus;
import com.dropshipshop.api.policy.domain.PolicyDocumentType;

@Service
class PolicyPageService {

	private static final List<PolicyDocumentType> PUBLIC_POLICY_TYPES = List.of(
		PolicyDocumentType.SHIPPING_POLICY,
		PolicyDocumentType.CANCELLATION_REFUND_POLICY,
		PolicyDocumentType.OUT_OF_STOCK_NOTICE
	);
	private static final String VERSION = "2026-06-28";
	private static final PolicyDtos.BusinessProfileResponse BUSINESS_PROFILE = new PolicyDtos.BusinessProfileResponse(
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
		VERSION
	);
	private static final List<PolicyDtos.PrivacyProcessingItemResponse> PRIVACY_PROCESSING_ITEMS = List.of(
		new PolicyDtos.PrivacyProcessingItemResponse(
			"SOCIAL_LOGIN",
			"제공자, 제공자 user id, 이메일, 표시 이름",
			"회원 식별과 로그인",
			"회원 탈퇴 시까지",
			null,
			null,
			null,
			null
		),
		new PolicyDtos.PrivacyProcessingItemResponse(
			"ORDER_CONTACT",
			"이름, 전화번호, 이메일",
			"주문, 배송, 클레임 안내",
			"회원 탈퇴 시까지 또는 법정 보존 기간까지",
			"이메일 발송 처리자",
			"거래 알림 이메일 발송",
			null,
			null
		),
		new PolicyDtos.PrivacyProcessingItemResponse(
			"SHIPPING_ADDRESS",
			"수령인, 전화번호, 주소",
			"상품 배송",
			"주문 처리 완료 후 법정 보존 기간까지",
			"택배/배송조회 처리자",
			"배송 및 배송조회",
			null,
			null
		),
		new PolicyDtos.PrivacyProcessingItemResponse(
			"PAYMENT",
			"주문 상품, 결제 금액, 결제 수단, PG 거래 식별자",
			"계약 이행, 결제, 환불, 분쟁 대응",
			"법정 보존 기간까지",
			"Toss Payments",
			"결제 승인, 취소, 환불 처리",
			null,
			null
		),
		new PolicyDtos.PrivacyProcessingItemResponse(
			"CLAIM",
			"클레임 사유, 사진, 운송장, 고객 메모, 관리자 처리 이력",
			"취소, 반품, 교환, 분쟁 대응",
			"법정 보존 기간까지",
			null,
			null,
			null,
			null
		)
	);

	private final PolicyDocumentRepository policyDocumentRepository;

	PolicyPageService(PolicyDocumentRepository policyDocumentRepository) {
		this.policyDocumentRepository = policyDocumentRepository;
	}

	@Transactional(readOnly = true)
	PolicyDtos.PolicyIndexResponse listPolicies() {
		return new PolicyDtos.PolicyIndexResponse(PUBLIC_POLICY_TYPES.stream()
			.map(this::activePolicy)
			.map(this::toLink)
			.toList());
	}

	@Transactional(readOnly = true)
	PolicyDtos.PolicyPageResponse getPolicy(String slug) {
		PolicyDocument policy = activePolicy(typeFromSlug(slug));
		List<String> paragraphs = paragraphs(policy.getContent());
		String summary = paragraphs.isEmpty() ? policy.getTitle() : paragraphs.get(0);
		List<String> body = paragraphs.size() <= 1 ? paragraphs : paragraphs.subList(1, paragraphs.size());
		return new PolicyDtos.PolicyPageResponse(
			policy.getType().name(),
			policy.getTitle(),
			policy.getVersion(),
			summary,
			List.of(new PolicyDtos.PolicySectionResponse(policy.getTitle(), body)),
			listPolicies().policies()
		);
	}

	PolicyDtos.BusinessProfileResponse getBusinessProfile() {
		return BUSINESS_PROFILE;
	}

	PolicyDtos.PrivacyProcessingItemListResponse listPrivacyProcessingItems() {
		return new PolicyDtos.PrivacyProcessingItemListResponse(PRIVACY_PROCESSING_ITEMS);
	}

	private PolicyDocument activePolicy(PolicyDocumentType type) {
		return policyDocumentRepository.findByTypeAndStatus(type, PolicyDocumentStatus.ACTIVE)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active policy document not found"));
	}

	private PolicyDtos.PolicyLinkResponse toLink(PolicyDocument policy) {
		return new PolicyDtos.PolicyLinkResponse(policy.getType().name(), policy.getTitle(), href(policy.getType()));
	}

	private PolicyDocumentType typeFromSlug(String slug) {
		return switch (slug) {
			case "shipping" -> PolicyDocumentType.SHIPPING_POLICY;
			case "cancellation-refund" -> PolicyDocumentType.CANCELLATION_REFUND_POLICY;
			case "stock-risk" -> PolicyDocumentType.OUT_OF_STOCK_NOTICE;
			default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found");
		};
	}

	private String href(PolicyDocumentType type) {
		return switch (type) {
			case SHIPPING_POLICY -> "/api/policies/shipping";
			case CANCELLATION_REFUND_POLICY -> "/api/policies/cancellation-refund";
			case OUT_OF_STOCK_NOTICE -> "/api/policies/stock-risk";
			default -> throw new IllegalArgumentException("Unsupported public policy type: " + type);
		};
	}

	private List<String> paragraphs(String content) {
		return content.lines()
			.map(String::trim)
			.filter(line -> !line.isBlank())
			.toList();
	}
}
