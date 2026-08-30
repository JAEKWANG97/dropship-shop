package com.dropshipshop.api.catalog.domain;

import java.time.Instant;
import java.util.Objects;
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
	private static final UUID LEGACY_SYSTEM_ADMIN_ID = new UUID(0, 0);
	private static final String DOMEGGOOK_CATALOG_SYNC = "DOMEGGOOK_CATALOG_SYNC";

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private Product product;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_option_id")
	private ProductOption productOption;

	@Column(name = "subject_product_id", nullable = false, updatable = false)
	private UUID subjectProductId;

	@Column(name = "subject_product_option_id", updatable = false)
	private UUID subjectProductOptionId;

	@Column(name = "admin_user_id")
	private UUID adminUserId;

	@Column(name = "actor_user_id")
	private UUID actorUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor_type", nullable = false, length = 20, updatable = false)
	private ProductChangeActorType actorType;

	@Column(name = "actor_supplier_id", updatable = false)
	private UUID actorSupplierId;

	@Column(name = "actor_system_code", length = 100, updatable = false)
	private String actorSystemCode;

	@Column(name = "before_version", updatable = false)
	private Long beforeVersion;

	@Column(name = "after_version", updatable = false)
	private Long afterVersion;

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
		this(
			product,
			productOption,
			LEGACY_SYSTEM_ADMIN_ID.equals(adminUserId)
				? ProductChangeActor.system(DOMEGGOOK_CATALOG_SYNC)
				: ProductChangeActor.admin(adminUserId),
			null,
			null,
			changeType,
			beforeValue,
			afterValue,
			reason
		);
		if (!LEGACY_SYSTEM_ADMIN_ID.equals(adminUserId)) {
			this.adminUserId = adminUserId;
		}
	}

	public ProductChangeHistory(
		Product product,
		ProductOption productOption,
		ProductChangeActor actor,
		Long beforeVersion,
		Long afterVersion,
		ProductChangeType changeType,
		String beforeValue,
		String afterValue,
		String reason
	) {
		this.product = Objects.requireNonNull(product, "product");
		this.productOption = productOption;
		this.subjectProductId = product.getId();
		this.subjectProductOptionId = productOption == null ? null : productOption.getId();
		ProductChangeActor changeActor = Objects.requireNonNull(actor, "actor");
		this.actorType = changeActor.type();
		this.actorUserId = changeActor.userId();
		this.actorSupplierId = changeActor.supplierId();
		this.actorSystemCode = changeActor.systemCode();
		this.adminUserId = actor.type() == ProductChangeActorType.ADMIN ? actor.userId() : null;
		this.beforeVersion = beforeVersion;
		this.afterVersion = afterVersion;
		this.changeType = Objects.requireNonNull(changeType, "changeType");
		this.beforeValue = beforeValue;
		this.afterValue = afterValue;
		this.reason = Objects.requireNonNull(reason, "reason");
	}

	@PrePersist
	void prePersist() {
		if (subjectProductId == null) {
			subjectProductId = Objects.requireNonNull(product.getId(), "persisted product id");
		}
		if (productOption != null && subjectProductOptionId == null) {
			subjectProductOptionId = Objects.requireNonNull(productOption.getId(), "persisted product option id");
		}
		createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public ProductOption getProductOption() {
		return productOption;
	}

	public Product getProduct() {
		return product;
	}

	public UUID getSubjectProductId() {
		return subjectProductId;
	}

	public UUID getSubjectProductOptionId() {
		return subjectProductOptionId;
	}

	public UUID getAdminUserId() {
		return adminUserId;
	}

	public UUID getActorUserId() {
		return actorUserId;
	}

	public ProductChangeActorType getActorType() {
		return actorType;
	}

	public UUID getActorSupplierId() {
		return actorSupplierId;
	}

	public String getActorSystemCode() {
		return actorSystemCode;
	}

	public Long getBeforeVersion() {
		return beforeVersion;
	}

	public Long getAfterVersion() {
		return afterVersion;
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
