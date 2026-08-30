package com.dropshipshop.api.catalog.domain;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.common.money.MoneyMath;

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

	@Column(name = "source_option_code", length = 100)
	private String sourceOptionCode;

	@Column(name = "source_additional_price")
	private Long sourceAdditionalPrice;

	@Column(name = "source_stock_quantity")
	private Long sourceStockQuantity;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

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
		this(product, name, additionalPrice, status, null, null, null, 0);
	}

	public ProductOption(
		Product product,
		String name,
		long additionalPrice,
		ProductOptionStatus status,
		String sourceOptionCode,
		Long sourceAdditionalPrice,
		Long sourceStockQuantity,
		int sortOrder
	) {
		validatePrices(product, additionalPrice, sourceAdditionalPrice);
		this.product = product;
		this.name = name;
		this.additionalPrice = additionalPrice;
		this.status = status;
		this.sourceOptionCode = sourceOptionCode;
		this.sourceAdditionalPrice = sourceAdditionalPrice;
		this.sourceStockQuantity = sourceStockQuantity;
		this.sortOrder = sortOrder;
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
		update(name, additionalPrice, sourceOptionCode, sourceAdditionalPrice, sourceStockQuantity, sortOrder);
	}

	public void update(
		String name,
		long additionalPrice,
		String sourceOptionCode,
		Long sourceAdditionalPrice,
		Long sourceStockQuantity,
		int sortOrder
	) {
		validatePrices(product, additionalPrice, sourceAdditionalPrice);
		this.name = name;
		this.additionalPrice = additionalPrice;
		this.sourceOptionCode = sourceOptionCode;
		this.sourceAdditionalPrice = sourceAdditionalPrice;
		this.sourceStockQuantity = sourceStockQuantity;
		this.sortOrder = sortOrder;
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

	public String getSourceOptionCode() {
		return sourceOptionCode;
	}

	public Long getSourceAdditionalPrice() {
		return sourceAdditionalPrice;
	}

	public Long getSourceStockQuantity() {
		return sourceStockQuantity;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	private static void validatePrices(Product product, long additionalPrice, Long sourceAdditionalPrice) {
		MoneyMath.requireCustomerUnitPrice(additionalPrice, "additionalPrice");
		MoneyMath.requireCustomerUnitPrice(
			MoneyMath.addNonNegative(product.getBasePrice(), additionalPrice),
			"unitPrice"
		);
		if (sourceAdditionalPrice != null) {
			MoneyMath.requireSupplierUnitCost(sourceAdditionalPrice, "sourceAdditionalPrice");
		}
	}
}
