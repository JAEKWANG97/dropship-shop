UPDATE products duplicate_product
SET source_item_no = NULL,
    status = 'HIDDEN'
WHERE duplicate_product.source_item_no IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM products keeper
      WHERE keeper.source_item_no = duplicate_product.source_item_no
        AND (
            keeper.created_at < duplicate_product.created_at
            OR (
                keeper.created_at = duplicate_product.created_at
                AND CAST(keeper.id AS VARCHAR) < CAST(duplicate_product.id AS VARCHAR)
            )
        )
  );

DROP INDEX idx_products_source_item_no;

CREATE UNIQUE INDEX uk_products_source_item_no
    ON products(source_item_no)
    WHERE source_item_no IS NOT NULL;
