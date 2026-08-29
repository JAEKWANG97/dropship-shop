package com.dropshipshop.api.supplierportal;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class SupplierPortalWebMvcConfig implements WebMvcConfigurer {

	private final SupplierPortalOriginInterceptor originInterceptor;

	SupplierPortalWebMvcConfig(SupplierPortalOriginInterceptor originInterceptor) {
		this.originInterceptor = originInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(originInterceptor);
	}
}
