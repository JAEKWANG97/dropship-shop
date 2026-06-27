CREATE TABLE claims (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    user_id UUID NOT NULL REFERENCES users(id),
    claim_type VARCHAR(30) NOT NULL,
    claim_reason VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    requested_action VARCHAR(30) NOT NULL,
    customer_memo TEXT NOT NULL,
    reviewed_by_admin_id UUID,
    admin_review_reason TEXT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_claims_order_id ON claims(order_id);
CREATE INDEX idx_claims_user_id ON claims(user_id);
CREATE INDEX idx_claims_claim_type_status ON claims(claim_type, status);
