ALTER TABLE products
    ADD COLUMN source_item_no VARCHAR(50);

UPDATE products
SET source_item_no = substring(source_url FROM '/([0-9]+)(?:[/?]|$)')
WHERE source_url IS NOT NULL
  AND source_item_no IS NULL;

CREATE INDEX idx_products_source_item_no ON products(source_item_no);

ALTER TABLE order_items
    ADD COLUMN source_item_no VARCHAR(50),
    ADD COLUMN source_option_code VARCHAR(100),
    ADD COLUMN source_unit_price BIGINT;

ALTER TABLE fulfillments
    ADD COLUMN purchase_provider VARCHAR(30),
    ADD COLUMN purchase_status VARCHAR(40),
    ADD COLUMN expected_source_amount BIGINT,
    ADD COLUMN actual_source_amount BIGINT,
    ADD COLUMN request_fingerprint VARCHAR(64),
    ADD COLUMN last_purchase_error TEXT,
    ADD COLUMN purchase_synced_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN supplier_cancel_status VARCHAR(40);

CREATE INDEX idx_fulfillments_purchase_status ON fulfillments(purchase_status);
CREATE UNIQUE INDEX uk_fulfillments_supplier_order_number
    ON fulfillments(supplier_order_number)
    WHERE supplier_order_number IS NOT NULL;

CREATE TABLE supplier_purchase_attempts (
    id UUID PRIMARY KEY,
    fulfillment_id UUID NOT NULL REFERENCES fulfillments(id),
    action VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    external_order_number VARCHAR(100),
    expected_amount BIGINT,
    actual_amount BIGINT,
    failure_code VARCHAR(100),
    failure_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_supplier_purchase_attempts_fulfillment_id
    ON supplier_purchase_attempts(fulfillment_id, created_at DESC);
