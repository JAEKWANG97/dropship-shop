ALTER TABLE payment_groups
    ADD COLUMN actual_depositor_name VARCHAR(100),
    ADD COLUMN actual_deposit_amount BIGINT,
    ADD COLUMN deposit_received_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN deposit_transaction_reference VARCHAR(200),
    ADD CONSTRAINT chk_payment_groups_actual_deposit_amount_positive
        CHECK (actual_deposit_amount IS NULL OR actual_deposit_amount > 0);

ALTER TABLE refunds
    ADD COLUMN manual_refund_transferred_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN manual_refund_transaction_reference VARCHAR(200);
