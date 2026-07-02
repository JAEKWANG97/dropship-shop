package com.dropshipshop.api.catalog;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class ProductImageResourceConfig implements WebMvcConfigurer {

	private final Path imageStoragePath;

	ProductImageResourceConfig(
		@Value("${app.storage.local.upload-dir:${app.catalog.image-storage-path:build/product-images}}") String imageStoragePath
	) {
		this.imageStoragePath = Path.of(imageStoragePath);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/uploads/products/**")
			.addResourceLocations(imageStoragePath.toUri().toString() + "/");
	}
}
