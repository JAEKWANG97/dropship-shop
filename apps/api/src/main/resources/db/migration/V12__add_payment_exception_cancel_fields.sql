ALTER TABLE payments
    ADD COLUMN provider_cancel_transaction_key VARCHAR(200),
    ADD COLUMN cancel_requested_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE;
