package com.dropshipshop.api.refund;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
class AdminRefundController {

	private final RefundService refundService;
	private final CurrentUser currentUser;

	AdminRefundController(RefundService refundService, CurrentUser currentUser) {
		this.refundService = refundService;
		this.currentUser = currentUser;
	}

	@GetMapping
	RefundDtos.AdminRefundListResponse listRefunds() {
		return refundService.listRefunds();
	}

	@PostMapping("/{refundId}/approve")
	RefundDtos.AdminRefundResponse approve(
		@PathVariable UUID refundId,
		@Valid @RequestBody RefundDtos.RefundApprovalRequest request,
		Authentication authentication
	) {
		return refundService.approve(refundId, currentUser.id(authentication), request);
	}

	@PostMapping("/{refundId}/manual-review")
	RefundDtos.AdminRefundResponse markManualReview(
		@PathVariable UUID refundId,
		@Valid @RequestBody RefundDtos.RefundManualReviewRequest request,
		Authentication authentication
	) {
		return refundService.markManualReview(refundId, currentUser.id(authentication), request);
	}

	@PostMapping("/{refundId}/manual-complete")
	RefundDtos.AdminRefundResponse completeManualBankTransferRefund(
		@PathVariable UUID refundId,
		@Valid @RequestBody RefundDtos.ManualBankTransferRefundCompleteRequest request,
		Authentication authentication
	) {
		return refundService.completeManualBankTransferRefund(refundId, currentUser.id(authentication), request);
	}
}
