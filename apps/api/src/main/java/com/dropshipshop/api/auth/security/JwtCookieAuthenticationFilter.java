package com.dropshipshop.api.auth.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dropshipshop.api.auth.JwtAccessTokenService;
import com.dropshipshop.api.auth.AuthProperties;
import com.dropshipshop.api.user.domain.UserAccount;
import com.dropshipshop.api.user.domain.UserRole;
import com.dropshipshop.api.user.domain.UserStatus;
import com.dropshipshop.api.user.repository.UserAccountRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

	private final AuthProperties authProperties;
	private final JwtAccessTokenService jwtAccessTokenService;
	private final UserAccountRepository userAccountRepository;

	JwtCookieAuthenticationFilter(
		AuthProperties authProperties,
		JwtAccessTokenService jwtAccessTokenService,
		UserAccountRepository userAccountRepository
	) {
		this.authProperties = authProperties;
		this.jwtAccessTokenService = jwtAccessTokenService;
		this.userAccountRepository = userAccountRepository;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			cookieValue(request, authProperties.accessTokenCookieName())
				.flatMap(jwtAccessTokenService::verify)
				.flatMap(this::activeUser)
				.ifPresent(this::authenticate);
		}
		filterChain.doFilter(request, response);
	}

	private Optional<String> cookieValue(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
			.filter(cookie -> name.equals(cookie.getName()))
			.map(Cookie::getValue)
			.findFirst();
	}

	private Optional<UserAccount> activeUser(UUID userId) {
		return userAccountRepository.findByIdAndStatus(userId, UserStatus.ACTIVE);
	}

	private void authenticate(UserAccount user) {
		AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getRole());
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			principal,
			null,
			user.getRole() == UserRole.ADMIN
				? List.of(
					new SimpleGrantedAuthority("ROLE_ADMIN"),
					new SimpleGrantedAuthority("ROLE_CUSTOMER")
				)
				: List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
		);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
