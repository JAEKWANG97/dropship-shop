ALTER TABLE suppliers
    ADD COLUMN manager_user_id UUID REFERENCES users(id),
    ADD COLUMN portal_enrolled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN portal_status VARCHAR(30),
    ADD COLUMN contact_email_verified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN portal_contract_status VARCHAR(30),
    ADD COLUMN portal_contract_version VARCHAR(100),
    ADD COLUMN portal_contract_effective_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN portal_contract_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN portal_contract_verified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN portal_contract_verified_by_admin_id UUID REFERENCES users(id),
    ADD COLUMN contact_retention_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN contact_anonymized_at TIMESTAMP WITH TIME ZONE;

UPDATE suppliers
SET portal_status = 'DISABLED',
    portal_contract_status = 'UNVERIFIED';

ALTER TABLE suppliers
    ALTER COLUMN portal_status SET NOT NULL,
    ALTER COLUMN portal_status SET DEFAULT 'DISABLED',
    ALTER COLUMN portal_contract_status SET NOT NULL,
    ALTER COLUMN portal_contract_status SET DEFAULT 'UNVERIFIED',
    ADD CONSTRAINT ck_suppliers_portal_status
        CHECK (portal_status IN ('DISABLED', 'PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED')),
    ADD CONSTRAINT ck_suppliers_portal_contract_status
        CHECK (portal_contract_status IN ('UNVERIFIED', 'VERIFIED', 'EXPIRED', 'REVOKED')),
    ADD CONSTRAINT ck_suppliers_portal_manager_state
        CHECK (
            (portal_status IN ('ACTIVE', 'SUSPENDED') AND manager_user_id IS NOT NULL)
            OR (portal_status IN ('DISABLED', 'PENDING_ACTIVATION') AND manager_user_id IS NULL)
        ),
    ADD CONSTRAINT ck_suppliers_portal_enrollment
        CHECK (
            portal_enrolled_at IS NOT NULL
            OR (portal_status = 'DISABLED' AND manager_user_id IS NULL)
        ),
    ADD CONSTRAINT ck_suppliers_portal_contract_times
        CHECK (
            portal_contract_expires_at IS NULL
            OR portal_contract_effective_at IS NULL
            OR portal_contract_expires_at > portal_contract_effective_at
        );

CREATE UNIQUE INDEX uk_suppliers_manager_user_id
    ON suppliers(manager_user_id)
    WHERE manager_user_id IS NOT NULL;
CREATE UNIQUE INDEX uk_suppliers_normalized_contact_email
    ON suppliers(lower(btrim(email)))
    WHERE email IS NOT NULL
      AND portal_enrolled_at IS NOT NULL
      AND contact_anonymized_at IS NULL;

CREATE INDEX idx_suppliers_portal_status ON suppliers(portal_status);
CREATE INDEX idx_suppliers_contact_retention
    ON suppliers(contact_retention_expires_at)
    WHERE contact_retention_expires_at IS NOT NULL AND contact_anonymized_at IS NULL;

CREATE TABLE supplier_applications (
    id UUID PRIMARY KEY,
    supplier_name VARCHAR(100),
    contact_name VARCHAR(100),
    contact_email VARCHAR(320),
    normalized_contact_email VARCHAR(320),
    contact_phone VARCHAR(30),
    memo TEXT,
    idempotency_key VARCHAR(200),
    request_hash VARCHAR(128),
    consent_policy_version VARCHAR(50) NOT NULL,
    consented_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    reviewed_by_admin_id UUID REFERENCES users(id),
    review_reason_code VARCHAR(60),
    review_reason VARCHAR(500),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    approved_supplier_id UUID UNIQUE REFERENCES suppliers(id),
    review_action VARCHAR(20),
    approval_mode VARCHAR(30),
    requested_existing_supplier_id UUID REFERENCES suppliers(id),
    review_idempotency_key VARCHAR(200),
    review_request_hash VARCHAR(128),
    review_result_snapshot JSONB,
    retention_expires_at TIMESTAMP WITH TIME ZONE,
    anonymized_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_supplier_applications_status
        CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_supplier_applications_review_action
        CHECK (review_action IS NULL OR review_action IN ('APPROVE', 'REJECT')),
    CONSTRAINT ck_supplier_applications_approval_mode
        CHECK (approval_mode IS NULL OR approval_mode IN ('CREATE_NEW', 'LINK_EXISTING')),
    CONSTRAINT ck_supplier_applications_reason_code
        CHECK (
            review_reason_code IS NULL
            OR review_reason_code IN (
                'APPLICATION_APPROVED',
                'INCOMPLETE_INFORMATION',
                'OUT_OF_SCOPE',
                'POLICY_NOT_MET',
                'DUPLICATE_OR_EXISTING_RELATIONSHIP'
            )
        ),
    CONSTRAINT ck_supplier_applications_mode_target
        CHECK (
            approval_mode IS NULL
            OR (approval_mode = 'CREATE_NEW' AND requested_existing_supplier_id IS NULL)
            OR (approval_mode = 'LINK_EXISTING' AND requested_existing_supplier_id IS NOT NULL)
        ),
    CONSTRAINT ck_supplier_applications_review_reason_action
        CHECK (
            review_reason_code IS NULL
            OR (review_action = 'APPROVE' AND review_reason_code = 'APPLICATION_APPROVED')
            OR (review_action = 'REJECT' AND review_reason_code <> 'APPLICATION_APPROVED')
        )
);

CREATE INDEX idx_supplier_applications_review_queue
    ON supplier_applications(status, created_at);
CREATE INDEX idx_supplier_applications_retention
    ON supplier_applications(retention_expires_at)
    WHERE retention_expires_at IS NOT NULL AND anonymized_at IS NULL;
CREATE UNIQUE INDEX uk_supplier_applications_active_email
    ON supplier_applications(normalized_contact_email)
    WHERE normalized_contact_email IS NOT NULL AND status IN ('SUBMITTED', 'APPROVED');
CREATE UNIQUE INDEX uk_supplier_applications_idempotency_key
    ON supplier_applications(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE supplier_invites (
    id UUID PRIMARY KEY,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    recipient_email VARCHAR(320),
    token_digest VARCHAR(128) NOT NULL,
    issuance_idempotency_key VARCHAR(200),
    issuance_request_hash VARCHAR(128),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    consumed_by_user_id UUID REFERENCES users(id),
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoked_by_admin_id UUID REFERENCES users(id),
    revocation_reason_code VARCHAR(40),
    recipient_retention_expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recipient_anonymized_at TIMESTAMP WITH TIME ZONE,
    created_by_admin_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_supplier_invites_token_digest UNIQUE (token_digest),
    CONSTRAINT ck_supplier_invites_terminal_state
        CHECK (consumed_at IS NULL OR revoked_at IS NULL),
    CONSTRAINT ck_supplier_invites_consumed_actor
        CHECK (consumed_by_user_id IS NULL OR consumed_at IS NOT NULL),
    CONSTRAINT ck_supplier_invites_revoked_actor
        CHECK (revoked_by_admin_id IS NULL OR revoked_at IS NOT NULL),
    CONSTRAINT ck_supplier_invites_revocation_reason
        CHECK (
            revocation_reason_code IS NULL
            OR revocation_reason_code IN (
                'DELIVERY_FAILED',
                'INVITE_EXPIRED',
                'RECIPIENT_CHANGED',
                'ADMIN_REISSUE'
            )
        )
);

CREATE INDEX idx_supplier_invites_supplier_created
    ON supplier_invites(supplier_id, created_at DESC);
CREATE INDEX idx_supplier_invites_recipient_retention
    ON supplier_invites(recipient_retention_expires_at)
    WHERE recipient_anonymized_at IS NULL;
CREATE UNIQUE INDEX uk_supplier_invites_open_supplier
    ON supplier_invites(supplier_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
CREATE UNIQUE INDEX uk_supplier_invites_issuance_key
    ON supplier_invites(supplier_id, issuance_idempotency_key)
    WHERE issuance_idempotency_key IS NOT NULL;

CREATE TABLE supplier_portal_action_histories (
    id UUID PRIMARY KEY,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    actor_admin_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(40) NOT NULL,
    before_portal_status VARCHAR(30) NOT NULL,
    after_portal_status VARCHAR(30) NOT NULL,
    before_sales_status VARCHAR(20) NOT NULL,
    after_sales_status VARCHAR(20) NOT NULL,
    sales_action VARCHAR(20),
    reason VARCHAR(500),
    request_hash VARCHAR(128),
    idempotency_key VARCHAR(200),
    result_snapshot JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_supplier_portal_action_histories_action
        CHECK (action IN (
            'INVITE_REISSUED',
            'INVITE_REVOKED',
            'PORTAL_SUSPENDED',
            'PORTAL_REACTIVATED',
            'PORTAL_DISABLED',
            'MANAGER_DISCONNECTED',
            'CONTACT_EMAIL_CHANGED',
            'SALES_STATUS_CHANGED'
        )),
    CONSTRAINT ck_supplier_portal_action_histories_portal_status
        CHECK (
            before_portal_status IN ('DISABLED', 'PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED')
            AND after_portal_status IN ('DISABLED', 'PENDING_ACTIVATION', 'ACTIVE', 'SUSPENDED')
        ),
    CONSTRAINT ck_supplier_portal_action_histories_sales_status
        CHECK (
            before_sales_status IN ('ACTIVE', 'INACTIVE')
            AND after_sales_status IN ('ACTIVE', 'INACTIVE')
        ),
    CONSTRAINT ck_supplier_portal_action_histories_sales_action
        CHECK (sales_action IS NULL OR sales_action IN ('KEEP', 'PAUSE'))
);

CREATE INDEX idx_supplier_portal_action_histories_supplier_created
    ON supplier_portal_action_histories(supplier_id, created_at DESC);
CREATE UNIQUE INDEX uk_supplier_portal_action_histories_idempotency_key
    ON supplier_portal_action_histories(supplier_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE fulfillments
    ADD COLUMN channel VARCHAR(30),
    ADD COLUMN requested_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN operational_owner VARCHAR(20),
    ADD COLUMN pii_access_cutoff_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN handed_over_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN handed_over_reason VARCHAR(200),
    ADD COLUMN handed_over_by_admin_id UUID REFERENCES users(id);

UPDATE fulfillments
SET channel = CASE
        WHEN purchase_provider = 'DOMEGGOOK' THEN 'DOMEGGOOK_API'
        ELSE 'COREABLE_MANUAL'
    END,
    operational_owner = 'COREABLE';

ALTER TABLE fulfillments
    ALTER COLUMN channel SET NOT NULL,
    ALTER COLUMN channel SET DEFAULT 'COREABLE_MANUAL',
    ALTER COLUMN operational_owner SET NOT NULL,
    ALTER COLUMN operational_owner SET DEFAULT 'COREABLE',
    ADD CONSTRAINT ck_fulfillments_channel
        CHECK (channel IN ('COREABLE_MANUAL', 'DOMEGGOOK_API', 'SUPPLIER_PORTAL')),
    ADD CONSTRAINT ck_fulfillments_operational_owner
        CHECK (operational_owner IN ('COREABLE', 'SUPPLIER')),
    ADD CONSTRAINT ck_fulfillments_supplier_owner_channel
        CHECK (operational_owner <> 'SUPPLIER' OR channel = 'SUPPLIER_PORTAL'),
    ADD CONSTRAINT ck_fulfillments_portal_request_times
        CHECK (channel <> 'SUPPLIER_PORTAL' OR (requested_at IS NOT NULL AND pii_access_cutoff_at IS NOT NULL)),
    ADD CONSTRAINT ck_fulfillments_supplier_owner_not_handed_over
        CHECK (operational_owner <> 'SUPPLIER' OR handed_over_at IS NULL),
    ADD CONSTRAINT ck_fulfillments_handover_fields
        CHECK (
            handed_over_at IS NOT NULL
            OR (handed_over_reason IS NULL AND handed_over_by_admin_id IS NULL)
        );

CREATE INDEX idx_fulfillments_supplier_portal_owner
    ON fulfillments(supplier_id, operational_owner, id)
    WHERE channel = 'SUPPLIER_PORTAL' AND status IN ('PENDING', 'ORDERED');

CREATE TABLE fulfillment_handover_histories (
    id UUID PRIMARY KEY,
    fulfillment_id UUID NOT NULL REFERENCES fulfillments(id),
    actor_type VARCHAR(20) NOT NULL,
    actor_admin_id UUID REFERENCES users(id),
    reason_code VARCHAR(50) NOT NULL,
    reason VARCHAR(200),
    request_hash VARCHAR(128),
    idempotency_key VARCHAR(200),
    result_snapshot JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_fulfillment_handover_histories_actor_type
        CHECK (actor_type IN ('ADMIN', 'SYSTEM')),
    CONSTRAINT ck_fulfillment_handover_histories_actor
        CHECK (
            (actor_type = 'ADMIN' AND actor_admin_id IS NOT NULL)
            OR (actor_type = 'SYSTEM' AND actor_admin_id IS NULL)
        ),
    CONSTRAINT ck_fulfillment_handover_histories_reason_code
        CHECK (reason_code IN (
            'ADMIN_TAKEOVER',
            'PII_CUTOFF_REACHED',
            'PORTAL_SUSPENDED',
            'PORTAL_DISABLED',
            'MANAGER_DISCONNECTED',
            'CONTACT_EMAIL_CHANGED',
            'CONTRACT_EXPIRED',
            'CONTRACT_REVOKED',
            'SUPPLIER_SHORTAGE_REPORTED',
            'TERMINAL_STATE'
        )),
    CONSTRAINT ck_fulfillment_handover_histories_admin_reason
        CHECK (reason_code <> 'ADMIN_TAKEOVER' OR reason IS NOT NULL)
);

CREATE INDEX idx_fulfillment_handover_histories_fulfillment_created
    ON fulfillment_handover_histories(fulfillment_id, created_at DESC);
CREATE UNIQUE INDEX uk_fulfillment_handover_histories_idempotency_key
    ON fulfillment_handover_histories(fulfillment_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE notification_logs
    ALTER COLUMN recipient DROP NOT NULL,
    ADD COLUMN supplier_id UUID REFERENCES suppliers(id),
    ADD COLUMN supplier_invite_id UUID REFERENCES supplier_invites(id),
    ADD COLUMN recipient_retention_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN recipient_anonymized_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_notification_logs_supplier_id ON notification_logs(supplier_id);
CREATE INDEX idx_notification_logs_supplier_invite_id ON notification_logs(supplier_invite_id);
CREATE INDEX idx_notification_logs_recipient_retention
    ON notification_logs(recipient_retention_expires_at)
    WHERE recipient_retention_expires_at IS NOT NULL AND recipient_anonymized_at IS NULL;
