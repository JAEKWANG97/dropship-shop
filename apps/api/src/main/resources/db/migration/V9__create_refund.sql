CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    payment_group_id UUID NOT NULL REFERENCES payment_groups(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    payment_id UUID REFERENCES payments(id),
    reason VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    refund_amount BIGINT NOT NULL,
    refund_scope VARCHAR(30) NOT NULL,
    provider_payment_key VARCHAR(200),
    provider_cancel_transaction_key VARCHAR(200),
    idempotency_key VARCHAR(200),
    failure_code VARCHAR(100),
    failure_message VARCHAR(1000),
    raw_provider_status VARCHAR(100),
    requested_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_refunds_order_id UNIQUE (order_id)
);

CREATE INDEX idx_refunds_payment_group_id ON refunds(payment_group_id);
CREATE INDEX idx_refunds_payment_id ON refunds(payment_id);
CREATE INDEX idx_refunds_status ON refunds(status);
