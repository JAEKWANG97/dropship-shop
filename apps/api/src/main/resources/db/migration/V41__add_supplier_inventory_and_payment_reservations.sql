-- B-102 is an expand-contract migration. Abort rather than inventing reservation
-- evidence for portal-origin orders that predate the reservation ledger.
DO $$
BEGIN
	IF EXISTS (
		SELECT 1
		FROM order_items item
		JOIN products product ON product.id = item.product_id
		WHERE product.management_channel = 'SUPPLIER_PORTAL'
	) THEN
		RAISE EXCEPTION
			'V41 preflight failed: portal-origin order items require explicit reservation reconciliation';
	END IF;

	IF EXISTS (
		SELECT 1
		FROM refunds refund
		LEFT JOIN orders customer_order ON customer_order.id = refund.order_id
		LEFT JOIN payments payment ON payment.id = refund.payment_id
		WHERE refund.refund_amount <= 0
			OR refund.order_id IS NULL
			OR refund.refund_scope = 'PAYMENT_GROUP'
			OR customer_order.id IS NULL
			OR customer_order.payment_group_id <> refund.payment_group_id
			OR (payment.id IS NOT NULL AND payment.payment_group_id <> refund.payment_group_id)
	) THEN
		RAISE EXCEPTION
			'V41 preflight failed: existing refund scope, amount, or aggregate linkage is incompatible';
	END IF;

	IF EXISTS (
		SELECT order_id
		FROM refunds
		GROUP BY order_id
		HAVING count(*) > 1
	) THEN
		RAISE EXCEPTION
			'V41 preflight failed: duplicate order-scoped refunds require reconciliation';
	END IF;
END $$;

ALTER TABLE product_options
	ADD COLUMN supplier_availability VARCHAR(20) DEFAULT 'AVAILABLE',
	ADD COLUMN inventory_mode VARCHAR(20) DEFAULT 'UNTRACKED',
	ADD COLUMN on_hand_quantity BIGINT,
	ADD COLUMN reserved_quantity BIGINT NOT NULL DEFAULT 0,
	ADD COLUMN inventory_version BIGINT NOT NULL DEFAULT 0;

UPDATE product_options option
SET supplier_availability = 'AVAILABLE',
	inventory_mode = CASE
		WHEN product.management_channel = 'SUPPLIER_PORTAL' THEN 'TRACKED'
		ELSE 'UNTRACKED'
	END,
	on_hand_quantity = CASE
		WHEN product.management_channel = 'SUPPLIER_PORTAL' THEN 0
		ELSE NULL
	END
FROM products product
WHERE product.id = option.product_id;

ALTER TABLE product_options
	ALTER COLUMN supplier_availability SET NOT NULL,
	ALTER COLUMN inventory_mode SET NOT NULL,
	ADD CONSTRAINT chk_product_options_supplier_availability
		CHECK (supplier_availability IN ('AVAILABLE', 'UNAVAILABLE')),
	ADD CONSTRAINT chk_product_options_inventory_mode
		CHECK (inventory_mode IN ('TRACKED', 'UNTRACKED')),
	ADD CONSTRAINT chk_product_options_inventory_version_nonnegative
		CHECK (inventory_version >= 0),
	ADD CONSTRAINT chk_product_options_inventory_projection
		CHECK (
			(inventory_mode = 'TRACKED'
				AND on_hand_quantity IS NOT NULL
				AND on_hand_quantity >= 0
				AND reserved_quantity >= 0
				AND reserved_quantity <= on_hand_quantity)
			OR
			(inventory_mode = 'UNTRACKED'
				AND on_hand_quantity IS NULL
				AND reserved_quantity = 0)
		);

ALTER TABLE order_items
	ADD COLUMN management_channel_snapshot VARCHAR(30) DEFAULT 'COREABLE',
	ADD COLUMN inventory_mode_snapshot VARCHAR(20) DEFAULT 'UNTRACKED',
	ADD COLUMN reservation_status VARCHAR(30) DEFAULT 'NOT_APPLICABLE',
	ADD COLUMN reserved_at TIMESTAMP WITH TIME ZONE,
	ADD COLUMN consumed_at TIMESTAMP WITH TIME ZONE,
	ADD COLUMN released_at TIMESTAMP WITH TIME ZONE,
	ADD COLUMN reacquired_at TIMESTAMP WITH TIME ZONE;

UPDATE order_items
SET management_channel_snapshot = 'COREABLE',
	inventory_mode_snapshot = 'UNTRACKED',
	reservation_status = 'NOT_APPLICABLE';

ALTER TABLE order_items
	ALTER COLUMN management_channel_snapshot SET NOT NULL,
	ALTER COLUMN inventory_mode_snapshot SET NOT NULL,
	ALTER COLUMN reservation_status SET NOT NULL,
	ADD CONSTRAINT chk_order_items_management_channel_snapshot
		CHECK (management_channel_snapshot IN ('COREABLE', 'SUPPLIER_PORTAL')),
	ADD CONSTRAINT chk_order_items_inventory_mode_snapshot
		CHECK (inventory_mode_snapshot IN ('TRACKED', 'UNTRACKED')),
	ADD CONSTRAINT chk_order_items_reservation_status
		CHECK (reservation_status IN ('NOT_APPLICABLE', 'HELD', 'CONSUMED', 'RELEASED')),
	ADD CONSTRAINT chk_order_items_reservation_evidence
		CHECK (
			(reservation_status = 'NOT_APPLICABLE'
				AND inventory_mode_snapshot = 'UNTRACKED'
				AND reserved_at IS NULL
				AND consumed_at IS NULL
				AND released_at IS NULL
				AND reacquired_at IS NULL)
			OR
			(reservation_status = 'HELD'
				AND inventory_mode_snapshot = 'TRACKED'
				AND reserved_at IS NOT NULL
				AND consumed_at IS NULL
				AND released_at IS NULL
				AND reacquired_at IS NULL)
			OR
			(reservation_status = 'RELEASED'
				AND inventory_mode_snapshot = 'TRACKED'
				AND reserved_at IS NOT NULL
				AND consumed_at IS NULL
				AND released_at IS NOT NULL
				AND reacquired_at IS NULL)
			OR
			(reservation_status = 'CONSUMED'
				AND inventory_mode_snapshot = 'TRACKED'
				AND reserved_at IS NOT NULL
				AND consumed_at IS NOT NULL
				AND ((released_at IS NULL AND reacquired_at IS NULL)
					OR (released_at IS NOT NULL AND reacquired_at IS NOT NULL)))
		);

CREATE TABLE supplier_inventory_change_histories (
	id UUID PRIMARY KEY,
	product_option_id UUID REFERENCES product_options(id) ON DELETE SET NULL,
	subject_product_option_id UUID NOT NULL,
	supplier_id UUID NOT NULL REFERENCES suppliers(id),
	actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
	before_supplier_availability VARCHAR(20) NOT NULL,
	after_supplier_availability VARCHAR(20) NOT NULL,
	before_inventory_mode VARCHAR(20) NOT NULL,
	after_inventory_mode VARCHAR(20) NOT NULL,
	before_on_hand_quantity BIGINT,
	after_on_hand_quantity BIGINT,
	before_reserved_quantity BIGINT NOT NULL,
	after_reserved_quantity BIGINT NOT NULL,
	before_inventory_version BIGINT NOT NULL,
	after_inventory_version BIGINT NOT NULL,
	request_hash VARCHAR(128) NOT NULL,
	idempotency_key VARCHAR(200) NOT NULL,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL,
	CONSTRAINT uk_supplier_inventory_history_subject_key
		UNIQUE (subject_product_option_id, idempotency_key),
	CONSTRAINT chk_supplier_inventory_history_availability
		CHECK (
			before_supplier_availability IN ('AVAILABLE', 'UNAVAILABLE')
			AND after_supplier_availability IN ('AVAILABLE', 'UNAVAILABLE')
		),
	CONSTRAINT chk_supplier_inventory_history_mode
		CHECK (
			before_inventory_mode IN ('TRACKED', 'UNTRACKED')
			AND after_inventory_mode IN ('TRACKED', 'UNTRACKED')
		),
	CONSTRAINT chk_supplier_inventory_history_reserved_nonnegative
		CHECK (before_reserved_quantity >= 0 AND after_reserved_quantity >= 0),
	CONSTRAINT chk_supplier_inventory_history_version_nonnegative
		CHECK (before_inventory_version >= 0 AND after_inventory_version >= 0)
);

CREATE INDEX idx_supplier_inventory_history_supplier_subject
	ON supplier_inventory_change_histories(supplier_id, subject_product_option_id, created_at DESC);
CREATE INDEX idx_supplier_inventory_history_live_option
	ON supplier_inventory_change_histories(product_option_id)
	WHERE product_option_id IS NOT NULL;

ALTER TABLE payment_events
	ADD COLUMN command_type VARCHAR(60),
	ADD COLUMN request_hash VARCHAR(128),
	ADD COLUMN result_snapshot JSONB,
	ADD CONSTRAINT chk_payment_events_command_replay_fields
		CHECK (
			(command_type IS NULL AND request_hash IS NULL AND result_snapshot IS NULL)
			OR
			(command_type IS NOT NULL
				AND idempotency_key IS NOT NULL
				AND request_hash IS NOT NULL
				AND result_snapshot IS NOT NULL)
		);

CREATE UNIQUE INDEX uk_payment_events_group_idempotency_command
	ON payment_events(payment_group_id, idempotency_key)
	WHERE command_type IS NOT NULL AND idempotency_key IS NOT NULL;

CREATE INDEX idx_payment_groups_pending_expiry
	ON payment_groups(expires_at, id)
	WHERE status = 'PAYMENT_PENDING';

ALTER TABLE refunds
	ALTER COLUMN order_id DROP NOT NULL,
	ADD CONSTRAINT chk_refunds_amount_positive CHECK (refund_amount > 0),
	ADD CONSTRAINT chk_refunds_scope_subject
		CHECK (
			(refund_scope = 'DELIVERY_GROUP_ORDER' AND order_id IS NOT NULL)
			OR
			(refund_scope = 'PAYMENT_GROUP'
				AND order_id IS NULL
				AND reason = 'PAYMENT_AMOUNT_MISMATCH')
		);

ALTER TABLE payments
	ADD CONSTRAINT uk_payments_id_payment_group UNIQUE (id, payment_group_id);

ALTER TABLE orders
	ADD CONSTRAINT uk_orders_id_payment_group UNIQUE (id, payment_group_id);

ALTER TABLE refunds
	ADD CONSTRAINT fk_refunds_payment_same_group
		FOREIGN KEY (payment_id, payment_group_id)
		REFERENCES payments(id, payment_group_id),
	ADD CONSTRAINT fk_refunds_order_same_group
		FOREIGN KEY (order_id, payment_group_id)
		REFERENCES orders(id, payment_group_id);

CREATE UNIQUE INDEX uk_refunds_payment_group_scope
	ON refunds(payment_group_id)
	WHERE refund_scope = 'PAYMENT_GROUP';
