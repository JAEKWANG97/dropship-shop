package com.dropshipshop.api.common.storage;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
class LocalFileStorage implements FileStorage {

	private final Path uploadDir;
	private final String publicBaseUrl;

	LocalFileStorage(
		@Value("${app.storage.local.upload-dir:${app.catalog.image-storage-path:build/product-images}}") String uploadDir,
		@Value("${app.storage.public-base-url:/uploads/products}") String publicBaseUrl
	) {
		this.uploadDir = Path.of(uploadDir);
		this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
	}

	@Override
	public StoredFile store(String objectKey, MultipartFile file) {
		Path root = uploadDir.toAbsolutePath().normalize();
		Path target = root.resolve(objectKey).normalize();
		if (!target.startsWith(root)) {
			throw new IllegalArgumentException("Invalid storage object key");
		}
		try {
			Files.createDirectories(target.getParent());
			file.transferTo(target);
		} catch (Exception exception) {
			throw new IllegalStateException("File upload failed", exception);
		}
		return new StoredFile(publicBaseUrl + "/" + objectKey, objectKey, file.getSize(), file.getContentType());
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
