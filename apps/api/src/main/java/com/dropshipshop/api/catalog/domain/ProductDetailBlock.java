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

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_image_id")
	private ProductImage productImage;

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

	public ProductDetailBlock(Product product, ProductImage productImage, int sortOrder, String altText) {
		if (productImage == null || productImage.getType() != ProductImageType.DETAIL
			|| !sameProduct(product, productImage.getProduct())) {
			throw new IllegalArgumentException("A DETAIL image owned by the same product is required");
		}
		this.product = product;
		this.type = ProductDetailBlockType.IMAGE;
		this.imageUrl = productImage.getImageUrl();
		this.productImage = productImage;
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

	public Product getProduct() {
		return product;
	}

	public ProductDetailBlockType getType() {
		return type;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public ProductImage getProductImage() {
		return productImage;
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

	private static boolean sameProduct(Product left, Product right) {
		if (left == right) {
			return true;
		}
		return left != null && right != null && left.getId() != null && left.getId().equals(right.getId());
	}
}
