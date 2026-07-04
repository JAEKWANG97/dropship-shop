package com.dropshipshop.api.account;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/referrals")
@PreAuthorize("hasRole('ADMIN')")
class AdminReferralController {

	private final AccountReferralService accountReferralService;

	AdminReferralController(AccountReferralService accountReferralService) {
		this.accountReferralService = accountReferralService;
	}

	@GetMapping
	AccountReferralDtos.AdminReferralListResponse listReferrals() {
		return accountReferralService.listAdminReferrals();
	}
}
