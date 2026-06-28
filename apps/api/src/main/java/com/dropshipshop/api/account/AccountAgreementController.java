package com.dropshipshop.api.account;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/me/agreements")
class AccountAgreementController {

	private final AccountAgreementService accountAgreementService;
	private final CurrentUser currentUser;

	AccountAgreementController(AccountAgreementService accountAgreementService, CurrentUser currentUser) {
		this.accountAgreementService = accountAgreementService;
		this.currentUser = currentUser;
	}

	@GetMapping
	AccountAgreementDtos.AgreementStateResponse getAgreementState(Authentication authentication) {
		return accountAgreementService.getAgreementState(currentUser.id(authentication));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AccountAgreementDtos.AgreeResponse agree(
		@Valid @RequestBody AccountAgreementDtos.AgreeRequest request,
		Authentication authentication
	) {
		UUID userId = currentUser.id(authentication);
		return accountAgreementService.agree(userId, request);
	}
}
