package com.dropshipshop.api.policy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dropshipshop.api.policy.domain.PolicyDocument;
import com.dropshipshop.api.policy.domain.PolicyDocumentStatus;
import com.dropshipshop.api.policy.domain.PolicyDocumentType;

public interface PolicyDocumentRepository extends JpaRepository<PolicyDocument, UUID> {

	List<PolicyDocument> findAllByOrderByCreatedAtAsc();

	Optional<PolicyDocument> findByTypeAndStatus(PolicyDocumentType type, PolicyDocumentStatus status);

	Optional<PolicyDocument> findByTypeAndVersion(PolicyDocumentType type, String version);
}
