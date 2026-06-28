package com.dropshipshop.api.policy;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dropshipshop.api.policy.domain.PolicyDocumentStatus;
import com.dropshipshop.api.policy.domain.PolicyDocumentType;

@Service
public class CustomerPolicyLinkService {

	private static final List<PolicyDocumentType> PUBLIC_POLICY_TYPES = List.of(
		PolicyDocumentType.SHIPPING_POLICY,
		PolicyDocumentType.CANCELLATION_REFUND_POLICY,
		PolicyDocumentType.OUT_OF_STOCK_NOTICE
	);

	private final PolicyDocumentRepository policyDocumentRepository;

	CustomerPolicyLinkService(PolicyDocumentRepository policyDocumentRepository) {
		this.policyDocumentRepository = policyDocumentRepository;
	}

	@Transactional(readOnly = true)
	public List<PolicyLink> links() {
		return PUBLIC_POLICY_TYPES.stream()
			.map(type -> new PolicyLink(title(type), href(type), type.name()))
			.toList();
	}

	private String title(PolicyDocumentType type) {
		return policyDocumentRepository.findByTypeAndStatus(type, PolicyDocumentStatus.ACTIVE)
			.map(policy -> policy.getTitle())
			.orElse(type.name());
	}

	private String href(PolicyDocumentType type) {
		return switch (type) {
			case SHIPPING_POLICY -> "/api/policies/shipping";
			case CANCELLATION_REFUND_POLICY -> "/api/policies/cancellation-refund";
			case OUT_OF_STOCK_NOTICE -> "/api/policies/stock-risk";
			default -> throw new IllegalArgumentException("Unsupported public policy type: " + type);
		};
	}

	public record PolicyLink(String label, String href, String policyType) {
	}
}
