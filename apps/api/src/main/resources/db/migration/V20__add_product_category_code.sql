ALTER TABLE products ADD COLUMN category_code VARCHAR(80);

UPDATE products SET category_code = 'PPE_SAFETY_HELMET' WHERE category_code IS NULL;

ALTER TABLE products ALTER COLUMN category_code SET NOT NULL;

CREATE INDEX idx_products_category_code ON products(category_code);
