package com.dropshipshop.api.policy;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.policy.domain.BusinessProfile;
import com.dropshipshop.api.policy.domain.PolicyDocument;
import com.dropshipshop.api.policy.domain.PolicyDocumentStatus;
import com.dropshipshop.api.policy.domain.PolicyDocumentType;
import com.dropshipshop.api.policy.domain.PrivacyProcessingItem;

@Service
class PolicyPageService {

	private static final List<PolicyDocumentType> PUBLIC_POLICY_TYPES = List.of(
		PolicyDocumentType.SHIPPING_POLICY,
		PolicyDocumentType.CANCELLATION_REFUND_POLICY,
		PolicyDocumentType.OUT_OF_STOCK_NOTICE
	);

	private final PolicyDocumentRepository policyDocumentRepository;
	private final BusinessProfileRepository businessProfileRepository;
	private final PrivacyProcessingItemRepository privacyProcessingItemRepository;

	PolicyPageService(
		PolicyDocumentRepository policyDocumentRepository,
		BusinessProfileRepository businessProfileRepository,
		PrivacyProcessingItemRepository privacyProcessingItemRepository
	) {
		this.policyDocumentRepository = policyDocumentRepository;
		this.businessProfileRepository = businessProfileRepository;
		this.privacyProcessingItemRepository = privacyProcessingItemRepository;
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
		BusinessProfile profile = businessProfileRepository.findFirstByActiveTrueOrderByEffectiveFromDesc()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business profile not found"));
		return new PolicyDtos.BusinessProfileResponse(
			profile.getCompanyName(),
			profile.getRepresentativeName(),
			profile.getBusinessRegistrationNumber(),
			profile.getMailOrderSalesRegistrationNumber(),
			profile.getMailOrderSalesRegistrationAuthority(),
			profile.getBusinessAddress(),
			profile.getCustomerCenterPhone(),
			profile.getCustomerCenterEmail(),
			profile.getCustomerCenterHours(),
			profile.getPrivacyOfficerName(),
			profile.getPrivacyOfficerEmail(),
			profile.getPrivacyOfficerPhone(),
			profile.getHostingProvider(),
			profile.getEffectiveFrom().toString()
		);
	}

	PolicyDtos.PrivacyProcessingItemListResponse listPrivacyProcessingItems() {
		return new PolicyDtos.PrivacyProcessingItemListResponse(privacyProcessingItemRepository.findAllByActiveTrueOrderBySortOrderAsc()
			.stream()
			.map(this::toPrivacyProcessingItem)
			.toList());
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

	private PolicyDtos.PrivacyProcessingItemResponse toPrivacyProcessingItem(PrivacyProcessingItem item) {
		return new PolicyDtos.PrivacyProcessingItemResponse(
			item.getCategory(),
			item.getCollectedItems(),
			item.getPurpose(),
			item.getRetentionPeriod(),
			item.getProcessorName(),
			item.getProcessorPurpose(),
			item.getThirdPartyRecipient(),
			item.getThirdPartyPurpose()
		);
	}
}
