package com.dropshipshop.api.claim.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "claim_evidences")
public class ClaimEvidence {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "claim_id", nullable = false)
	private Claim claim;

	@Column(name = "file_url", nullable = false, length = 500)
	private String fileUrl;

	@Column(name = "object_key", nullable = false, length = 500)
	private String objectKey;

	@Column(name = "original_filename", length = 255)
	private String originalFilename;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "uploaded_at", nullable = false, updatable = false)
	private Instant uploadedAt;

	protected ClaimEvidence() {
	}

	public ClaimEvidence(
		Claim claim,
		String fileUrl,
		String objectKey,
		String originalFilename,
		String contentType,
		long sizeBytes
	) {
		this.claim = claim;
		this.fileUrl = fileUrl;
		this.objectKey = objectKey;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
	}

	@PrePersist
	void prePersist() {
		uploadedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public Claim getClaim() {
		return claim;
	}

	public String getFileUrl() {
		return fileUrl;
	}

	public String getObjectKey() {
		return objectKey;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}
}
