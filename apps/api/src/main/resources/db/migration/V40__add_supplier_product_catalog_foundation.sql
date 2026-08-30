-- Validate monetary compatibility before adding the B-101 catalog contract.
-- These constraints are intentionally validated immediately: incompatible historical
-- amounts abort the migration and require an explicit data correction first.
DO $$
BEGIN
	IF EXISTS (
		SELECT 1
		FROM product_options options
		JOIN products ON products.id = options.product_id
		WHERE products.base_price::numeric + options.additional_price::numeric > 1000000000
	) THEN
		RAISE EXCEPTION
			'V40 preflight failed: legacy customer unit price exceeds 1000000000 KRW';
	END IF;
END $$;

ALTER TABLE products
	ADD CONSTRAINT chk_products_source_price_range
		CHECK (source_price BETWEEN 0 AND 100000000),
	ADD CONSTRAINT chk_products_base_price_range
		CHECK (base_price BETWEEN 0 AND 1000000000);

ALTER TABLE product_options
	ADD CONSTRAINT chk_product_options_source_additional_price_range
		CHECK (source_additional_price IS NULL OR source_additional_price BETWEEN 0 AND 100000000),
	ADD CONSTRAINT chk_product_options_additional_price_range
		CHECK (additional_price BETWEEN 0 AND 1000000000);

ALTER TABLE payment_groups
	ADD CONSTRAINT chk_payment_groups_total_amount_positive CHECK (total_amount > 0),
	ADD CONSTRAINT chk_payment_groups_approved_amount_positive
		CHECK (approved_amount IS NULL OR approved_amount > 0),
	ADD CONSTRAINT chk_payment_groups_refundable_amount_nonnegative CHECK (refundable_amount >= 0);

ALTER TABLE orders
	ADD CONSTRAINT chk_orders_subtotal_amount_positive CHECK (subtotal_amount > 0),
	ADD CONSTRAINT chk_orders_shipping_fee_nonnegative CHECK (shipping_fee >= 0),
	ADD CONSTRAINT chk_orders_discount_amount_nonnegative CHECK (discount_amount >= 0),
	ADD CONSTRAINT chk_orders_total_amount_positive CHECK (total_amount > 0);

ALTER TABLE order_items
	ADD CONSTRAINT chk_order_items_unit_price_range CHECK (unit_price BETWEEN 1 AND 1000000000),
	ADD CONSTRAINT chk_order_items_quantity_positive CHECK (quantity > 0),
	ADD CONSTRAINT chk_order_items_line_amount_positive CHECK (line_amount > 0),
	ADD CONSTRAINT chk_order_items_line_amount_snapshot
		CHECK (line_amount::numeric = unit_price::numeric * quantity),
	ADD CONSTRAINT chk_order_items_source_unit_price_range
		CHECK (source_unit_price IS NULL OR source_unit_price BETWEEN 0 AND 200000000);

ALTER TABLE pricing_policies
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_pricing_policies_version_positive CHECK (version >= 1);

ALTER TABLE products
	    ADD COLUMN management_channel VARCHAR(30) NOT NULL DEFAULT 'COREABLE',
	    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
	    ADD COLUMN source_auto_sold_out BOOLEAN NOT NULL DEFAULT FALSE,
	    ADD COLUMN first_submitted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN pricing_policy_id_applied UUID REFERENCES pricing_policies(id),
    ADD COLUMN pricing_policy_version_applied BIGINT,
    ADD COLUMN review_status VARCHAR(30),
    ADD COLUMN review_reason_code VARCHAR(50),
    ADD COLUMN supplier_review_message VARCHAR(500),
    ADD CONSTRAINT chk_products_management_channel
        CHECK (management_channel IN ('COREABLE', 'SUPPLIER_PORTAL')),
    ADD CONSTRAINT chk_products_aggregate_version_nonnegative
        CHECK (version >= 0),
    ADD CONSTRAINT chk_products_pricing_policy_snapshot
        CHECK (
            (pricing_policy_id_applied IS NULL AND pricing_policy_version_applied IS NULL)
            OR (pricing_policy_id_applied IS NOT NULL AND pricing_policy_version_applied >= 1)
        ),
    ADD CONSTRAINT chk_products_review_status
        CHECK (
            review_status IS NULL OR review_status IN (
                'DRAFT',
                'AUTO_APPROVED',
                'REVIEW_REQUIRED',
                'SUPPLEMENT_REQUESTED',
                'APPROVED',
                'REJECTED'
            )
        ),
    ADD CONSTRAINT chk_products_review_reason_code
        CHECK (
            review_reason_code IS NULL OR review_reason_code IN (
                'CERTIFICATION_REVIEW',
                'CATEGORY_REVIEW',
                'REQUIRED_INFO_MISSING',
                'SAFETY_REVIEW',
                'SUPPLEMENT_REQUIRED',
                'REJECTED_POLICY'
            )
        ),
    ADD CONSTRAINT chk_products_review_reason_required
        CHECK (
            review_status NOT IN ('REVIEW_REQUIRED', 'SUPPLEMENT_REQUESTED', 'REJECTED')
            OR review_reason_code IS NOT NULL
        ),
    ADD CONSTRAINT chk_products_review_message_required
        CHECK (
            review_status NOT IN ('SUPPLEMENT_REQUESTED', 'REJECTED')
            OR (supplier_review_message IS NOT NULL AND btrim(supplier_review_message) <> '')
        ),
    ADD CONSTRAINT chk_products_review_message_single_line
        CHECK (
            supplier_review_message IS NULL
            OR (position(chr(10) IN supplier_review_message) = 0
                AND position(chr(13) IN supplier_review_message) = 0)
        ),
    ADD CONSTRAINT chk_products_portal_review_status
        CHECK (management_channel = 'COREABLE' OR review_status IS NOT NULL);

CREATE INDEX idx_products_management_channel ON products(management_channel);
CREATE INDEX idx_products_supplier_portal_list
    ON products(supplier_id, updated_at DESC, id)
    WHERE management_channel = 'SUPPLIER_PORTAL';
CREATE INDEX idx_products_review_queue
    ON products(review_status, updated_at DESC, id)
    WHERE review_status IS NOT NULL;

ALTER TABLE product_images
    ADD COLUMN storage_object_key VARCHAR(1000),
    ADD CONSTRAINT chk_product_images_type
        CHECK (type IN ('THUMBNAIL', 'GALLERY', 'DETAIL'));

CREATE UNIQUE INDEX uk_product_images_storage_object_key
    ON product_images(storage_object_key)
    WHERE storage_object_key IS NOT NULL;

ALTER TABLE product_detail_blocks
    ADD COLUMN product_image_id UUID REFERENCES product_images(id);

CREATE INDEX idx_product_detail_blocks_product_image_id
    ON product_detail_blocks(product_image_id)
    WHERE product_image_id IS NOT NULL;

ALTER TABLE product_change_histories
    ADD COLUMN subject_product_id UUID,
    ADD COLUMN subject_product_option_id UUID,
    ADD COLUMN actor_user_id UUID,
    ADD COLUMN actor_type VARCHAR(20),
    ADD COLUMN actor_supplier_id UUID,
    ADD COLUMN actor_system_code VARCHAR(100),
    ADD COLUMN before_version BIGINT,
    ADD COLUMN after_version BIGINT;

UPDATE product_change_histories
SET subject_product_id = product_id,
    subject_product_option_id = product_option_id;

UPDATE product_change_histories history
SET actor_type = CASE
        WHEN history.admin_user_id = '00000000-0000-0000-0000-000000000000'::UUID THEN 'SYSTEM'
        WHEN EXISTS (SELECT 1 FROM users WHERE users.id = history.admin_user_id) THEN 'ADMIN'
        ELSE 'SYSTEM'
    END,
    actor_user_id = CASE
        WHEN EXISTS (SELECT 1 FROM users WHERE users.id = history.admin_user_id)
            THEN history.admin_user_id
        ELSE NULL
    END,
    actor_system_code = CASE
        WHEN history.admin_user_id = '00000000-0000-0000-0000-000000000000'::UUID
            THEN 'DOMEGGOOK_CATALOG_SYNC'
        WHEN NOT EXISTS (SELECT 1 FROM users WHERE users.id = history.admin_user_id)
            THEN 'LEGACY_CATALOG_WRITER'
        ELSE NULL
    END;

UPDATE product_change_histories
SET admin_user_id = NULL
WHERE admin_user_id = '00000000-0000-0000-0000-000000000000'::UUID;

ALTER TABLE product_change_histories
    ALTER COLUMN subject_product_id SET NOT NULL,
    ALTER COLUMN actor_type SET NOT NULL,
    ALTER COLUMN admin_user_id DROP NOT NULL,
    ALTER COLUMN product_id DROP NOT NULL,
    ADD CONSTRAINT chk_product_change_histories_actor_type
        CHECK (actor_type IN ('ADMIN', 'SUPPLIER', 'SYSTEM')),
    ADD CONSTRAINT chk_product_change_histories_actor_shape
        CHECK (
            (actor_type = 'ADMIN' AND actor_supplier_id IS NULL AND actor_system_code IS NULL)
            OR (actor_type = 'SUPPLIER' AND actor_supplier_id IS NOT NULL AND actor_system_code IS NULL)
            OR (actor_type = 'SYSTEM' AND actor_user_id IS NULL AND actor_supplier_id IS NULL
                AND actor_system_code IS NOT NULL)
        ),
    ADD CONSTRAINT chk_product_change_histories_versions
        CHECK (
            (before_version IS NULL OR before_version >= 0)
            AND (after_version IS NULL OR after_version >= 0)
        ),
    ADD CONSTRAINT fk_product_change_histories_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE product_change_histories
    DROP CONSTRAINT product_change_histories_product_id_fkey,
    DROP CONSTRAINT product_change_histories_product_option_id_fkey;

ALTER TABLE product_change_histories
    ADD CONSTRAINT product_change_histories_product_id_fkey
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE SET NULL,
    ADD CONSTRAINT product_change_histories_product_option_id_fkey
        FOREIGN KEY (product_option_id) REFERENCES product_options(id) ON DELETE SET NULL;

CREATE INDEX idx_product_change_histories_subject_product_id
    ON product_change_histories(subject_product_id, created_at, id);
CREATE INDEX idx_product_change_histories_subject_option_id
    ON product_change_histories(subject_product_option_id, created_at, id)
    WHERE subject_product_option_id IS NOT NULL;

CREATE TABLE product_image_cleanup_jobs (
    id UUID PRIMARY KEY,
    storage_object_key VARCHAR(1000) NOT NULL,
    subject_product_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error_code VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_product_image_cleanup_jobs_storage_object_key UNIQUE (storage_object_key),
    CONSTRAINT chk_product_image_cleanup_jobs_status CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT chk_product_image_cleanup_jobs_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_product_image_cleanup_jobs_completion CHECK (
        (status = 'PENDING' AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_product_image_cleanup_jobs_due
    ON product_image_cleanup_jobs(next_attempt_at, created_at, id)
    WHERE status = 'PENDING';
