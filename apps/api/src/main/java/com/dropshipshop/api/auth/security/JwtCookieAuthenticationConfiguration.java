package com.dropshipshop.api.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dropshipshop.api.auth.AuthProperties;
import com.dropshipshop.api.auth.JwtAccessTokenService;
import com.dropshipshop.api.catalog.repository.SupplierRepository;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;
import com.dropshipshop.api.user.repository.UserAccountRepository;

@Configuration
class JwtCookieAuthenticationConfiguration {

	@Bean
	SupplierPortalFeatureGateFilter supplierPortalFeatureGateFilter(SupplierPortalFeatureGate featureGate) {
		return new SupplierPortalFeatureGateFilter(featureGate);
	}

	@Bean
	JwtCookieAuthenticationFilter jwtCookieAuthenticationFilter(
		AuthProperties authProperties,
		JwtAccessTokenService jwtAccessTokenService,
		UserAccountRepository userAccountRepository,
		SupplierRepository supplierRepository
	) {
		return new JwtCookieAuthenticationFilter(
			authProperties,
			jwtAccessTokenService,
			userAccountRepository,
			supplierRepository
		);
	}
}
