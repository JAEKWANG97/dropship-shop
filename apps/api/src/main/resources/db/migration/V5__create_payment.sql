CREATE TABLE payments (
    id UUID PRIMARY KEY,
    payment_group_id UUID NOT NULL REFERENCES payment_groups(id),
    provider VARCHAR(30) NOT NULL,
    provider_payment_key VARCHAR(200) NOT NULL,
    method VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    requested_amount BIGINT NOT NULL,
    approved_amount BIGINT,
    approved_at TIMESTAMP WITH TIME ZONE,
    exception_reason VARCHAR(60),
    idempotency_key VARCHAR(200),
    failure_code VARCHAR(100),
    failure_message VARCHAR(1000),
    raw_provider_status VARCHAR(100),
    last_synced_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_payments_provider_payment_key UNIQUE (provider_payment_key)
);

CREATE INDEX idx_payments_payment_group_id ON payments(payment_group_id);
CREATE INDEX idx_payments_status ON payments(status);

CREATE TABLE payment_events (
    id UUID PRIMARY KEY,
    payment_id UUID REFERENCES payments(id),
    payment_group_id UUID NOT NULL REFERENCES payment_groups(id),
    order_id UUID REFERENCES orders(id),
    provider_payment_key VARCHAR(200),
    event_type VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(200),
    raw_payload TEXT,
    result_message VARCHAR(1000),
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_payment_events_payment_id ON payment_events(payment_id);
CREATE INDEX idx_payment_events_payment_group_id ON payment_events(payment_group_id);
CREATE INDEX idx_payment_events_provider_payment_key ON payment_events(provider_payment_key);
