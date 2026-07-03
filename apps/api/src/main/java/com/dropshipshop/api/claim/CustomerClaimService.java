package com.dropshipshop.api.claim;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.dropshipshop.api.claim.domain.Claim;
import com.dropshipshop.api.claim.domain.ClaimEvidence;
import com.dropshipshop.api.claim.domain.ClaimReason;
import com.dropshipshop.api.claim.domain.ClaimStatus;
import com.dropshipshop.api.claim.domain.ClaimType;
import com.dropshipshop.api.claim.domain.RequestedAction;
import com.dropshipshop.api.claim.repository.ClaimEvidenceRepository;
import com.dropshipshop.api.claim.repository.ClaimRepository;
import com.dropshipshop.api.common.storage.FileStorage;
import com.dropshipshop.api.common.storage.ImageFileValidator;
import com.dropshipshop.api.common.storage.StoredFile;
import com.dropshipshop.api.notification.NotificationService;
import com.dropshipshop.api.notification.domain.NotificationType;
import com.dropshipshop.api.order.domain.CustomerOrder;
import com.dropshipshop.api.order.domain.OrderStatus;
import com.dropshipshop.api.order.repository.CustomerOrderRepository;
import com.dropshipshop.api.refund.RefundService;
import com.dropshipshop.api.shipment.domain.Shipment;
import com.dropshipshop.api.shipment.repository.ShipmentRepository;

@Service
class CustomerClaimService {

	private static final EnumSet<ClaimStatus> ACTIVE_CLAIM_STATUSES = EnumSet.of(
		ClaimStatus.REQUESTED,
		ClaimStatus.UNDER_REVIEW,
		ClaimStatus.APPROVED,
		ClaimStatus.EVIDENCE_REQUESTED,
		ClaimStatus.RETURN_WAITING,
		ClaimStatus.RETURN_RECEIVED,
		ClaimStatus.EXCHANGE_SHIPPING,
		ClaimStatus.REFUND_PROCESSING
	);

	private final CustomerOrderRepository orderRepository;
	private final ClaimRepository claimRepository;
	private final ClaimEvidenceRepository claimEvidenceRepository;
	private final RefundService refundService;
	private final ShipmentRepository shipmentRepository;
	private final NotificationService notificationService;
	private final FileStorage fileStorage;
	private final ImageFileValidator imageFileValidator;

	CustomerClaimService(
		CustomerOrderRepository orderRepository,
		ClaimRepository claimRepository,
		ClaimEvidenceRepository claimEvidenceRepository,
		RefundService refundService,
		ShipmentRepository shipmentRepository,
		NotificationService notificationService,
		FileStorage fileStorage,
		ImageFileValidator imageFileValidator
	) {
		this.orderRepository = orderRepository;
		this.claimRepository = claimRepository;
		this.claimEvidenceRepository = claimEvidenceRepository;
		this.refundService = refundService;
		this.shipmentRepository = shipmentRepository;
		this.notificationService = notificationService;
		this.fileStorage = fileStorage;
		this.imageFileValidator = imageFileValidator;
	}

	@Transactional
	ClaimDtos.ClaimResponse selfServiceCancel(
		UUID userId,
		UUID orderId,
		ClaimDtos.CustomerCancelRequest request
	) {
		CustomerOrder order = findCustomerOrder(userId, orderId);
		if (!order.isSelfServiceCancellable()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not eligible for self-service cancellation");
		}
		rejectDuplicateCancellation(order);
		order.markRefundRequested();
		Claim claim = claimRepository.save(new Claim(
			order,
			order.getUser(),
			ClaimType.CANCEL,
			ClaimReason.SIMPLE_CHANGE_OF_MIND,
			ClaimStatus.APPROVED,
			RequestedAction.REFUND,
			request.reason()
		));
		refundService.createCustomerCancelRefund(order);
		notificationService.email(order.getUser(), order, order.getPaymentGroup(), claim, null, NotificationType.CLAIM_STATUS_CHANGED);
		return toResponse(claim);
	}

	@Transactional
	ClaimDtos.ClaimResponse createClaim(UUID userId, UUID orderId, ClaimDtos.CustomerClaimRequest request) {
		return createClaim(userId, orderId, request, List.of());
	}

	@Transactional
	ClaimDtos.ClaimResponse createClaim(
		UUID userId,
		UUID orderId,
		ClaimDtos.CustomerClaimRequest request,
		List<MultipartFile> evidenceFiles
	) {
		CustomerOrder order = findCustomerOrder(userId, orderId);
		List<MultipartFile> uploadFiles = nonEmptyFiles(evidenceFiles);
		validateClaimRequest(request);
		validateEvidenceRequirement(request, uploadFiles);
		validateEvidenceFiles(uploadFiles);
		if (request.claimType() == ClaimType.CANCEL) {
			return createCancellationClaim(order, request, uploadFiles);
		}
		return createReturnExchangeClaim(order, request, uploadFiles);
	}

	@Transactional(readOnly = true)
	ClaimDtos.CustomerClaimListResponse listClaims(UUID userId, UUID orderId) {
		findCustomerOrder(userId, orderId);
		return new ClaimDtos.CustomerClaimListResponse(
			claimRepository.findAllByOrder_IdOrderByCreatedAtAsc(orderId).stream()
				.map(this::toResponse)
				.toList()
		);
	}

	@Transactional(readOnly = true)
	ClaimDtos.ClaimResponse getClaim(UUID userId, UUID orderId, UUID claimId) {
		Claim claim = claimRepository.findByIdAndUser_Id(claimId, userId)
			.filter(item -> item.getOrder().getId().equals(orderId))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
		return toResponse(claim);
	}

	@Transactional
	ClaimDtos.ClaimResponse addEvidence(UUID userId, UUID orderId, UUID claimId, List<MultipartFile> evidenceFiles) {
		Claim claim = claimRepository.findByIdAndUser_Id(claimId, userId)
			.filter(item -> item.getOrder().getId().equals(orderId))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
		List<MultipartFile> uploadFiles = nonEmptyFiles(evidenceFiles);
		if (uploadFiles.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence image is required");
		}
		validateEvidenceFiles(uploadFiles);
		storeEvidenceFiles(claim, uploadFiles);
		return toResponse(claim);
	}

	private ClaimDtos.ClaimResponse createCancellationClaim(
		CustomerOrder order,
		ClaimDtos.CustomerClaimRequest request,
		List<MultipartFile> evidenceFiles
	) {
		if (!order.canRequestCancellationClaim()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order is not eligible for cancellation claim");
		}
		rejectDuplicateClaim(order, ClaimType.CANCEL);
		Claim claim = claimRepository.saveAndFlush(new Claim(
			order,
			order.getUser(),
			ClaimType.CANCEL,
			request.claimReason(),
			ClaimStatus.REQUESTED,
			RequestedAction.REFUND,
			request.customerMemo()
		));
		storeEvidenceFiles(claim, evidenceFiles);
		notificationService.email(order.getUser(), order, order.getPaymentGroup(), claim, null, NotificationType.CLAIM_STATUS_CHANGED);
		return toResponse(claim);
	}

	private ClaimDtos.ClaimResponse createReturnExchangeClaim(
		CustomerOrder order,
		ClaimDtos.CustomerClaimRequest request,
		List<MultipartFile> evidenceFiles
	) {
		if (order.getStatus() != OrderStatus.DELIVERED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Return or exchange claim is allowed only after delivery");
		}
		Shipment shipment = shipmentRepository.findByOrder_Id(order.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivered shipment not found"));
		validateReturnExchangeWindow(request.claimReason(), shipment.getDeliveredAt());
		rejectDuplicateClaim(order, request.claimType());
		Claim claim = claimRepository.saveAndFlush(new Claim(
			order,
			order.getUser(),
			request.claimType(),
			request.claimReason(),
			ClaimStatus.REQUESTED,
			request.claimType() == ClaimType.RETURN ? RequestedAction.REFUND : RequestedAction.EXCHANGE,
			request.customerMemo()
		));
		storeEvidenceFiles(claim, evidenceFiles);
		notificationService.email(order.getUser(), order, order.getPaymentGroup(), claim, null, NotificationType.CLAIM_STATUS_CHANGED);
		return toResponse(claim);
	}

	ClaimDtos.ClaimResponse toResponse(Claim claim) {
		return toResponse(claim, claimEvidenceRepository.findAllByClaim_IdOrderByUploadedAtAsc(claim.getId()));
	}

	ClaimDtos.ClaimResponse toResponse(Claim claim, List<ClaimEvidence> evidenceFiles) {
		return new ClaimDtos.ClaimResponse(
			claim.getId(),
			claim.getOrder().getId(),
			claim.getOrder().getOrderNumber(),
			claim.getOrder().getStatus(),
			claim.getClaimType(),
			claim.getClaimReason(),
			claim.getStatus(),
			customerStatus(claim.getStatus()),
			customerStatusLabel(claim.getStatus()),
			claim.getRequestedAction(),
			claim.getCustomerMemo(),
			claim.getReviewedByAdminId(),
			claim.getAdminReviewReason(),
			claim.getReviewedAt(),
			claim.getReturnReceivedByAdminId(),
			claim.getReturnReceivedAt(),
			claim.getReturnReceivedMemo(),
			claim.getRefundId(),
			claim.getCompletedAt(),
			claim.getCreatedAt(),
			evidenceFiles.stream()
				.map(this::toEvidenceResponse)
				.toList()
		);
	}

	ClaimDtos.ClaimEvidenceResponse toEvidenceResponse(ClaimEvidence evidence) {
		return new ClaimDtos.ClaimEvidenceResponse(
			evidence.getId(),
			evidence.getFileUrl(),
			evidence.getOriginalFilename(),
			evidence.getContentType(),
			evidence.getSizeBytes(),
			evidence.getUploadedAt()
		);
	}

	private CustomerOrder findCustomerOrder(UUID userId, UUID orderId) {
		return orderRepository.findByIdAndUser_Id(orderId, userId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
	}

	private void rejectDuplicateCancellation(CustomerOrder order) {
		rejectDuplicateClaim(order, ClaimType.CANCEL);
	}

	private void rejectDuplicateClaim(CustomerOrder order, ClaimType claimType) {
		if (claimRepository.existsByOrder_IdAndClaimTypeAndStatusIn(
			order.getId(),
			claimType,
			ACTIVE_CLAIM_STATUSES
		)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Active claim already exists");
		}
	}

	private void validateClaimRequest(ClaimDtos.CustomerClaimRequest request) {
		if (request.claimType() == null || request.claimReason() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Claim type and reason are required");
		}
		if (request.customerMemo() == null || request.customerMemo().isBlank() || request.customerMemo().length() > 1000) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer memo is required");
		}
		if (request.claimType() == ClaimType.CANCEL && request.claimReason() != ClaimReason.SIMPLE_CHANGE_OF_MIND) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation claim supports simple change-of-mind reason only");
		}
	}

	private void validateEvidenceRequirement(ClaimDtos.CustomerClaimRequest request, List<MultipartFile> evidenceFiles) {
		if (request.claimReason() != ClaimReason.SIMPLE_CHANGE_OF_MIND && evidenceFiles.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence image is required for this claim reason");
		}
	}

	private List<MultipartFile> nonEmptyFiles(List<MultipartFile> files) {
		if (files == null) {
			return List.of();
		}
		return files.stream()
			.filter(file -> file != null && !file.isEmpty())
			.toList();
	}

	private void validateEvidenceFiles(List<MultipartFile> evidenceFiles) {
		evidenceFiles.forEach(imageFileValidator::validateUpload);
	}

	private void storeEvidenceFiles(Claim claim, List<MultipartFile> evidenceFiles) {
		if (evidenceFiles.isEmpty()) {
			return;
		}
		List<ClaimEvidence> evidences = evidenceFiles.stream()
			.map(file -> storeEvidenceFile(claim, file))
			.toList();
		claimEvidenceRepository.saveAll(evidences);
	}

	private ClaimEvidence storeEvidenceFile(Claim claim, MultipartFile file) {
		String extension = imageFileValidator.validateUpload(file);
		String objectKey = "claims/" + claim.getId() + "/" + UUID.randomUUID() + extension;
		StoredFile storedFile;
		try {
			storedFile = fileStorage.store(objectKey, file);
		} catch (RuntimeException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Evidence upload failed");
		}
		return new ClaimEvidence(
			claim,
			storedFile.url(),
			storedFile.objectKey(),
			file.getOriginalFilename(),
			storedFile.contentType(),
			storedFile.size()
		);
	}

	private void validateReturnExchangeWindow(ClaimReason reason, Instant deliveredAt) {
		if (deliveredAt == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivered time is required for return or exchange claim");
		}
		Instant now = Instant.now();
		if (reason == ClaimReason.SIMPLE_CHANGE_OF_MIND && deliveredAt.isBefore(now.minus(7, ChronoUnit.DAYS))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simple change-of-mind claim period has expired");
		}
		if (reason != ClaimReason.SIMPLE_CHANGE_OF_MIND && deliveredAt.isBefore(now.minus(90, ChronoUnit.DAYS))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seller-fault claim period has expired");
		}
	}

	private String customerStatus(ClaimStatus status) {
		return switch (status) {
			case REQUESTED, UNDER_REVIEW -> "REVIEWING";
			case EVIDENCE_REQUESTED -> "EVIDENCE_REQUESTED";
			case APPROVED -> "APPROVED";
			case REJECTED -> "REJECTED";
			case RETURN_WAITING -> "RETURN_WAITING";
			case RETURN_RECEIVED -> "RETURN_RECEIVED";
			case REFUND_PROCESSING -> "REFUND_PROCESSING";
			case EXCHANGE_SHIPPING -> "EXCHANGE_SHIPPING";
			case COMPLETED -> "COMPLETED";
			case WITHDRAWN -> "WITHDRAWN";
		};
	}

	private String customerStatusLabel(ClaimStatus status) {
		return switch (status) {
			case REQUESTED, UNDER_REVIEW -> "검토 중";
			case EVIDENCE_REQUESTED -> "증빙 요청";
			case APPROVED -> "승인됨";
			case REJECTED -> "거부됨";
			case RETURN_WAITING -> "반송 대기";
			case RETURN_RECEIVED -> "반품 수령됨";
			case REFUND_PROCESSING -> "환불 처리 중";
			case EXCHANGE_SHIPPING -> "교환 배송 중";
			case COMPLETED -> "완료";
			case WITHDRAWN -> "철회됨";
		};
	}
}
