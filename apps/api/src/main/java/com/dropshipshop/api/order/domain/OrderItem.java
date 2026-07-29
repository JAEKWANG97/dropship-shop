package com.dropshipshop.api.order.domain;

import java.time.Instant;
import java.util.UUID;

import com.dropshipshop.api.catalog.domain.Product;
import com.dropshipshop.api.catalog.domain.ProductOption;
import com.dropshipshop.api.catalog.domain.Supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private CustomerOrder order;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_option_id", nullable = false)
	private ProductOption productOption;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "supplier_id", nullable = false)
	private Supplier supplier;

	@Column(name = "product_name", nullable = false, length = 200)
	private String productName;

	@Column(name = "product_summary", nullable = false, length = 500)
	private String productSummary;

	@Column(name = "product_detail_version", nullable = false)
	private int productDetailVersion;

	@Column(name = "product_notice_version")
	private Integer productNoticeVersion;

	@Column(name = "option_name", nullable = false, length = 200)
	private String optionName;

	@Column(name = "unit_price", nullable = false)
	private long unitPrice;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "line_amount", nullable = false)
	private long lineAmount;

	@Column(name = "source_item_no", length = 50)
	private String sourceItemNo;

	@Column(name = "source_option_code", length = 100)
	private String sourceOptionCode;

	@Column(name = "source_unit_price")
	private Long sourceUnitPrice;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected OrderItem() {
	}

	public OrderItem(
		CustomerOrder order,
		Product product,
		ProductOption productOption,
		Integer productNoticeVersion,
		int quantity
	) {
		this.order = order;
		this.product = product;
		this.productOption = productOption;
		this.supplier = product.getSupplier();
		this.productName = product.getName();
		this.productSummary = product.getSummary();
		this.productDetailVersion = product.getDetailVersion();
		this.productNoticeVersion = productNoticeVersion;
		this.optionName = productOption.getName();
		this.unitPrice = product.getBasePrice() + productOption.getAdditionalPrice();
		this.quantity = quantity;
		this.lineAmount = unitPrice * quantity;
		this.sourceItemNo = product.getSourceItemNo();
		this.sourceOptionCode = productOption.getSourceOptionCode();
		this.sourceUnitPrice = product.getSourcePrice()
			+ (productOption.getSourceAdditionalPrice() == null ? 0 : productOption.getSourceAdditionalPrice());
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

	public CustomerOrder getOrder() {
		return order;
	}

	public Product getProduct() {
		return product;
	}

	public ProductOption getProductOption() {
		return productOption;
	}

	public String getProductName() {
		return productName;
	}

	public String getProductSummary() {
		return productSummary;
	}

	public int getProductDetailVersion() {
		return productDetailVersion;
	}

	public Integer getProductNoticeVersion() {
		return productNoticeVersion;
	}

	public String getOptionName() {
		return optionName;
	}

	public long getUnitPrice() {
		return unitPrice;
	}

	public int getQuantity() {
		return quantity;
	}

	public long getLineAmount() {
		return lineAmount;
	}

	public String getSourceItemNo() {
		return sourceItemNo;
	}

	public String getSourceOptionCode() {
		return sourceOptionCode;
	}

	public Long getSourceUnitPrice() {
		return sourceUnitPrice;
	}
}
