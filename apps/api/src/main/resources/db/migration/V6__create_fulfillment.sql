CREATE TABLE fulfillments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    status VARCHAR(30) NOT NULL,
    supplier_order_started_at TIMESTAMP WITH TIME ZONE,
    supplier_order_number VARCHAR(100),
    ordered_address_snapshot TEXT,
    ordered_by_admin_id UUID,
    ordered_at TIMESTAMP WITH TIME ZONE,
    expected_ship_date DATE,
    supplier_response_memo TEXT,
    out_of_stock_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fulfillments_order_id UNIQUE (order_id)
);

CREATE INDEX idx_fulfillments_order_id ON fulfillments(order_id);
CREATE INDEX idx_fulfillments_supplier_id ON fulfillments(supplier_id);
CREATE INDEX idx_fulfillments_status ON fulfillments(status);

CREATE TABLE admin_order_action_histories (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    admin_user_id UUID NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    before_status VARCHAR(30) NOT NULL,
    after_status VARCHAR(30) NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_admin_order_action_histories_order_id ON admin_order_action_histories(order_id);
CREATE INDEX idx_admin_order_action_histories_admin_user_id ON admin_order_action_histories(admin_user_id);
CREATE INDEX idx_admin_order_action_histories_action_type ON admin_order_action_histories(action_type);
