ALTER TABLE products
    ADD COLUMN minimum_order_quantity INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN order_quantity_step INTEGER NOT NULL DEFAULT 1;

ALTER TABLE products
    ADD CONSTRAINT chk_products_minimum_order_quantity
        CHECK (minimum_order_quantity BETWEEN 1 AND 99),
    ADD CONSTRAINT chk_products_order_quantity_step
        CHECK (order_quantity_step BETWEEN 1 AND 99);
