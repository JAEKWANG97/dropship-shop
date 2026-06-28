package com.dropshipshop.api.policy.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "privacy_processing_items")
public class PrivacyProcessingItem {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false, length = 80)
	private String category;

	@Column(name = "collected_items", nullable = false, columnDefinition = "TEXT")
	private String collectedItems;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String purpose;

	@Column(name = "retention_period", nullable = false, columnDefinition = "TEXT")
	private String retentionPeriod;

	@Column(name = "processor_name", length = 200)
	private String processorName;

	@Column(name = "processor_purpose", columnDefinition = "TEXT")
	private String processorPurpose;

	@Column(name = "third_party_recipient", length = 200)
	private String thirdPartyRecipient;

	@Column(name = "third_party_purpose", columnDefinition = "TEXT")
	private String thirdPartyPurpose;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PrivacyProcessingItem() {
	}

	public PrivacyProcessingItem(
		String category,
		String collectedItems,
		String purpose,
		String retentionPeriod,
		String processorName,
		String processorPurpose,
		String thirdPartyRecipient,
		String thirdPartyPurpose,
		int sortOrder,
		boolean active
	) {
		this.category = category;
		this.collectedItems = collectedItems;
		this.purpose = purpose;
		this.retentionPeriod = retentionPeriod;
		this.processorName = processorName;
		this.processorPurpose = processorPurpose;
		this.thirdPartyRecipient = thirdPartyRecipient;
		this.thirdPartyPurpose = thirdPartyPurpose;
		this.sortOrder = sortOrder;
		this.active = active;
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

	public String getCategory() {
		return category;
	}

	public String getCollectedItems() {
		return collectedItems;
	}

	public String getPurpose() {
		return purpose;
	}

	public String getRetentionPeriod() {
		return retentionPeriod;
	}

	public String getProcessorName() {
		return processorName;
	}

	public String getProcessorPurpose() {
		return processorPurpose;
	}

	public String getThirdPartyRecipient() {
		return thirdPartyRecipient;
	}

	public String getThirdPartyPurpose() {
		return thirdPartyPurpose;
	}
}
