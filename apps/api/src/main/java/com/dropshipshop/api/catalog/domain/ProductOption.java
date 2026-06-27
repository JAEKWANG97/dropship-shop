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
@Table(name = "product_options")
public class ProductOption {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(name = "additional_price", nullable = false)
	private long additionalPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductOptionStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ProductOption() {
	}

	public ProductOption(Product product, String name, long additionalPrice, ProductOptionStatus status) {
		this.product = product;
		this.name = name;
		this.additionalPrice = additionalPrice;
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

	public void update(String name, long additionalPrice) {
		this.name = name;
		this.additionalPrice = additionalPrice;
	}

	public void updateStatus(ProductOptionStatus status) {
		this.status = status;
	}

	public UUID getId() {
		return id;
	}

	public Product getProduct() {
		return product;
	}

	public String getName() {
		return name;
	}

	public long getAdditionalPrice() {
		return additionalPrice;
	}

	public ProductOptionStatus getStatus() {
		return status;
	}
}
