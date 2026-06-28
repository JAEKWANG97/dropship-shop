CREATE TABLE notification_logs (
    id UUID PRIMARY KEY,
    user_id UUID,
    order_id UUID,
    payment_group_id UUID,
    claim_id UUID,
    refund_id UUID,
    type VARCHAR(50) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    transactional BOOLEAN NOT NULL,
    status VARCHAR(30) NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    template_key VARCHAR(100) NOT NULL,
    payload_snapshot TEXT NOT NULL,
    failure_reason TEXT,
    sent_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_notification_logs_user_id ON notification_logs(user_id);
CREATE INDEX idx_notification_logs_order_id ON notification_logs(order_id);
CREATE INDEX idx_notification_logs_payment_group_id ON notification_logs(payment_group_id);
CREATE INDEX idx_notification_logs_type_status ON notification_logs(type, status);
