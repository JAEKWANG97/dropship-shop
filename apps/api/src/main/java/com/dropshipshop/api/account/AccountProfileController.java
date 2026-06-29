package com.dropshipshop.api.account;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/me")
class AccountProfileController {

	private final AccountProfileService accountProfileService;
	private final CurrentUser currentUser;

	AccountProfileController(AccountProfileService accountProfileService, CurrentUser currentUser) {
		this.accountProfileService = accountProfileService;
		this.currentUser = currentUser;
	}

	@GetMapping("/profile-completion")
	AccountProfileDtos.ProfileCompletionResponse getProfileCompletion(Authentication authentication) {
		return accountProfileService.getProfileCompletion(currentUser.id(authentication));
	}

	@PatchMapping("/profile")
	AccountProfileDtos.ProfileCompletionResponse updateProfile(
		@Valid @RequestBody AccountProfileDtos.ProfileUpdateRequest request,
		Authentication authentication
	) {
		return accountProfileService.updateProfile(currentUser.id(authentication), request);
	}

	@PostMapping("/phone-verifications")
	@ResponseStatus(HttpStatus.CREATED)
	AccountProfileDtos.PhoneVerificationResponse requestPhoneVerification(
		@Valid @RequestBody AccountProfileDtos.PhoneVerificationRequest request,
		Authentication authentication
	) {
		UUID userId = currentUser.id(authentication);
		return accountProfileService.requestPhoneVerification(userId, request);
	}

	@PostMapping("/phone-verifications/confirm")
	AccountProfileDtos.ProfileCompletionResponse confirmPhoneVerification(
		@Valid @RequestBody AccountProfileDtos.PhoneVerificationConfirmRequest request,
		Authentication authentication
	) {
		return accountProfileService.confirmPhoneVerification(currentUser.id(authentication), request);
	}
}
