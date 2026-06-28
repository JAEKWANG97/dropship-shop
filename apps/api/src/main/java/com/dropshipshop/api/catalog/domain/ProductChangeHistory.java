package com.dropshipshop.api.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_change_histories")
public class ProductChangeHistory {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_option_id")
	private ProductOption productOption;

	@Column(name = "admin_user_id", nullable = false)
	private UUID adminUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "change_type", nullable = false, length = 30)
	private ProductChangeType changeType;

	@Column(name = "before_value", columnDefinition = "TEXT")
	private String beforeValue;

	@Column(name = "after_value", columnDefinition = "TEXT")
	private String afterValue;

	@Column(nullable = false, length = 500)
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ProductChangeHistory() {
	}

	public ProductChangeHistory(
		Product product,
		ProductOption productOption,
		UUID adminUserId,
		ProductChangeType changeType,
		String beforeValue,
		String afterValue,
		String reason
	) {
		this.product = product;
		this.productOption = productOption;
		this.adminUserId = adminUserId;
		this.changeType = changeType;
		this.beforeValue = beforeValue;
		this.afterValue = afterValue;
		this.reason = reason;
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public ProductOption getProductOption() {
		return productOption;
	}

	public UUID getAdminUserId() {
		return adminUserId;
	}

	public ProductChangeType getChangeType() {
		return changeType;
	}

	public String getBeforeValue() {
		return beforeValue;
	}

	public String getAfterValue() {
		return afterValue;
	}

	public String getReason() {
		return reason;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
