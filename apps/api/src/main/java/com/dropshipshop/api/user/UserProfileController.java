package com.dropshipshop.api.user;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dropshipshop.api.auth.security.CurrentUser;

@RestController
@RequestMapping("/api/me")
class UserProfileController {

	private final CurrentUser currentUser;

	UserProfileController(CurrentUser currentUser) {
		this.currentUser = currentUser;
	}

	@GetMapping
	UserProfileResponse me(Authentication authentication) {
		return new UserProfileResponse(currentUser.id(authentication));
	}

	record UserProfileResponse(UUID userId) {
	}
}
