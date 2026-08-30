package com.dropshipshop.api.auth.security;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.supplierportal.AllowedWebOrigins;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		CorsConfigurationSource corsConfigurationSource,
		ObjectProvider<JwtCookieAuthenticationFilter> jwtCookieAuthenticationFilterProvider,
		ObjectProvider<SupplierPortalFeatureGateFilter> supplierPortalFeatureGateFilterProvider
	) throws Exception {
		http
			.cors(cors -> cors.configurationSource(corsConfigurationSource))
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, exception) -> SecurityErrorResponseWriter.write(
					request,
					response,
					HttpStatus.UNAUTHORIZED,
					ApiErrorCode.UNAUTHORIZED,
					"Authentication is required"
				))
				.accessDeniedHandler((request, response, exception) -> SecurityErrorResponseWriter.write(
					request,
					response,
					HttpStatus.FORBIDDEN,
					ApiErrorCode.FORBIDDEN,
					"Access is denied"
				))
			);

		jwtCookieAuthenticationFilterProvider.ifAvailable(filter -> {
			http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
			supplierPortalFeatureGateFilterProvider.ifAvailable(
				gateFilter -> http.addFilterBefore(gateFilter, JwtCookieAuthenticationFilter.class)
			);
		});

		return http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/health", "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
				.requestMatchers("/api/auth/oauth2/**").permitAll()
				.requestMatchers("/api/supplier-applications", "/api/supplier-invites/session").permitAll()
				.requestMatchers("/api/supplier/auth/**").permitAll()
				.requestMatchers("/api/dev/login", "/api/dev/login/**").permitAll()
				.requestMatchers("/api/internal/**").permitAll()
				.requestMatchers("/api/products", "/api/products/**").permitAll()
				.requestMatchers("/uploads/products/**").permitAll()
				.requestMatchers("/api/policies", "/api/policies/**").permitAll()
				.requestMatchers("/api/business-profile", "/api/privacy-processing-items").permitAll()
				.requestMatchers("/api/customer-inquiries", "/api/customer-inquiries/**").permitAll()
				.requestMatchers("/api/admin/**").hasRole("ADMIN")
				.requestMatchers("/api/supplier/**").hasRole("SUPPLIER")
				.anyRequest().authenticated()
			)
			.build();
	}

	@Bean
	AllowedWebOrigins allowedWebOrigins(
		@Value("${app.cors.allowed-origins:}") String configuredOrigins
	) {
		return new AllowedWebOrigins(configuredOrigins);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(
		AllowedWebOrigins allowedOrigins
	) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins.values());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "If-Match"));
		configuration.setExposedHeaders(List.of("Location", "ETag"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

}
