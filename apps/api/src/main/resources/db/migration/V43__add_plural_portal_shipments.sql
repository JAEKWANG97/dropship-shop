-- B-104 expands the legacy one-shipment model without rewriting shipment evidence.
-- Abort before removing the singular-order constraint if a legacy shipment cannot
-- be deterministically allocated to every item in its order.
DO $$
BEGIN
	IF EXISTS (
		SELECT 1
		FROM shipments shipment
		WHERE NOT EXISTS (
			SELECT 1
			FROM order_items item
			WHERE item.order_id = shipment.order_id
		)
	) THEN
		RAISE EXCEPTION
			'V43 preflight failed: every legacy shipment order must contain at least one order item';
	END IF;

	IF EXISTS (
		SELECT 1
		FROM shipments shipment
		JOIN order_items item ON item.order_id = shipment.order_id
		WHERE item.quantity <= 0
	) THEN
		RAISE EXCEPTION
			'V43 preflight failed: legacy shipment allocations require positive order item quantities';
	END IF;

	IF EXISTS (
		SELECT 1
		FROM shipments
		WHERE status NOT IN ('READY', 'SHIPPED', 'DELIVERED')
	) THEN
		RAISE EXCEPTION
			'V43 preflight failed: unsupported legacy shipment status requires reconciliation';
	END IF;
END $$;

ALTER TABLE shipments
	ADD COLUMN version BIGINT,
	ADD COLUMN idempotency_key VARCHAR(200),
	ADD COLUMN creation_request_hash VARCHAR(128),
	ADD COLUMN creation_result_snapshot JSONB,
	ADD COLUMN carrier_code VARCHAR(40),
	ADD COLUMN registered_at TIMESTAMP WITH TIME ZONE,
	ADD COLUMN registered_by_user_id UUID,
	ADD COLUMN registered_actor_type VARCHAR(20),
	ADD COLUMN delivery_evidence_observed_at TIMESTAMP WITH TIME ZONE;

UPDATE shipments
SET version = 0,
	registered_at = created_at,
	carrier_code = CASE upper(btrim(carrier))
		WHEN 'CJ_LOGISTICS' THEN 'CJ_LOGISTICS'
		WHEN 'CJ대한통운' THEN 'CJ_LOGISTICS'
		WHEN 'LOTTE' THEN 'LOTTE'
		WHEN '롯데택배' THEN 'LOTTE'
		WHEN 'HANJIN' THEN 'HANJIN'
		WHEN '한진택배' THEN 'HANJIN'
		WHEN 'KOREA_POST' THEN 'KOREA_POST'
		WHEN '우체국택배' THEN 'KOREA_POST'
		ELSE NULL
	END;

ALTER TABLE shipments
	ALTER COLUMN version SET DEFAULT 0,
	ALTER COLUMN version SET NOT NULL,
	ALTER COLUMN registered_at SET DEFAULT CURRENT_TIMESTAMP,
	ALTER COLUMN registered_at SET NOT NULL,
	ALTER COLUMN shipped_at DROP NOT NULL,
	ADD CONSTRAINT fk_shipments_registered_by_user
		FOREIGN KEY (registered_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
	ADD CONSTRAINT chk_shipments_version_nonnegative
		CHECK (version >= 0),
	ADD CONSTRAINT chk_shipments_status
		CHECK (status IN ('READY', 'SHIPPED', 'TRACKING_REGISTERED', 'DELIVERED', 'VOIDED')),
	ADD CONSTRAINT chk_shipments_carrier_code
		CHECK (carrier_code IS NULL OR carrier_code IN ('CJ_LOGISTICS', 'LOTTE', 'HANJIN', 'KOREA_POST')),
	ADD CONSTRAINT chk_shipments_registered_actor_type
		CHECK (registered_actor_type IS NULL OR registered_actor_type IN ('ADMIN', 'SUPPLIER')),
	ADD CONSTRAINT chk_shipments_creation_replay
		CHECK (
			(idempotency_key IS NULL
				AND creation_request_hash IS NULL
				AND creation_result_snapshot IS NULL
				AND registered_actor_type IS NULL)
			OR
			(idempotency_key IS NOT NULL
				AND creation_request_hash IS NOT NULL
				AND creation_result_snapshot IS NOT NULL
				AND carrier_code IS NOT NULL
				AND registered_actor_type IS NOT NULL)
		),
	ADD CONSTRAINT chk_shipments_portal_delivery_evidence
		CHECK (
			delivery_evidence_observed_at IS NULL
			OR (delivered_at IS NOT NULL
				AND registered_at <= delivered_at
				AND delivered_at <= delivery_evidence_observed_at)
		);

CREATE UNIQUE INDEX uk_shipments_order_creation_key
	ON shipments(order_id, idempotency_key)
	WHERE idempotency_key IS NOT NULL;

CREATE TABLE shipment_items (
	id UUID PRIMARY KEY,
	shipment_id UUID NOT NULL REFERENCES shipments(id),
	order_item_id UUID NOT NULL REFERENCES order_items(id),
	quantity INTEGER NOT NULL,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL,
	CONSTRAINT uk_shipment_items_shipment_order_item
		UNIQUE (shipment_id, order_item_id),
	CONSTRAINT chk_shipment_items_quantity_positive
		CHECK (quantity > 0)
);

CREATE INDEX idx_shipment_items_order_item_id
	ON shipment_items(order_item_id);

-- Two independent foreign keys do not prove that the Shipment and OrderItem
-- belong to the same Order. Keep the invariant at the database boundary too,
-- so direct SQL and future writers cannot create a cross-order allocation.
CREATE FUNCTION enforce_shipment_item_order_match()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
	shipment_order_id UUID;
	item_order_id UUID;
BEGIN
	SELECT shipment.order_id
	INTO shipment_order_id
	FROM shipments shipment
	WHERE shipment.id = NEW.shipment_id;

	SELECT item.order_id
	INTO item_order_id
	FROM order_items item
	WHERE item.id = NEW.order_item_id;

	IF shipment_order_id IS DISTINCT FROM item_order_id THEN
		RAISE EXCEPTION
			'shipment_items order mismatch: shipment % and order item % belong to different orders',
			NEW.shipment_id,
			NEW.order_item_id
			USING ERRCODE = '23514';
	END IF;

	RETURN NEW;
END $$;

CREATE CONSTRAINT TRIGGER trg_shipment_items_order_match
	AFTER INSERT OR UPDATE ON shipment_items
	DEFERRABLE INITIALLY IMMEDIATE
	FOR EACH ROW
	EXECUTE FUNCTION enforce_shipment_item_order_match();

-- The allocation-row trigger alone cannot protect the invariant if a future
-- writer reassigns an already allocated Shipment or OrderItem to another Order.
CREATE FUNCTION enforce_shipment_order_update_match()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
	IF EXISTS (
		SELECT 1
		FROM shipment_items allocation
		JOIN order_items item ON item.id = allocation.order_item_id
		WHERE allocation.shipment_id = NEW.id
			AND item.order_id IS DISTINCT FROM NEW.order_id
	) THEN
		RAISE EXCEPTION
			'shipment_items order mismatch after shipment % order update',
			NEW.id
			USING ERRCODE = '23514';
	END IF;

	RETURN NEW;
END $$;

CREATE TRIGGER trg_shipments_order_update_match
	AFTER UPDATE OF order_id ON shipments
	FOR EACH ROW
	EXECUTE FUNCTION enforce_shipment_order_update_match();

CREATE FUNCTION enforce_order_item_order_update_match()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
	IF EXISTS (
		SELECT 1
		FROM shipment_items allocation
		JOIN shipments shipment ON shipment.id = allocation.shipment_id
		WHERE allocation.order_item_id = NEW.id
			AND shipment.order_id IS DISTINCT FROM NEW.order_id
	) THEN
		RAISE EXCEPTION
			'shipment_items order mismatch after order item % order update',
			NEW.id
			USING ERRCODE = '23514';
	END IF;

	RETURN NEW;
END $$;

CREATE TRIGGER trg_order_items_order_update_match
	AFTER UPDATE OF order_id ON order_items
	FOR EACH ROW
	EXECUTE FUNCTION enforce_order_item_order_update_match();

INSERT INTO shipment_items(id, shipment_id, order_item_id, quantity, created_at)
SELECT gen_random_uuid(), shipment.id, item.id, item.quantity, shipment.created_at
FROM shipments shipment
JOIN order_items item ON item.order_id = shipment.order_id;

DO $$
BEGIN
	IF EXISTS (
		SELECT 1
		FROM shipments shipment
		JOIN order_items item ON item.order_id = shipment.order_id
		LEFT JOIN shipment_items allocation
			ON allocation.shipment_id = shipment.id
			AND allocation.order_item_id = item.id
		WHERE allocation.id IS NULL
	) THEN
		RAISE EXCEPTION
			'V43 backfill failed: legacy shipment allocation is incomplete';
	END IF;
END $$;

CREATE TABLE shipment_change_histories (
	id UUID PRIMARY KEY,
	shipment_id UUID NOT NULL REFERENCES shipments(id),
	actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
	actor_type VARCHAR(20) NOT NULL,
	action VARCHAR(50) NOT NULL,
	before_snapshot JSONB NOT NULL,
	after_snapshot JSONB NOT NULL,
	reason VARCHAR(200) NOT NULL,
	evidence_observed_at TIMESTAMP WITH TIME ZONE,
	request_hash VARCHAR(128) NOT NULL,
	idempotency_key VARCHAR(200) NOT NULL,
	result_snapshot JSONB NOT NULL,
	created_at TIMESTAMP WITH TIME ZONE NOT NULL,
	CONSTRAINT uk_shipment_change_histories_shipment_key
		UNIQUE (shipment_id, idempotency_key),
	CONSTRAINT chk_shipment_change_histories_actor_type
		CHECK (actor_type IN ('ADMIN', 'SUPPLIER')),
	CONSTRAINT chk_shipment_change_histories_action
		CHECK (action IN (
			'SUPPLIER_CORRECTED',
			'ADMIN_CORRECTED',
			'ADMIN_VOIDED',
			'ADMIN_DELIVERY_COMPLETED',
			'ADMIN_DELIVERY_REOPENED',
			'ADMIN_DELIVERED_AT_CORRECTED'
		)),
	CONSTRAINT chk_shipment_change_histories_actor_action
		CHECK (
			(actor_type = 'SUPPLIER' AND action = 'SUPPLIER_CORRECTED')
			OR (actor_type = 'ADMIN' AND action <> 'SUPPLIER_CORRECTED')
		),
	CONSTRAINT chk_shipment_change_histories_evidence
		CHECK (
			(action IN ('ADMIN_DELIVERY_COMPLETED', 'ADMIN_DELIVERED_AT_CORRECTED')
				AND evidence_observed_at IS NOT NULL)
			OR (action NOT IN ('ADMIN_DELIVERY_COMPLETED', 'ADMIN_DELIVERED_AT_CORRECTED')
				AND evidence_observed_at IS NULL)
		)
);

CREATE INDEX idx_shipment_change_histories_shipment_created
	ON shipment_change_histories(shipment_id, created_at, id);

CREATE INDEX idx_shipments_registered_by_user_id
	ON shipments(registered_by_user_id);

CREATE INDEX idx_shipment_change_histories_actor_user_id
	ON shipment_change_histories(actor_user_id);

-- The compatibility readers/writers have plural allocation support before this
-- final contract step. Keep the existing non-unique order index, while allowing
-- at most one pre-B-104 writer row per Order during a rolling deployment.
ALTER TABLE shipments
	DROP CONSTRAINT uk_shipments_order_id;

CREATE UNIQUE INDEX uk_shipments_order_legacy
	ON shipments(order_id)
	WHERE idempotency_key IS NULL;

-- A V42 binary can still insert its old Shipment column shape while old and new
-- application tasks overlap. At commit, give that legacy row the deterministic
-- whole-order allocation used by the V43 backfill. New V43 writers add their own
-- immutable allocations in the same transaction, so this trigger becomes a no-op
-- for them. The production portal feature flag remains off until all old writers
-- have drained; only then may portal rows make the Order genuinely plural.
CREATE FUNCTION ensure_legacy_shipment_allocations()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
	inserted_count INTEGER;
BEGIN
	IF NEW.registered_actor_type IS NULL
		AND NOT EXISTS (
			SELECT 1
			FROM shipment_items allocation
			WHERE allocation.shipment_id = NEW.id
		)
	THEN
		INSERT INTO shipment_items(id, shipment_id, order_item_id, quantity, created_at)
		SELECT gen_random_uuid(), NEW.id, item.id, item.quantity, NEW.created_at
		FROM order_items item
		WHERE item.order_id = NEW.order_id;

		GET DIAGNOSTICS inserted_count = ROW_COUNT;
		IF inserted_count = 0 THEN
			RAISE EXCEPTION
				'legacy shipment % cannot be allocated because order % has no items',
				NEW.id,
				NEW.order_id
				USING ERRCODE = '23514';
		END IF;
	END IF;

	RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER trg_shipments_legacy_allocations
	AFTER INSERT ON shipments
	DEFERRABLE INITIALLY DEFERRED
	FOR EACH ROW
	EXECUTE FUNCTION ensure_legacy_shipment_allocations();
