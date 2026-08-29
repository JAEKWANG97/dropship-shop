package com.dropshipshop.api.auth.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.supplierportal.SupplierPortalFeatureGate;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class SupplierPortalFeatureGateFilter extends OncePerRequestFilter {

	private final SupplierPortalFeatureGate featureGate;

	SupplierPortalFeatureGateFilter(SupplierPortalFeatureGate featureGate) {
		this.featureGate = featureGate;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if (featureGate.isEnabled()) {
			return true;
		}
		String path = request.getRequestURI();
		return !(path.equals("/api/supplier-applications")
			|| path.equals("/api/supplier-invites/session")
			|| path.startsWith("/api/supplier/auth/")
			|| path.startsWith("/api/supplier/"));
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		SecurityErrorResponseWriter.write(
			request,
			response,
			HttpStatus.NOT_FOUND,
			ApiErrorCode.RESOURCE_NOT_FOUND,
			"Resource not found"
		);
	}
}
