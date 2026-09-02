\set ON_ERROR_STOP on

-- One-time production repair before V40. This script is deliberately guarded
-- against source-catalog drift and is safe only for the audited V39 snapshot.
-- Stop the API before running it. If the legacy API must be restarted before the
-- V40+ deploy, keep DOMEGGOOK_CATALOG_SYNC_ENABLED=false until the new application
-- is healthy so it cannot recreate negative deltas after this transaction.
BEGIN;

SET LOCAL lock_timeout = '15s';
SET LOCAL statement_timeout = '60s';

LOCK TABLE products IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE product_options IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE product_change_histories IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE v40_product_shift ON COMMIT DROP AS
SELECT
    product.id AS product_id,
    product.source_price AS old_source_price,
    min(option.source_additional_price) AS minimum_delta,
    product.source_price::numeric + min(option.source_additional_price)::numeric AS new_source_price,
    product.base_price AS customer_base_price
FROM products product
JOIN product_options option ON option.product_id = product.id
WHERE option.source_additional_price < 0
GROUP BY product.id, product.source_price, product.base_price;

CREATE TEMP TABLE v40_option_shift ON COMMIT DROP AS
SELECT
    option.id AS option_id,
    option.product_id,
    option.source_additional_price AS old_source_additional_price,
    option.source_additional_price::numeric - product.minimum_delta::numeric AS new_source_additional_price,
    product.old_source_price,
    product.new_source_price,
    option.additional_price AS customer_additional_price
FROM product_options option
JOIN v40_product_shift product ON product.product_id = option.product_id
WHERE option.source_additional_price IS NOT NULL;

CREATE TEMP TABLE v40_order_item_snapshot ON COMMIT DROP AS
SELECT
    count(*) AS row_count,
    coalesce(sum(unit_price::numeric), 0) AS unit_price_sum,
    coalesce(sum(line_amount::numeric), 0) AS line_amount_sum,
    coalesce(sum(source_unit_price::numeric), 0) AS source_unit_price_sum
FROM order_items;

DO $$
DECLARE
    current_schema_version text;
BEGIN
    SELECT version INTO current_schema_version
    FROM flyway_schema_history
    WHERE success
    ORDER BY installed_rank DESC
    LIMIT 1;

    IF current_schema_version IS DISTINCT FROM '39' THEN
        RAISE EXCEPTION 'V40 repair requires schema version 39; found %', current_schema_version;
    END IF;

    IF (SELECT count(*) FROM product_options WHERE source_additional_price < 0) <> 34 THEN
        RAISE EXCEPTION 'V40 repair aborted: expected 34 negative source option rows';
    END IF;

    IF (SELECT count(*) FROM v40_product_shift) <> 13 THEN
        RAISE EXCEPTION 'V40 repair aborted: expected 13 affected products';
    END IF;

    IF (SELECT count(*) FROM v40_option_shift) <> 68 THEN
        RAISE EXCEPTION 'V40 repair aborted: expected 68 non-null affected options';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM v40_product_shift
        WHERE new_source_price NOT BETWEEN 0 AND 100000000
    ) THEN
        RAISE EXCEPTION 'V40 repair aborted: normalized product source price is out of range';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM v40_option_shift
        WHERE new_source_additional_price NOT BETWEEN 0 AND 100000000
    ) THEN
        RAISE EXCEPTION 'V40 repair aborted: normalized option source price is out of range';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM v40_option_shift
        WHERE old_source_price::numeric + old_source_additional_price::numeric
            <> new_source_price + new_source_additional_price
    ) THEN
        RAISE EXCEPTION 'V40 repair aborted: option total source price would change';
    END IF;
END $$;

-- V40 converts legacy system histories to a nullable actor model, but its
-- backfill writes NULL before its later DROP NOT NULL statement. Release the
-- legacy constraint here so the already-published V40 checksum stays unchanged.
ALTER TABLE product_change_histories
    ALTER COLUMN admin_user_id DROP NOT NULL;

INSERT INTO product_change_histories (
    id,
    product_id,
    product_option_id,
    admin_user_id,
    change_type,
    before_value,
    after_value,
    reason,
    created_at
)
SELECT
    gen_random_uuid(),
    product_id,
    NULL,
    '00000000-0000-0000-0000-000000000000'::uuid,
    'PRODUCT_BASE',
    format('sourcePrice=%s', old_source_price),
    format('sourcePrice=%s', new_source_price::bigint),
    'V40_PREDEPLOY_SOURCE_OPTION_NORMALIZATION_20260902',
    CURRENT_TIMESTAMP
FROM v40_product_shift;

INSERT INTO product_change_histories (
    id,
    product_id,
    product_option_id,
    admin_user_id,
    change_type,
    before_value,
    after_value,
    reason,
    created_at
)
SELECT
    gen_random_uuid(),
    product_id,
    option_id,
    '00000000-0000-0000-0000-000000000000'::uuid,
    'OPTION_BASE',
    format('sourceAdditionalPrice=%s', old_source_additional_price),
    format('sourceAdditionalPrice=%s', new_source_additional_price::bigint),
    'V40_PREDEPLOY_SOURCE_OPTION_NORMALIZATION_20260902',
    CURRENT_TIMESTAMP
FROM v40_option_shift;

UPDATE products product
SET source_price = shift.new_source_price::bigint
FROM v40_product_shift shift
WHERE product.id = shift.product_id;

UPDATE product_options option
SET source_additional_price = shift.new_source_additional_price::bigint
FROM v40_option_shift shift
WHERE option.id = shift.option_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'product_change_histories'
          AND column_name = 'admin_user_id'
          AND is_nullable <> 'YES'
    ) THEN
        RAISE EXCEPTION 'V40 repair verification failed: admin_user_id is still NOT NULL';
    END IF;

    IF EXISTS (SELECT 1 FROM product_options WHERE source_additional_price < 0) THEN
        RAISE EXCEPTION 'V40 repair verification failed: negative source option rows remain';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM v40_option_shift before_state
        JOIN products product ON product.id = before_state.product_id
        JOIN product_options option ON option.id = before_state.option_id
        WHERE product.source_price::numeric + option.source_additional_price::numeric
            <> before_state.old_source_price::numeric
                + before_state.old_source_additional_price::numeric
            OR product.base_price <> (
                SELECT customer_base_price
                FROM v40_product_shift
                WHERE product_id = before_state.product_id
            )
            OR option.additional_price <> before_state.customer_additional_price
    ) THEN
        RAISE EXCEPTION 'V40 repair verification failed: source total or customer price changed';
    END IF;

    IF (SELECT count(*) FROM product_change_histories
        WHERE reason = 'V40_PREDEPLOY_SOURCE_OPTION_NORMALIZATION_20260902'
          AND created_at = CURRENT_TIMESTAMP) <> 81 THEN
        RAISE EXCEPTION 'V40 repair verification failed: expected 81 audit rows';
    END IF;

    IF (SELECT row(
            count(*),
            coalesce(sum(unit_price::numeric), 0),
            coalesce(sum(line_amount::numeric), 0),
            coalesce(sum(source_unit_price::numeric), 0)
        ) FROM order_items)
        IS DISTINCT FROM
        (SELECT row(row_count, unit_price_sum, line_amount_sum, source_unit_price_sum)
         FROM v40_order_item_snapshot) THEN
        RAISE EXCEPTION 'V40 repair verification failed: order item snapshot changed';
    END IF;
END $$;

COMMIT;

SELECT 'negative_source_options=' || count(*)
FROM product_options
WHERE source_additional_price < 0
UNION ALL
SELECT 'product_audit_rows=' || count(*)
FROM product_change_histories
WHERE reason = 'V40_PREDEPLOY_SOURCE_OPTION_NORMALIZATION_20260902'
  AND change_type = 'PRODUCT_BASE'
UNION ALL
SELECT 'option_audit_rows=' || count(*)
FROM product_change_histories
WHERE reason = 'V40_PREDEPLOY_SOURCE_OPTION_NORMALIZATION_20260902'
  AND change_type = 'OPTION_BASE';
