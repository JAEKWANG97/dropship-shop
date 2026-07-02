package com.dropshipshop.api.common.storage;

public record StoredFile(
	String url,
	String objectKey,
	long size,
	String contentType
) {
}
