ALTER TABLE products
    ADD COLUMN source_price BIGINT;

UPDATE products
SET source_price = base_price
WHERE source_price IS NULL;

ALTER TABLE products
    ALTER COLUMN source_price SET NOT NULL;

CREATE TABLE pricing_policies (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    commission_rate NUMERIC(5, 2) NOT NULL,
    tax_buffer_rate NUMERIC(5, 2) NOT NULL,
    overhead_rate NUMERIC(5, 2) NOT NULL,
    safety_margin_rate NUMERIC(5, 2) NOT NULL,
    rounding_unit INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_pricing_policies_one_active
    ON pricing_policies(active)
    WHERE active = TRUE;

INSERT INTO pricing_policies (
    id,
    name,
    commission_rate,
    tax_buffer_rate,
    overhead_rate,
    safety_margin_rate,
    rounding_unit,
    active,
    created_at,
    updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000033',
    '기본 가격 정책',
    5.00,
    10.00,
    5.00,
    5.00,
    100,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
