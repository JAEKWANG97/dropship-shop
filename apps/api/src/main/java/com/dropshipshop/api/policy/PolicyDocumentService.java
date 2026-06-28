package com.dropshipshop.api.policy;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.policy.domain.PolicyDocument;
import com.dropshipshop.api.policy.domain.PolicyDocumentStatus;
import com.dropshipshop.api.policy.domain.PolicyDocumentType;

@Service
class PolicyDocumentService {

	private final PolicyDocumentRepository policyDocumentRepository;

	PolicyDocumentService(PolicyDocumentRepository policyDocumentRepository) {
		this.policyDocumentRepository = policyDocumentRepository;
	}

	@Transactional(readOnly = true)
	PolicyDocumentDtos.PolicyDocumentListResponse listPolicies() {
		return new PolicyDocumentDtos.PolicyDocumentListResponse(
			policyDocumentRepository.findAllByOrderByCreatedAtAsc()
				.stream()
				.map(this::toResponse)
				.toList()
		);
	}

	@Transactional(readOnly = true)
	PolicyDocumentDtos.PolicyDocumentResponse getCurrent(PolicyDocumentType type) {
		return policyDocumentRepository.findByTypeAndStatus(type, PolicyDocumentStatus.ACTIVE)
			.map(this::toResponse)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active policy document not found"));
	}

	@Transactional(readOnly = true)
	PolicyDocumentDtos.PolicyDocumentResponse getVersion(PolicyDocumentType type, String version) {
		return policyDocumentRepository.findByTypeAndVersion(type, version)
			.map(this::toResponse)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy document version not found"));
	}

	@Transactional
	PolicyDocumentDtos.PolicyDocumentResponse create(PolicyDocumentDtos.PolicyDocumentRequest request) {
		if (policyDocumentRepository.findByTypeAndVersion(request.type(), request.version()).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Policy document version already exists");
		}
		return toResponse(policyDocumentRepository.save(new PolicyDocument(
			request.type(),
			request.version(),
			request.title(),
			request.content(),
			request.effectiveFrom()
		)));
	}

	@Transactional
	PolicyDocumentDtos.PolicyDocumentResponse update(UUID policyId, PolicyDocumentDtos.PolicyDocumentRequest request) {
		PolicyDocument policy = findPolicy(policyId);
		if (policy.getType() != request.type() || !policy.getVersion().equals(request.version())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Policy type and version cannot be changed");
		}
		try {
			policy.update(request.title(), request.content(), request.effectiveFrom());
			return toResponse(policy);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	@Transactional
	PolicyDocumentDtos.PolicyDocumentResponse activate(UUID policyId) {
		PolicyDocument policy = findPolicy(policyId);
		try {
			policyDocumentRepository.findByTypeAndStatus(policy.getType(), PolicyDocumentStatus.ACTIVE)
				.ifPresent(PolicyDocument::archive);
			policy.activate();
			return toResponse(policy);
		} catch (IllegalStateException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
		}
	}

	private PolicyDocument findPolicy(UUID policyId) {
		return policyDocumentRepository.findById(policyId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy document not found"));
	}

	private PolicyDocumentDtos.PolicyDocumentResponse toResponse(PolicyDocument policy) {
		return new PolicyDocumentDtos.PolicyDocumentResponse(
			policy.getId(),
			policy.getType(),
			policy.getVersion(),
			policy.getTitle(),
			policy.getContent(),
			policy.getEffectiveFrom(),
			policy.getStatus(),
			policy.getCreatedAt()
		);
	}
}
