package com.dropshipshop.api.common.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

	StoredFile store(String objectKey, MultipartFile file);

	boolean matchesStoredFile(String objectKey, String publicUrl);

	void delete(String objectKey);
}
