package com.dropshipshop.api.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.supplierportal.SupplierPortalDtos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
class SupplierInviteAuthController {

	private final SupplierInviteAuthService authService;

	SupplierInviteAuthController(SupplierInviteAuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/api/supplier-invites/session")
	ResponseEntity<SupplierPortalDtos.InviteSessionResponse> exchange(
		@Valid @RequestBody SupplierPortalDtos.InviteSessionRequest request
	) {
		return authService.exchange(request.token());
	}

	@GetMapping("/api/supplier/auth/kakao/authorize")
	ResponseEntity<Void> authorize(HttpServletRequest request) {
		return authService.authorize(request);
	}

	@GetMapping("/api/supplier/auth/kakao/callback")
	ResponseEntity<Void> callback(
		@RequestParam(required = false) String code,
		@RequestParam(required = false) String state,
		@RequestParam(required = false) String error,
		HttpServletRequest request
	) {
		return authService.callback(code, state, error, request);
	}
}
