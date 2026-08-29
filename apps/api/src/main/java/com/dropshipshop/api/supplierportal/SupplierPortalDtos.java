package com.dropshipshop.api.supplierportal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.dropshipshop.api.catalog.domain.SupplierPortalContractStatus;
import com.dropshipshop.api.catalog.domain.SupplierPortalStatus;
import com.dropshipshop.api.catalog.domain.SupplierSalesAction;
import com.dropshipshop.api.catalog.domain.SupplierStatus;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationApprovalMode;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationReviewReasonCode;
import com.dropshipshop.api.supplierportal.domain.SupplierApplicationStatus;
import com.dropshipshop.api.supplierportal.domain.SupplierInviteRevocationReasonCode;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SupplierPortalDtos {

	private SupplierPortalDtos() {
	}

	public record ApplicationSubmitRequest(
		@NotBlank @Size(max = 100) String supplierName,
		@NotBlank @Size(max = 100) String contactName,
		@NotBlank @Email @Size(max = 320) String contactEmail,
		@Pattern(regexp = "^[0-9+() -]{7,30}$", message = "contactPhone has an invalid format") String contactPhone,
		@Size(max = 1000) String memo,
		@AssertTrue(message = "privacyAgreed must be true") boolean privacyAgreed,
		@NotBlank @Size(max = 50) String consentPolicyVersion
	) {
	}

	public record ApplicationAcceptedResponse(boolean accepted, String message) {
		public static ApplicationAcceptedResponse generic() {
			return new ApplicationAcceptedResponse(true, "신청이 접수되었습니다.");
		}
	}

	public record ApplicationSummaryResponse(
		UUID applicationId,
		String supplierName,
		String contactName,
		String contactEmail,
		SupplierApplicationStatus status,
		Instant retentionExpiresAt,
		Instant createdAt
	) {
	}

	public record ApplicationPageResponse(
		List<ApplicationSummaryResponse> applications,
		int page,
		int size,
		long totalElements,
		int totalPages
	) {
	}

	public record ApplicationDetailResponse(
		UUID applicationId,
		String supplierName,
		String contactName,
		String contactEmail,
		String contactPhone,
		String memo,
		String consentPolicyVersion,
		Instant consentedAt,
		SupplierApplicationStatus status,
		UUID reviewedByAdminId,
		SupplierApplicationReviewReasonCode reviewReasonCode,
		String reviewReason,
		Instant reviewedAt,
		UUID approvedSupplierId,
		SupplierApplicationApprovalMode approvalMode,
		UUID requestedExistingSupplierId,
		Instant retentionExpiresAt,
		Instant anonymizedAt,
		Instant createdAt
	) {
	}

	public record ApplicationApproveRequest(
		@NotNull SupplierApplicationApprovalMode approvalMode,
		UUID existingSupplierId,
		@NotNull SupplierApplicationReviewReasonCode reviewReasonCode,
		@NotBlank @Size(max = 200) String internalReason
	) {
	}

	public record ApplicationRejectRequest(
		@NotNull SupplierApplicationReviewReasonCode reviewReasonCode,
		@NotBlank @Size(max = 200) String internalReason
	) {
	}

	public record ApplicationReviewResponse(
		UUID applicationId,
		SupplierApplicationStatus status,
		UUID supplierId,
		UUID inviteId,
		Instant inviteExpiresAt,
		SupplierPortalStatus portalStatus,
		SupplierStatus salesStatus
	) {
	}

	public record InviteReissueRequest(@NotNull SupplierInviteRevocationReasonCode reasonCode) {
	}

	public record InviteResponse(
		UUID inviteId,
		UUID supplierId,
		Instant expiresAt,
		String status
	) {
	}

	public record PortalStatusRequest(
		@NotNull SupplierPortalStatus portalStatus,
		@NotNull SupplierSalesAction salesAction,
		@NotBlank @Size(max = 200) String reason
	) {
	}

	public record SalesStatusRequest(
		@NotNull SupplierStatus status,
		@NotBlank @Size(max = 200) String reason
	) {
	}

	public record ManagerDisconnectRequest(
		@NotNull SupplierSalesAction salesAction,
		@NotBlank @Size(max = 200) String reason
	) {
	}

	public record ContactEmailRequest(
		@NotBlank @Email @Size(max = 320) String contactEmail,
		@NotNull SupplierSalesAction salesAction,
		@NotBlank @Size(max = 200) String reason
	) {
	}

	public record SupplierLifecycleResponse(
		UUID supplierId,
		String name,
		String contactName,
		String contactEmail,
		UUID managerUserId,
		SupplierPortalStatus portalStatus,
		SupplierStatus salesStatus,
		SupplierPortalContractStatus contractStatus,
		String contractVersion,
		Instant contractEffectiveAt,
		Instant contractExpiresAt,
		Instant contactEmailVerifiedAt
	) {
	}

	public record InviteSessionRequest(
		@NotBlank @Size(min = 40, max = 200) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String token
	) {
	}

	public record InviteSessionResponse(String next) {
	}

	public record SupplierMeResponse(
		UUID userId,
		UUID supplierId,
		String name,
		SupplierPortalStatus portalStatus,
		SupplierStatus salesStatus,
		SupplierPortalContractStatus contractStatus,
		String contractVersion,
		Instant contractEffectiveAt,
		Instant contractExpiresAt
	) {
	}
}
