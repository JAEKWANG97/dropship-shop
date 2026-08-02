package com.dropshipshop.api.catalog.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "product_notices",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_product_notices_product_version", columnNames = {"product_id", "version"})
	}
)
public class ProductNotice {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false)
	private int version;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProductNoticeStatus status;

	@Column(name = "product_info_notice", nullable = false, columnDefinition = "TEXT")
	private String productInfoNotice;

	@Column(name = "shipping_info", nullable = false, columnDefinition = "TEXT")
	private String shippingInfo;

	@Column(name = "as_info", nullable = false, columnDefinition = "TEXT")
	private String asInfo;

	@Column(name = "return_exchange_info", nullable = false, columnDefinition = "TEXT")
	private String returnExchangeInfo;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "notice_rows", columnDefinition = "jsonb")
	private List<ProductNoticeRow> noticeRows;

	@Column(name = "effective_from", nullable = false)
	private Instant effectiveFrom;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ProductNotice() {
	}

	public ProductNotice(
		Product product,
		int version,
		String productInfoNotice,
		String shippingInfo,
		String asInfo,
		String returnExchangeInfo
	) {
		this(product, version, productInfoNotice, shippingInfo, asInfo, returnExchangeInfo, List.of());
	}

	public ProductNotice(
		Product product,
		int version,
		String productInfoNotice,
		String shippingInfo,
		String asInfo,
		String returnExchangeInfo,
		List<ProductNoticeRow> noticeRows
	) {
		this.product = product;
		this.version = version;
		this.status = ProductNoticeStatus.ACTIVE;
		this.productInfoNotice = productInfoNotice;
		this.shippingInfo = shippingInfo;
		this.asInfo = asInfo;
		this.returnExchangeInfo = returnExchangeInfo;
		this.noticeRows = noticeRows == null ? List.of() : List.copyOf(noticeRows);
		this.effectiveFrom = Instant.now();
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

	public int getVersion() {
		return version;
	}

	public ProductNoticeStatus getStatus() {
		return status;
	}

	public String getProductInfoNotice() {
		return productInfoNotice;
	}

	public String getShippingInfo() {
		return shippingInfo;
	}

	public String getAsInfo() {
		return asInfo;
	}

	public String getReturnExchangeInfo() {
		return returnExchangeInfo;
	}

	public List<ProductNoticeRow> getNoticeRows() {
		return noticeRows == null ? List.of() : List.copyOf(noticeRows);
	}
}
