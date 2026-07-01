package com.dropshipshop.api.catalog.domain;

import java.math.BigDecimal;
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
@Table(name = "pricing_policies")
public class PricingPolicy {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
	private BigDecimal commissionRate;

	@Column(name = "tax_buffer_rate", nullable = false, precision = 5, scale = 2)
	private BigDecimal taxBufferRate;

	@Column(name = "overhead_rate", nullable = false, precision = 5, scale = 2)
	private BigDecimal overheadRate;

	@Column(name = "safety_margin_rate", nullable = false, precision = 5, scale = 2)
	private BigDecimal safetyMarginRate;

	@Column(name = "rounding_unit", nullable = false)
	private int roundingUnit;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PricingPolicy() {
	}

	public PricingPolicy(
		String name,
		BigDecimal commissionRate,
		BigDecimal taxBufferRate,
		BigDecimal overheadRate,
		BigDecimal safetyMarginRate,
		int roundingUnit
	) {
		update(name, commissionRate, taxBufferRate, overheadRate, safetyMarginRate, roundingUnit);
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

	public void update(
		String name,
		BigDecimal commissionRate,
		BigDecimal taxBufferRate,
		BigDecimal overheadRate,
		BigDecimal safetyMarginRate,
		int roundingUnit
	) {
		this.name = name;
		this.commissionRate = commissionRate;
		this.taxBufferRate = taxBufferRate;
		this.overheadRate = overheadRate;
		this.safetyMarginRate = safetyMarginRate;
		this.roundingUnit = roundingUnit;
		this.active = true;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public BigDecimal getCommissionRate() {
		return commissionRate;
	}

	public BigDecimal getTaxBufferRate() {
		return taxBufferRate;
	}

	public BigDecimal getOverheadRate() {
		return overheadRate;
	}

	public BigDecimal getSafetyMarginRate() {
		return safetyMarginRate;
	}

	public int getRoundingUnit() {
		return roundingUnit;
	}

	public boolean isActive() {
		return active;
	}
}
