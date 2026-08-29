package com.dropshipshop.api.supplierportal;

import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.dropshipshop.api.common.error.ApiErrorCode;
import com.dropshipshop.api.common.error.ApiErrorException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
class SupplierPortalOriginInterceptor implements HandlerInterceptor {

	private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
	private final AllowedWebOrigins allowedOrigins;

	SupplierPortalOriginInterceptor(AllowedWebOrigins allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!requiresOriginCheck(request)) {
			return true;
		}
		String origin = request.getHeader(HttpHeaders.ORIGIN);
		boolean allowed = origin == null
			? allowedOrigins.allowsReferer(request.getHeader(HttpHeaders.REFERER))
			: allowedOrigins.allowsOriginHeader(origin);
		if (!allowed) {
			throw new ApiErrorException(
				HttpStatus.FORBIDDEN,
				ApiErrorCode.ORIGIN_NOT_ALLOWED,
				"Request origin is not allowed"
			);
		}
		return true;
	}

	private boolean requiresOriginCheck(HttpServletRequest request) {
		if (HttpMethod.OPTIONS.matches(request.getMethod()) || !MUTATING_METHODS.contains(request.getMethod())) {
			return false;
		}
		String path = request.getRequestURI();
		return "/api/supplier-applications".equals(path)
			|| "/api/supplier-invites/session".equals(path)
			|| path.startsWith("/api/supplier/")
			|| path.matches("^/api/admin/supplier-applications/[^/]+/(approve|reject)$")
			|| path.matches("^/api/admin/suppliers/[^/]+/(invite/reissue|portal-status|sales-status|manager-disconnect|contact-email|portal-contract-status)$");
	}
}
