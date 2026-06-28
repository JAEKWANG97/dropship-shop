package com.dropshipshop.api.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dropshipshop.api.auth.AuthProperties;
import com.dropshipshop.api.auth.JwtAccessTokenService;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@Configuration
class JwtCookieAuthenticationConfiguration {

	@Bean
	JwtCookieAuthenticationFilter jwtCookieAuthenticationFilter(
		AuthProperties authProperties,
		JwtAccessTokenService jwtAccessTokenService,
		UserAccountRepository userAccountRepository
	) {
		return new JwtCookieAuthenticationFilter(authProperties, jwtAccessTokenService, userAccountRepository);
	}
}
