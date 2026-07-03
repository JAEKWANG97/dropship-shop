ALTER TABLE claims
    ADD COLUMN return_received_by_admin_id UUID,
    ADD COLUMN return_received_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN return_received_memo TEXT,
    ADD COLUMN refund_id UUID REFERENCES refunds(id),
    ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX idx_claims_refund_id_unique ON claims(refund_id);
