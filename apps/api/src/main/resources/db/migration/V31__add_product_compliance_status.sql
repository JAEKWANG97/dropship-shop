ALTER TABLE products
    ADD COLUMN compliance_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

UPDATE products
SET status = 'HIDDEN',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE';
