package com.dropshipshop.api.auth.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.time.Instant;
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
import com.dropshipshop.api.catalog.repository.SupplierRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

	private final AuthProperties authProperties;
	private final JwtAccessTokenService jwtAccessTokenService;
	private final UserAccountRepository userAccountRepository;
	private final SupplierRepository supplierRepository;

	JwtCookieAuthenticationFilter(
		AuthProperties authProperties,
		JwtAccessTokenService jwtAccessTokenService,
		UserAccountRepository userAccountRepository,
		SupplierRepository supplierRepository
	) {
		this.authProperties = authProperties;
		this.jwtAccessTokenService = jwtAccessTokenService;
		this.userAccountRepository = userAccountRepository;
		this.supplierRepository = supplierRepository;
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
		List<SimpleGrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
		if (user.getRole() == UserRole.ADMIN) {
			authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
		}
		supplierRepository.findByManagerUserId(user.getId())
			.filter(supplier -> supplier.isPortalAuthorityActive(Instant.now()))
			.ifPresent(supplier -> authorities.add(new SimpleGrantedAuthority("ROLE_SUPPLIER")));
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
			principal,
			null,
			authorities
		);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
