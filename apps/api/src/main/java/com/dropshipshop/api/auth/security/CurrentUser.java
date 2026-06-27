package com.dropshipshop.api.auth.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

	public UUID id(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
			throw new IllegalStateException("Authenticated user is required");
		}

		return user.userId();
	}
}
