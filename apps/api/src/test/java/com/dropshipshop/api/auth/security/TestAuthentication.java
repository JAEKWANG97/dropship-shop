package com.dropshipshop.api.auth.security;

import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.dropshipshop.api.user.domain.UserRole;

public final class TestAuthentication {

	public static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	public static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private TestAuthentication() {
	}

	public static Authentication customer() {
		return authentication(CUSTOMER_ID, UserRole.CUSTOMER);
	}

	public static Authentication customer(UUID userId) {
		return authentication(userId, UserRole.CUSTOMER);
	}

	public static Authentication admin() {
		return authentication(ADMIN_ID, UserRole.ADMIN);
	}

	private static Authentication authentication(UUID userId, UserRole role) {
		return new UsernamePasswordAuthenticationToken(
			new AuthenticatedUser(userId, role),
			null,
			List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
		);
	}
}
