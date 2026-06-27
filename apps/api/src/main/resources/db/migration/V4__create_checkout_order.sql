CREATE TABLE payment_groups (
    id UUID PRIMARY KEY,
    checkout_number VARCHAR(40) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(30) NOT NULL,
    total_amount BIGINT NOT NULL,
    approved_amount BIGINT,
    refundable_amount BIGINT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    policy_confirmed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_payment_groups_checkout_number UNIQUE (checkout_number)
);

CREATE INDEX idx_payment_groups_user_id ON payment_groups(user_id);
CREATE INDEX idx_payment_groups_status ON payment_groups(status);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(40) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    payment_group_id UUID NOT NULL REFERENCES payment_groups(id),
    status VARCHAR(30) NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    address1 VARCHAR(300) NOT NULL,
    address2 VARCHAR(300),
    subtotal_amount BIGINT NOT NULL,
    shipping_fee BIGINT NOT NULL,
    discount_amount BIGINT NOT NULL,
    total_amount BIGINT NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    supplier_order_started_at TIMESTAMP WITH TIME ZONE,
    address_locked_at TIMESTAMP WITH TIME ZONE,
    address_locked_by_admin_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_orders_order_number UNIQUE (order_number)
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_payment_group_id ON orders(payment_group_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    product_option_id UUID NOT NULL REFERENCES product_options(id),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    product_name VARCHAR(200) NOT NULL,
    product_summary VARCHAR(500) NOT NULL,
    product_detail_version INTEGER NOT NULL,
    product_notice_version INTEGER,
    option_name VARCHAR(200) NOT NULL,
    unit_price BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    line_amount BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
CREATE INDEX idx_order_items_product_option_id ON order_items(product_option_id);

CREATE TABLE order_policy_agreements (
    id UUID PRIMARY KEY,
    payment_group_id UUID NOT NULL REFERENCES payment_groups(id),
    user_id UUID NOT NULL REFERENCES users(id),
    terms_version VARCHAR(50) NOT NULL,
    privacy_version VARCHAR(50) NOT NULL,
    order_policy_version VARCHAR(50) NOT NULL,
    cancellation_refund_policy_version VARCHAR(50) NOT NULL,
    out_of_stock_notice_version VARCHAR(50) NOT NULL,
    confirmed_notice_text TEXT NOT NULL,
    confirmed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_order_policy_agreements_payment_group_id UNIQUE (payment_group_id)
);

CREATE INDEX idx_order_policy_agreements_user_id ON order_policy_agreements(user_id);
