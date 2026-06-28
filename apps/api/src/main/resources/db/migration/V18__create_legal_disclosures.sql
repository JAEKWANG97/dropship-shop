CREATE TABLE business_profiles (
    id UUID PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL,
    representative_name VARCHAR(100) NOT NULL,
    business_registration_number VARCHAR(50) NOT NULL,
    mail_order_sales_registration_number VARCHAR(100) NOT NULL,
    mail_order_sales_registration_authority VARCHAR(100) NOT NULL,
    business_address VARCHAR(500) NOT NULL,
    customer_center_phone VARCHAR(50) NOT NULL,
    customer_center_email VARCHAR(320) NOT NULL,
    customer_center_hours VARCHAR(100) NOT NULL,
    privacy_officer_name VARCHAR(100) NOT NULL,
    privacy_officer_email VARCHAR(320) NOT NULL,
    privacy_officer_phone VARCHAR(50) NOT NULL,
    hosting_provider VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_business_profiles_active_effective_from ON business_profiles(active, effective_from);

CREATE TABLE privacy_processing_items (
    id UUID PRIMARY KEY,
    category VARCHAR(80) NOT NULL,
    collected_items TEXT NOT NULL,
    purpose TEXT NOT NULL,
    retention_period TEXT NOT NULL,
    processor_name VARCHAR(200),
    processor_purpose TEXT,
    third_party_recipient VARCHAR(200),
    third_party_purpose TEXT,
    sort_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_privacy_processing_items_active_sort_order ON privacy_processing_items(active, sort_order);
