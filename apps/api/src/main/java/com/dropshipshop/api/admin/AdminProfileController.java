package com.dropshipshop.api.admin;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

@RestController
@RequestMapping("/api/admin")
class AdminProfileController {

	private final CurrentUser currentUser;

	AdminProfileController(CurrentUser currentUser) {
		this.currentUser = currentUser;
	}

	@GetMapping("/me")
	@PreAuthorize("hasRole('ADMIN')")
	AdminProfileResponse me(Authentication authentication) {
		return new AdminProfileResponse(currentUser.id(authentication));
	}

	record AdminProfileResponse(UUID userId) {
	}
}
