package com.dropshipshop.api.supplierportal;

import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.dropshipshop.api.catalog.domain.Supplier;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;
import com.dropshipshop.api.policy.PolicyDocumentRepository;
import com.dropshipshop.api.policy.domain.PolicyDocument;
import com.dropshipshop.api.policy.domain.PolicyDocumentStatus;
import com.dropshipshop.api.policy.domain.PolicyDocumentType;
import com.dropshipshop.api.supplierportal.SupplierInvitationService.IssuedInvite;
import com.dropshipshop.api.supplierportal.domain.SupplierApplication;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationApprovalMode;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationReviewAction;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationReviewReasonCode;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationStatus;
import com.dropshipshop.api.supplierportal.repository.SupplierApplicationRepository;
import com.dropshipshop.api.supplierportal.repository.SupplierInviteRepository;
import com.dropshipshop.api.supplierportal.repository.SupplierPortalActionHistoryRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class SupplierApplicationService {

	private final SupplierApplicationRepository applicationRepository;
	private final SupplierRepository supplierRepository;
	private final SupplierInviteRepository inviteRepository;
	private final SupplierPortalActionHistoryRepository actionHistoryRepository;
	private final PolicyDocumentRepository policyDocumentRepository;
	private final SupplierInvitationService invitationService;
	private final SupplierPortalFeatureGate featureGate;
	private final SupplierPortalProperties properties;
	private final SupplierPortalHasher hasher;
	private final SupplierPortalInputPolicy inputPolicy;
	private final SupplierApplicationRateLimiter rateLimiter;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;

	SupplierApplicationService(
		SupplierApplicationRepository applicationRepository,
		SupplierRepository supplierRepository,
		SupplierInviteRepository inviteRepository,
		SupplierPortalActionHistoryRepository actionHistoryRepository,
		PolicyDocumentRepository policyDocumentRepository,
		SupplierInvitationService invitationService,
		SupplierPortalFeatureGate featureGate,
		SupplierPortalProperties properties,
		SupplierPortalHasher hasher,
		SupplierPortalInputPolicy inputPolicy,
		SupplierApplicationRateLimiter rateLimiter,
		ObjectMapper objectMapper,
		PlatformTransactionManager transactionManager
	) {
		this.applicationRepository = applicationRepository;
		this.supplierRepository = supplierRepository;
		this.inviteRepository = inviteRepository;
		this.actionHistoryRepository = actionHistoryRepository;
		this.policyDocumentRepository = policyDocumentRepository;
		this.invitationService = invitationService;
		this.featureGate = featureGate;
		this.properties = properties;
		this.hasher = hasher;
		this.inputPolicy = inputPolicy;
		this.rateLimiter = rateLimiter;
		this.objectMapper = objectMapper;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public SupplierPortalDtos.ApplicationAcceptedResponse submit(
		SupplierPortalDtos.ApplicationSubmitRequest request,
		String idempotencyKey,
		String remoteAddress
	) {
		featureGate.requirePublicReleased();
		rateLimiter.check(remoteAddress);
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		NormalizedApplication normalized = normalize(request);
		String requestHash = hasher.hmac(
			"supplier-application-submit",
			key,
			normalized.supplierName(),
			normalized.contactName(),
			normalized.contactEmail(),
			normalized.contactPhone(),
			normalized.memo(),
			request.consentPolicyVersion(),
			Boolean.toString(request.privacyAgreed())
		);

		try {
			return transactionTemplate.execute(status -> submitInTransaction(
				request,
				normalized,
				key,
				requestHash,
				Instant.now()
			));
		} catch (DataIntegrityViolationException exception) {
			return transactionTemplate.execute(status -> resolveSubmissionRace(key, requestHash));
		}
	}

	public SupplierPortalDtos.ApplicationPageResponse list(
		SupplierApplicationStatus status,
		int page,
		int size
	) {
		PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<SupplierApplication> result = transactionTemplate.execute(transactionStatus -> status == null
			? applicationRepository.findAll(pageable)
			: applicationRepository.findAllByStatus(status, pageable));
		return new SupplierPortalDtos.ApplicationPageResponse(
			result.getContent().stream().map(this::toSummary).toList(),
			result.getNumber(),
			result.getSize(),
			result.getTotalElements(),
			result.getTotalPages()
		);
	}

	public SupplierPortalDtos.ApplicationDetailResponse get(UUID applicationId) {
		return transactionTemplate.execute(status -> toDetail(applicationRepository.findById(applicationId)
			.orElseThrow(() -> notFound("Supplier application not found"))));
	}

	public ReviewOutcome approve(
		UUID applicationId,
		UUID adminId,
		String idempotencyKey,
		SupplierPortalDtos.ApplicationApproveRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = inputPolicy.requirePiiFreeReason(request.internalReason(), 200);
		validateApprovalTarget(request);
		String requestHash = hasher.hmac(
			"supplier-application-review",
			SupplierApplicationReviewAction.APPROVE.name(),
			key,
			request.approvalMode().name(),
			request.existingSupplierId() == null ? null : request.existingSupplierId().toString(),
			request.reviewReasonCode().name(),
			reason
		);
		return transactionTemplate.execute(status -> approveInTransaction(
			applicationId,
			adminId,
			key,
			requestHash,
			reason,
			request,
			Instant.now()
		));
	}

	public ReviewOutcome reject(
		UUID applicationId,
		UUID adminId,
		String idempotencyKey,
		SupplierPortalDtos.ApplicationRejectRequest request
	) {
		String key = inputPolicy.requireIdempotencyKey(idempotencyKey);
		String reason = inputPolicy.requirePiiFreeReason(request.internalReason(), 200);
		if (request.reviewReasonCode() == SupplierApplicationReviewReasonCode.APPLICATION_APPROVED) {
			throw validation("Rejection requires a rejection reason code");
		}
		String requestHash = hasher.hmac(
			"supplier-application-review",
			SupplierApplicationReviewAction.REJECT.name(),
			key,
			request.reviewReasonCode().name(),
			reason
		);
		return transactionTemplate.execute(status -> rejectInTransaction(
			applicationId,
			adminId,
			key,
			requestHash,
			reason,
			request,
			Instant.now()
		));
	}

	private SupplierPortalDtos.ApplicationAcceptedResponse submitInTransaction(
		SupplierPortalDtos.ApplicationSubmitRequest request,
		NormalizedApplication normalized,
		String key,
		String requestHash,
		Instant now
	) {
		PolicyDocument activePolicy = policyDocumentRepository
			.findByTypeAndStatus(PolicyDocumentType.SUPPLIER_APPLICATION_PRIVACY, PolicyDocumentStatus.ACTIVE)
			.filter(policy -> !policy.getEffectiveFrom().isAfter(now))
			.orElseThrow(() -> new ApiErrorException(
				HttpStatus.SERVICE_UNAVAILABLE,
				ApiErrorCode.POLICY_UNAVAILABLE,
				"Supplier application privacy policy is unavailable"
			));
		if (!activePolicy.getVersion().equals(request.consentPolicyVersion())) {
			throw new ApiErrorException(
				HttpStatus.CONFLICT,
				ApiErrorCode.POLICY_VERSION_MISMATCH,
				"Supplier application privacy policy changed"
			);
		}

		SupplierApplication replay = applicationRepository.findByIdempotencyKey(key).orElse(null);
		if (replay != null) {
			if (replay.matchesSubmissionReplay(key, requestHash)) {
				return SupplierPortalDtos.ApplicationAcceptedResponse.generic();
			}
			throw applicationConflict();
		}

		SupplierApplication active = applicationRepository
			.findActiveByNormalizedContactEmailForUpdate(normalized.contactEmail())
			.orElse(null);
		if (active != null && active.expireAndAnonymize(now)) {
			applicationRepository.saveAndFlush(active);
			active = null;
		}
		if (active != null) {
			throw applicationConflict();
		}

		applicationRepository.saveAndFlush(SupplierApplication.submit(
			normalized.supplierName(),
			normalized.contactName(),
			normalized.contactEmail(),
			normalized.contactEmail(),
			normalized.contactPhone(),
			normalized.memo(),
			key,
			requestHash,
			activePolicy.getVersion(),
			now,
			properties.applicationRetention()
		));
		return SupplierPortalDtos.ApplicationAcceptedResponse.generic();
	}

	private SupplierPortalDtos.ApplicationAcceptedResponse resolveSubmissionRace(String key, String requestHash) {
		SupplierApplication existing = applicationRepository.findByIdempotencyKey(key).orElse(null);
		if (existing != null && existing.matchesSubmissionReplay(key, requestHash)) {
			return SupplierPortalDtos.ApplicationAcceptedResponse.generic();
		}
		throw applicationConflict();
	}

	private ReviewOutcome approveInTransaction(
		UUID applicationId,
		UUID adminId,
		String key,
		String requestHash,
		String reason,
		SupplierPortalDtos.ApplicationApproveRequest request,
		Instant now
	) {
		SupplierApplication application = applicationRepository.findByIdForUpdate(applicationId)
			.orElseThrow(() -> notFound("Supplier application not found"));
		ReviewOutcome replay = terminalReplay(application, SupplierApplicationReviewAction.APPROVE, key, requestHash);
		if (replay != null) {
			return replay;
		}
		featureGate.requireInvitationMutationReleased();
		if (application.expireAndAnonymize(now)) {
			return ReviewOutcome.applicationExpired();
		}
		Supplier supplier = request.approvalMode() == SupplierApplicationApprovalMode.CREATE_NEW
			? createSupplier(application)
			: linkExistingSupplier(application, request.existingSupplierId());
		IssuedInvite invite = invitationService.issueForApproval(supplier, application.getId(), adminId, now);
		SupplierPortalDtos.ApplicationReviewResponse response = new SupplierPortalDtos.ApplicationReviewResponse(
			application.getId(),
			SupplierApplicationStatus.APPROVED,
			supplier.getId(),
			invite.inviteId(),
			invite.expiresAt(),
			supplier.getPortalStatus(),
			supplier.getStatus()
		);
		String snapshot = json(response);
		application.approve(
			supplier,
			request.approvalMode(),
			request.approvalMode() == SupplierApplicationApprovalMode.LINK_EXISTING ? supplier : null,
			adminId,
			request.reviewReasonCode(),
			reason,
			key,
			requestHash,
			snapshot,
			now
		);
		return ReviewOutcome.completed(response);
	}

	private ReviewOutcome rejectInTransaction(
		UUID applicationId,
		UUID adminId,
		String key,
		String requestHash,
		String reason,
		SupplierPortalDtos.ApplicationRejectRequest request,
		Instant now
	) {
		SupplierApplication application = applicationRepository.findByIdForUpdate(applicationId)
			.orElseThrow(() -> notFound("Supplier application not found"));
		ReviewOutcome replay = terminalReplay(application, SupplierApplicationReviewAction.REJECT, key, requestHash);
		if (replay != null) {
			return replay;
		}
		if (application.expireAndAnonymize(now)) {
			return ReviewOutcome.applicationExpired();
		}
		SupplierPortalDtos.ApplicationReviewResponse response = new SupplierPortalDtos.ApplicationReviewResponse(
			application.getId(),
			SupplierApplicationStatus.REJECTED,
			null,
			null,
			null,
			null,
			null
		);
		application.reject(
			adminId,
			request.reviewReasonCode(),
			reason,
			key,
			requestHash,
			json(response),
			now,
			properties.applicationRetention()
		);
		return ReviewOutcome.completed(response);
	}

	private ReviewOutcome terminalReplay(
		SupplierApplication application,
		SupplierApplicationReviewAction action,
		String key,
		String requestHash
	) {
		if (application.getStatus() == SupplierApplicationStatus.SUBMITTED) {
			return null;
		}
		if (application.matchesReviewReplay(action, key, requestHash)
			&& application.getReviewResultSnapshot() != null) {
			return ReviewOutcome.completed(fromJson(
				application.getReviewResultSnapshot(),
				SupplierPortalDtos.ApplicationReviewResponse.class
			));
		}
		throw applicationConflict();
	}

	private Supplier createSupplier(SupplierApplication application) {
		if (supplierRepository.existsByCanonicalEmail(application.getNormalizedContactEmail())) {
			throw applicationConflict();
		}
		return supplierRepository.saveAndFlush(Supplier.portalApplicant(
			application.getSupplierName(),
			application.getContactName(),
			application.getContactPhone(),
			application.getNormalizedContactEmail(),
			application.getMemo()
		));
	}

	private Supplier linkExistingSupplier(SupplierApplication application, UUID supplierId) {
		Supplier supplier = supplierRepository.findByIdForUpdate(supplierId)
			.orElseThrow(() -> notFound("Supplier not found"));
		boolean neverEnrolled = supplier.getPortalStatus() == SupplierPortalStatus.DISABLED
			&& !supplier.isPortalEnrolled()
			&& supplier.getManagerUserId() == null
			&& !inviteRepository.existsBySupplier_Id(supplierId)
			&& !applicationRepository.existsByApprovedSupplier_Id(supplierId)
			&& !applicationRepository.existsByRequestedExistingSupplier_Id(supplierId)
			&& !actionHistoryRepository.existsBySupplier_Id(supplierId);
		if (!neverEnrolled || supplierRepository.existsByCanonicalEmailAndIdNot(
			application.getNormalizedContactEmail(),
			supplierId
		)) {
			throw applicationConflict();
		}
		supplier.enrollLegacyPortal(application.getNormalizedContactEmail());
		return supplier;
	}

	private void validateApprovalTarget(SupplierPortalDtos.ApplicationApproveRequest request) {
		if (request.reviewReasonCode() != SupplierApplicationReviewReasonCode.APPLICATION_APPROVED) {
			throw validation("Approval requires APPLICATION_APPROVED reason code");
		}
		if (request.approvalMode() == SupplierApplicationApprovalMode.CREATE_NEW
			&& request.existingSupplierId() != null) {
			throw validation("CREATE_NEW cannot target an existing supplier");
		}
		if (request.approvalMode() == SupplierApplicationApprovalMode.LINK_EXISTING
			&& request.existingSupplierId() == null) {
			throw validation("LINK_EXISTING requires an existing supplier");
		}
	}

	private NormalizedApplication normalize(SupplierPortalDtos.ApplicationSubmitRequest request) {
		return new NormalizedApplication(
			hasher.normalizeText(request.supplierName()),
			hasher.normalizeText(request.contactName()),
			hasher.normalizeEmail(request.contactEmail()),
			blankToNull(hasher.normalizeText(request.contactPhone())),
			blankToNull(hasher.normalizeText(request.memo()))
		);
	}

	private SupplierPortalDtos.ApplicationSummaryResponse toSummary(SupplierApplication application) {
		return new SupplierPortalDtos.ApplicationSummaryResponse(
			application.getId(),
			application.getSupplierName(),
			application.getContactName(),
			application.getContactEmail(),
			application.getStatus(),
			application.getRetentionExpiresAt(),
			application.getCreatedAt()
		);
	}

	private SupplierPortalDtos.ApplicationDetailResponse toDetail(SupplierApplication application) {
		return new SupplierPortalDtos.ApplicationDetailResponse(
			application.getId(),
			application.getSupplierName(),
			application.getContactName(),
			application.getContactEmail(),
			application.getContactPhone(),
			application.getMemo(),
			application.getConsentPolicyVersion(),
			application.getConsentedAt(),
			application.getStatus(),
			application.getReviewedByAdminId(),
			application.getReviewReasonCode(),
			application.getReviewReason(),
			application.getReviewedAt(),
			application.getApprovedSupplier() == null ? null : application.getApprovedSupplier().getId(),
			application.getApprovalMode(),
			application.getRequestedExistingSupplier() == null ? null : application.getRequestedExistingSupplier().getId(),
			application.getRetentionExpiresAt(),
			application.getAnonymizedAt(),
			application.getCreatedAt()
		);
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize supplier portal result");
		}
	}

	private <T> T fromJson(String value, Class<T> type) {
		try {
			return objectMapper.readValue(value, type);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Failed to read supplier portal result");
		}
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private ApiErrorException applicationConflict() {
		return new ApiErrorException(HttpStatus.CONFLICT, ApiErrorCode.APPLICATION_CONFLICT, "Application cannot be processed");
	}

	private ApiErrorException validation(String message) {
		return new ApiErrorException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED, message);
	}

	private ApiErrorException notFound(String message) {
		return new ApiErrorException(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_NOT_FOUND, message);
	}

	private record NormalizedApplication(
		String supplierName,
		String contactName,
		String contactEmail,
		String contactPhone,
		String memo
	) {
	}

	public record ReviewOutcome(boolean expired, SupplierPortalDtos.ApplicationReviewResponse response) {
		static ReviewOutcome applicationExpired() {
			return new ReviewOutcome(true, null);
		}

		static ReviewOutcome completed(SupplierPortalDtos.ApplicationReviewResponse response) {
			return new ReviewOutcome(false, response);
		}
	}
}
