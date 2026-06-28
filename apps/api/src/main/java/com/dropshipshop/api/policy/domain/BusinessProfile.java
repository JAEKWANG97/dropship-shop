package com.dropshipshop.api.policy.domain;

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
@Table(name = "business_profiles")
public class BusinessProfile {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "company_name", nullable = false, length = 200)
	private String companyName;

	@Column(name = "representative_name", nullable = false, length = 100)
	private String representativeName;

	@Column(name = "business_registration_number", nullable = false, length = 50)
	private String businessRegistrationNumber;

	@Column(name = "mail_order_sales_registration_number", nullable = false, length = 100)
	private String mailOrderSalesRegistrationNumber;

	@Column(name = "mail_order_sales_registration_authority", nullable = false, length = 100)
	private String mailOrderSalesRegistrationAuthority;

	@Column(name = "business_address", nullable = false, length = 500)
	private String businessAddress;

	@Column(name = "customer_center_phone", nullable = false, length = 50)
	private String customerCenterPhone;

	@Column(name = "customer_center_email", nullable = false, length = 320)
	private String customerCenterEmail;

	@Column(name = "customer_center_hours", nullable = false, length = 100)
	private String customerCenterHours;

	@Column(name = "privacy_officer_name", nullable = false, length = 100)
	private String privacyOfficerName;

	@Column(name = "privacy_officer_email", nullable = false, length = 320)
	private String privacyOfficerEmail;

	@Column(name = "privacy_officer_phone", nullable = false, length = 50)
	private String privacyOfficerPhone;

	@Column(name = "hosting_provider", nullable = false, length = 200)
	private String hostingProvider;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "effective_from", nullable = false)
	private Instant effectiveFrom;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected BusinessProfile() {
	}

	public BusinessProfile(
		String companyName,
		String representativeName,
		String businessRegistrationNumber,
		String mailOrderSalesRegistrationNumber,
		String mailOrderSalesRegistrationAuthority,
		String businessAddress,
		String customerCenterPhone,
		String customerCenterEmail,
		String customerCenterHours,
		String privacyOfficerName,
		String privacyOfficerEmail,
		String privacyOfficerPhone,
		String hostingProvider,
		boolean active,
		Instant effectiveFrom
	) {
		this.companyName = companyName;
		this.representativeName = representativeName;
		this.businessRegistrationNumber = businessRegistrationNumber;
		this.mailOrderSalesRegistrationNumber = mailOrderSalesRegistrationNumber;
		this.mailOrderSalesRegistrationAuthority = mailOrderSalesRegistrationAuthority;
		this.businessAddress = businessAddress;
		this.customerCenterPhone = customerCenterPhone;
		this.customerCenterEmail = customerCenterEmail;
		this.customerCenterHours = customerCenterHours;
		this.privacyOfficerName = privacyOfficerName;
		this.privacyOfficerEmail = privacyOfficerEmail;
		this.privacyOfficerPhone = privacyOfficerPhone;
		this.hostingProvider = hostingProvider;
		this.active = active;
		this.effectiveFrom = effectiveFrom;
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

	public String getCompanyName() {
		return companyName;
	}

	public String getRepresentativeName() {
		return representativeName;
	}

	public String getBusinessRegistrationNumber() {
		return businessRegistrationNumber;
	}

	public String getMailOrderSalesRegistrationNumber() {
		return mailOrderSalesRegistrationNumber;
	}

	public String getMailOrderSalesRegistrationAuthority() {
		return mailOrderSalesRegistrationAuthority;
	}

	public String getBusinessAddress() {
		return businessAddress;
	}

	public String getCustomerCenterPhone() {
		return customerCenterPhone;
	}

	public String getCustomerCenterEmail() {
		return customerCenterEmail;
	}

	public String getCustomerCenterHours() {
		return customerCenterHours;
	}

	public String getPrivacyOfficerName() {
		return privacyOfficerName;
	}

	public String getPrivacyOfficerEmail() {
		return privacyOfficerEmail;
	}

	public String getPrivacyOfficerPhone() {
		return privacyOfficerPhone;
	}

	public String getHostingProvider() {
		return hostingProvider;
	}

	public Instant getEffectiveFrom() {
		return effectiveFrom;
	}
}
