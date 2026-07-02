package com.dropshipshop.api.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileStorageTest {

	@TempDir
	Path uploadDir;

	@Test
	void storesFileAndReturnsPublicUrl() throws Exception {
		LocalFileStorage storage = new LocalFileStorage(uploadDir.toString(), "https://cdn.example.com/products/");
		MockMultipartFile file = new MockMultipartFile("file", "helmet.webp", "image/webp", "image".getBytes());

		StoredFile storedFile = storage.store("products/helmet.webp", file);

		assertThat(storedFile.url()).isEqualTo("https://cdn.example.com/products/products/helmet.webp");
		assertThat(storedFile.objectKey()).isEqualTo("products/helmet.webp");
		assertThat(storedFile.size()).isEqualTo(5);
		assertThat(Files.readString(uploadDir.resolve("products/helmet.webp"))).isEqualTo("image");
	}

	@Test
	void rejectsPathTraversalObjectKey() {
		LocalFileStorage storage = new LocalFileStorage(uploadDir.toString(), "/uploads/products");
		MockMultipartFile file = new MockMultipartFile("file", "helmet.webp", "image/webp", "image".getBytes());

		assertThatThrownBy(() -> storage.store("../helmet.webp", file))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
