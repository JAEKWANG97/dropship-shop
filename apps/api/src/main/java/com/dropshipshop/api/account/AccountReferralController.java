package com.dropshipshop.api.account;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/me/referral")
class AccountReferralController {

	private final AccountReferralService accountReferralService;
	private final CurrentUser currentUser;

	AccountReferralController(AccountReferralService accountReferralService, CurrentUser currentUser) {
		this.accountReferralService = accountReferralService;
		this.currentUser = currentUser;
	}

	@GetMapping
	AccountReferralDtos.ReferralStateResponse getReferralState(Authentication authentication) {
		return accountReferralService.getReferralState(currentUser.id(authentication));
	}

	@PostMapping
	AccountReferralDtos.ReferralStateResponse registerReferrer(
		@Valid @RequestBody AccountReferralDtos.ReferralRegisterRequest request,
		Authentication authentication
	) {
		return accountReferralService.registerReferrer(currentUser.id(authentication), request);
	}
}
