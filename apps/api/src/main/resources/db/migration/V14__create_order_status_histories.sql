CREATE TABLE order_status_histories (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    actor_user_id UUID,
    action_type VARCHAR(80) NOT NULL,
    from_status VARCHAR(30) NOT NULL,
    to_status VARCHAR(30) NOT NULL,
    guard_result VARCHAR(100) NOT NULL,
    side_effect_summary TEXT NOT NULL,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_order_status_histories_order_id ON order_status_histories(order_id);
CREATE INDEX idx_order_status_histories_actor_user_id ON order_status_histories(actor_user_id);
CREATE INDEX idx_order_status_histories_action_type ON order_status_histories(action_type);

ALTER TABLE shipments
    ADD COLUMN manual_override BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN manual_corrected_by_admin_id UUID,
    ADD COLUMN manual_corrected_at TIMESTAMP WITH TIME ZONE;
