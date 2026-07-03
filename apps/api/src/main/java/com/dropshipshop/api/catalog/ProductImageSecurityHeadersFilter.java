package com.dropshipshop.api.catalog;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
class ProductImageSecurityHeadersFilter extends OncePerRequestFilter {

	private static final String PRODUCT_UPLOAD_PATH = "/uploads/products/";

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		if (request.getRequestURI().startsWith(PRODUCT_UPLOAD_PATH)) {
			response.setHeader("X-Content-Type-Options", "nosniff");
		}
		filterChain.doFilter(request, response);
	}
}
