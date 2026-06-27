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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_detail_blocks")
public class ProductDetailBlock {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductDetailBlockType type;

	@Column(name = "image_url", length = 1000)
	private String imageUrl;

	@Column(name = "html_content", columnDefinition = "TEXT")
	private String htmlContent;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "alt_text", length = 200)
	private String altText;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ProductDetailBlock() {
	}

	public ProductDetailBlock(
		Product product,
		ProductDetailBlockType type,
		String imageUrl,
		String htmlContent,
		int sortOrder,
		String altText
	) {
		this.product = product;
		this.type = type;
		this.imageUrl = imageUrl;
		this.htmlContent = htmlContent;
		this.sortOrder = sortOrder;
		this.altText = altText;
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

	public UUID getId() {
		return id;
	}

	public ProductDetailBlockType getType() {
		return type;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public String getHtmlContent() {
		return htmlContent;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public String getAltText() {
		return altText;
	}
}
