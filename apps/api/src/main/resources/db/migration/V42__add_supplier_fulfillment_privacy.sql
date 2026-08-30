ALTER TABLE orders
    ADD COLUMN delivery_memo VARCHAR(300);

CREATE TABLE supplier_pii_access_grants (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    sequence INTEGER NOT NULL,
    action VARCHAR(20) NOT NULL,
    access_until TIMESTAMP WITH TIME ZONE,
    previous_grant_id UUID,
    acted_by_admin_id UUID NOT NULL,
    reason VARCHAR(200) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    result_snapshot JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_supplier_pii_access_grants_claim
        FOREIGN KEY (claim_id) REFERENCES claims(id),
    CONSTRAINT fk_supplier_pii_access_grants_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_supplier_pii_access_grants_previous
        FOREIGN KEY (previous_grant_id) REFERENCES supplier_pii_access_grants(id),
    CONSTRAINT fk_supplier_pii_access_grants_admin
        FOREIGN KEY (acted_by_admin_id) REFERENCES users(id),
    CONSTRAINT ck_supplier_pii_access_grants_sequence
        CHECK (sequence > 0),
    CONSTRAINT ck_supplier_pii_access_grants_action
        CHECK (action IN ('GRANTED', 'EXTENDED', 'REVOKED')),
    CONSTRAINT ck_supplier_pii_access_grants_deadline
        CHECK (
            (action IN ('GRANTED', 'EXTENDED') AND access_until IS NOT NULL)
            OR (action = 'REVOKED' AND access_until IS NULL)
        ),
    CONSTRAINT ck_supplier_pii_access_grants_previous
        CHECK (
            (sequence = 1 AND previous_grant_id IS NULL)
            OR (sequence > 1 AND previous_grant_id IS NOT NULL)
        ),
    CONSTRAINT ck_supplier_pii_access_grants_reason
        CHECK (
            (action IN ('GRANTED', 'EXTENDED') AND reason IN (
                'RETURN_COORDINATION_REQUIRED',
                'EXCHANGE_COORDINATION_REQUIRED',
                'REFUND_COORDINATION_REQUIRED'
            ))
            OR (action = 'REVOKED' AND reason = 'CLAIM_ACCESS_NO_LONGER_REQUIRED')
        ),
    CONSTRAINT uk_supplier_pii_access_grants_claim_sequence
        UNIQUE (claim_id, sequence),
    CONSTRAINT uk_supplier_pii_access_grants_claim_key
        UNIQUE (claim_id, idempotency_key)
);

CREATE INDEX idx_supplier_pii_access_grants_claim_sequence
    ON supplier_pii_access_grants(claim_id, sequence DESC);
CREATE INDEX idx_supplier_pii_access_grants_supplier
    ON supplier_pii_access_grants(supplier_id, created_at DESC);

CREATE TABLE supplier_pii_access_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL,
    order_id UUID NOT NULL,
    access_reason VARCHAR(30) NOT NULL,
    accessed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_supplier_pii_access_logs_actor_user
        FOREIGN KEY (actor_user_id) REFERENCES users(id),
    CONSTRAINT fk_supplier_pii_access_logs_order
        FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT ck_supplier_pii_access_logs_reason
        CHECK (access_reason IN ('NORMAL_FULL', 'CLAIM_FULL', 'TERMINAL_MASKED', 'EXPIRED_MASKED'))
);

CREATE INDEX idx_supplier_pii_access_logs_actor_time
    ON supplier_pii_access_logs(actor_user_id, accessed_at);
CREATE INDEX idx_supplier_pii_access_logs_order_time
    ON supplier_pii_access_logs(order_id, accessed_at);

CREATE INDEX idx_notification_logs_supplier_operational_retention
    ON notification_logs(recipient_retention_expires_at, id)
    WHERE supplier_id IS NOT NULL
      AND supplier_invite_id IS NULL
      AND recipient_anonymized_at IS NULL;
