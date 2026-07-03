package com.dropshipshop.api.common.storage;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ImageFileValidator {

	private static final List<String> ALLOWED_IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp");
	private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;

	public String validateUpload(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
		}
		if (file.getSize() > MAX_IMAGE_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is too large");
		}
		String extension = extension(file.getOriginalFilename());
		if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image extension");
		}
		validateImageContent(file, extension);
		return extension;
	}

	public void validateImageUrl(String imageUrl) {
		String normalized = imageUrl.toLowerCase(Locale.ROOT);
		int queryIndex = normalized.indexOf('?');
		if (queryIndex >= 0) {
			normalized = normalized.substring(0, queryIndex);
		}
		int hashIndex = normalized.indexOf('#');
		if (hashIndex >= 0) {
			normalized = normalized.substring(0, hashIndex);
		}
		boolean allowed = ALLOWED_IMAGE_EXTENSIONS.stream().anyMatch(normalized::endsWith);
		if (!allowed) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image extension");
		}
	}

	private String extension(String filename) {
		if (filename == null) {
			return "";
		}
		int dotIndex = filename.lastIndexOf('.');
		if (dotIndex < 0) {
			return "";
		}
		return filename.substring(dotIndex).toLowerCase(Locale.ROOT);
	}

	private void validateImageContent(MultipartFile file, String extension) {
		byte[] header;
		try (var inputStream = file.getInputStream()) {
			header = inputStream.readNBytes(12);
		} catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image file");
		}
		boolean valid = switch (extension) {
			case ".jpg", ".jpeg" -> isJpeg(header);
			case ".png" -> isPng(header);
			case ".webp" -> isWebp(header);
			default -> false;
		};
		if (!valid) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image file");
		}
	}

	private boolean isJpeg(byte[] header) {
		return header.length >= 3
			&& (header[0] & 0xFF) == 0xFF
			&& (header[1] & 0xFF) == 0xD8
			&& (header[2] & 0xFF) == 0xFF;
	}

	private boolean isPng(byte[] header) {
		return header.length >= 8
			&& (header[0] & 0xFF) == 0x89
			&& header[1] == 0x50
			&& header[2] == 0x4E
			&& header[3] == 0x47
			&& header[4] == 0x0D
			&& header[5] == 0x0A
			&& header[6] == 0x1A
			&& header[7] == 0x0A;
	}

	private boolean isWebp(byte[] header) {
		return header.length >= 12
			&& header[0] == 0x52
			&& header[1] == 0x49
			&& header[2] == 0x46
			&& header[3] == 0x46
			&& header[8] == 0x57
			&& header[9] == 0x45
			&& header[10] == 0x42
			&& header[11] == 0x50;
	}
}
