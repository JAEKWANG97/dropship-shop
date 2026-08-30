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
		assertThat(storage.matchesStoredFile(storedFile.objectKey(), storedFile.url())).isTrue();
		assertThat(storage.matchesStoredFile(storedFile.objectKey(), "https://evil.example/helmet.webp")).isFalse();
		assertThat(Files.readString(uploadDir.resolve("products/helmet.webp"))).isEqualTo("image");
	}

	@Test
	void rejectsPathTraversalObjectKey() {
		LocalFileStorage storage = new LocalFileStorage(uploadDir.toString(), "/uploads/products");
		MockMultipartFile file = new MockMultipartFile("file", "helmet.webp", "image/webp", "image".getBytes());

		assertThatThrownBy(() -> storage.store("../helmet.webp", file))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void deletesStoredFileIdempotently() {
		LocalFileStorage storage = new LocalFileStorage(uploadDir.toString(), "/uploads/products");
		MockMultipartFile file = new MockMultipartFile("file", "helmet.webp", "image/webp", "image".getBytes());
		storage.store("products/helmet.webp", file);

		storage.delete("products/helmet.webp");
		storage.delete("products/helmet.webp");

		assertThat(uploadDir.resolve("products/helmet.webp")).doesNotExist();
	}

	@Test
	void rejectsPathTraversalDeleteObjectKey() {
		LocalFileStorage storage = new LocalFileStorage(uploadDir.toString(), "/uploads/products");

		assertThatThrownBy(() -> storage.delete("../helmet.webp"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> storage.delete(""))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
