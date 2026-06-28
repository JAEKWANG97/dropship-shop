package com.dropshipshop.api.policy.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "policy_documents",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_policy_documents_type_version",
		columnNames = {"type", "version"}
	)
)
public class PolicyDocument {

	@Id
	@GeneratedValue
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 50)
	private PolicyDocumentType type;

	@Column(nullable = false, length = 50)
	private String version;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(name = "effective_from", nullable = false)
	private Instant effectiveFrom;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PolicyDocumentStatus status = PolicyDocumentStatus.DRAFT;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PolicyDocument() {
	}

	public PolicyDocument(PolicyDocumentType type, String version, String title, String content, Instant effectiveFrom) {
		this.type = type;
		this.version = version;
		this.title = title;
		this.content = content;
		this.effectiveFrom = effectiveFrom;
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}

	public void update(String title, String content, Instant effectiveFrom) {
		if (status != PolicyDocumentStatus.DRAFT) {
			throw new IllegalStateException("Only draft policy documents can be updated");
		}
		this.title = title;
		this.content = content;
		this.effectiveFrom = effectiveFrom;
	}

	public void activate() {
		if (status == PolicyDocumentStatus.ACTIVE) {
			return;
		}
		if (status != PolicyDocumentStatus.DRAFT) {
			throw new IllegalStateException("Only draft policy documents can be activated");
		}
		this.status = PolicyDocumentStatus.ACTIVE;
	}

	public void archive() {
		if (status == PolicyDocumentStatus.ACTIVE) {
			this.status = PolicyDocumentStatus.ARCHIVED;
		}
	}

	public UUID getId() {
		return id;
	}

	public PolicyDocumentType getType() {
		return type;
	}

	public String getVersion() {
		return version;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public Instant getEffectiveFrom() {
		return effectiveFrom;
	}

	public PolicyDocumentStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
