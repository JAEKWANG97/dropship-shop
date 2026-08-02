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
@Table(name = "products")
public class Product {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false)
	private Supplier supplier;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, length = 500)
	private String summary;

	@Column(name = "base_price", nullable = false)
	private long basePrice;

	@Column(name = "source_price", nullable = false)
	private long sourcePrice;

	@Column(name = "source_url", length = 2000)
	private String sourceUrl;

	@Column(name = "source_item_no", length = 50, unique = true)
	private String sourceItemNo;

	@Enumerated(EnumType.STRING)
	@Column(name = "category_code", nullable = false, length = 80)
	private ProductCategory categoryCode;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "compliance_status", nullable = false, length = 20)
	private ProductComplianceStatus complianceStatus = ProductComplianceStatus.PENDING;

	@Column(name = "thumbnail_image_url", length = 1000)
	private String thumbnailImageUrl;

	@Column(name = "detail_version", nullable = false)
	private int detailVersion = 1;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Product() {
	}

	public Product(Supplier supplier, String name, String summary, long basePrice, ProductStatus status) {
		this(supplier, name, summary, basePrice, basePrice, ProductCategory.PPE_SAFETY_HELMET, status);
	}

	public Product(
		Supplier supplier,
		String name,
		String summary,
		long basePrice,
		ProductCategory categoryCode,
		ProductStatus status
	) {
		this(supplier, name, summary, basePrice, basePrice, categoryCode, status);
	}

	public Product(
		Supplier supplier,
		String name,
		String summary,
		long sourcePrice,
		long basePrice,
		ProductCategory categoryCode,
		ProductStatus status
	) {
		this.supplier = supplier;
		this.name = name;
		this.summary = summary;
		this.sourcePrice = sourcePrice;
		this.basePrice = basePrice;
		this.categoryCode = categoryCode;
		this.status = status;
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

	public void updateBase(Supplier supplier, String name, String summary, long sourcePrice, long basePrice, ProductCategory categoryCode) {
		this.supplier = supplier;
		this.name = name;
		this.summary = summary;
		this.sourcePrice = sourcePrice;
		this.basePrice = basePrice;
		this.categoryCode = categoryCode;
	}

	public void updateStatus(ProductStatus status) {
		this.status = status;
	}

	public void updateComplianceStatus(ProductComplianceStatus complianceStatus) {
		this.complianceStatus = complianceStatus;
	}

	public void updateSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public void updateSourceItemNo(String sourceItemNo) {
		this.sourceItemNo = sourceItemNo;
	}

	public void updateThumbnailImageUrl(String thumbnailImageUrl) {
		this.thumbnailImageUrl = thumbnailImageUrl;
	}

	public void bumpDetailVersion() {
		detailVersion += 1;
	}

	public UUID getId() {
		return id;
	}

	public Supplier getSupplier() {
		return supplier;
	}

	public String getName() {
		return name;
	}

	public String getSummary() {
		return summary;
	}

	public long getBasePrice() {
		return basePrice;
	}

	public long getSourcePrice() {
		return sourcePrice;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public String getSourceItemNo() {
		return sourceItemNo;
	}

	public ProductCategory getCategoryCode() {
		return categoryCode;
	}

	public ProductStatus getStatus() {
		return status;
	}

	public ProductComplianceStatus getComplianceStatus() {
		return complianceStatus;
	}

	public String getThumbnailImageUrl() {
		return thumbnailImageUrl;
	}

	public int getDetailVersion() {
		return detailVersion;
	}
}
