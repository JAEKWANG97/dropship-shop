ALTER TABLE customer_inquiries
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    ADD COLUMN consent_policy_version VARCHAR(100),
    ADD COLUMN consented_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN retention_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN admin_memo TEXT,
    ADD COLUMN answer TEXT,
    ADD COLUMN handled_by_admin_id UUID REFERENCES users(id),
    ADD COLUMN answered_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

UPDATE customer_inquiries
SET retention_expires_at = created_at + INTERVAL '3 years',
    updated_at = created_at;

ALTER TABLE customer_inquiries
    ALTER COLUMN retention_expires_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX idx_customer_inquiries_status_created_at
    ON customer_inquiries(status, created_at DESC);

CREATE INDEX idx_customer_inquiries_email_created_at
    ON customer_inquiries(email, created_at DESC);

CREATE INDEX idx_customer_inquiries_retention_expires_at
    ON customer_inquiries(retention_expires_at);

ALTER TABLE notification_logs
    ADD COLUMN customer_inquiry_id UUID REFERENCES customer_inquiries(id) ON DELETE SET NULL;

CREATE INDEX idx_notification_logs_customer_inquiry_id
    ON notification_logs(customer_inquiry_id);
