ALTER TABLE product_options
    ADD COLUMN source_option_code VARCHAR(100),
    ADD COLUMN source_additional_price BIGINT,
    ADD COLUMN source_stock_quantity BIGINT,
    ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_product_options_product_sort
    ON product_options(product_id, sort_order, created_at);
