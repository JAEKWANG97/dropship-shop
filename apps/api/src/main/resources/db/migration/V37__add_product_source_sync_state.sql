ALTER TABLE products
    ADD COLUMN source_available BOOLEAN,
    ADD COLUMN source_synced_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN source_sync_error TEXT;

CREATE INDEX idx_products_source_sync
    ON products(source_synced_at)
    WHERE source_item_no IS NOT NULL;
