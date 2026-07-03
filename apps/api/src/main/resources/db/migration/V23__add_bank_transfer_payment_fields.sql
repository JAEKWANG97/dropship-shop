ALTER TABLE payment_groups
    ADD COLUMN bank_transfer_bank_name VARCHAR(100),
    ADD COLUMN bank_transfer_account_number VARCHAR(100),
    ADD COLUMN bank_transfer_account_holder VARCHAR(100),
    ADD COLUMN bank_transfer_depositor_name VARCHAR(100),
    ADD COLUMN bank_transfer_cash_receipt_notice VARCHAR(500),
    ADD COLUMN deposit_confirmed_by_admin_id UUID,
    ADD COLUMN deposit_confirmed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN deposit_confirmation_reason TEXT,
    ADD COLUMN deposit_mismatch_memo TEXT,
    ADD COLUMN deposit_mismatch_recorded_by_admin_id UUID,
    ADD COLUMN deposit_mismatch_recorded_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN unpaid_cancelled_by_admin_id UUID,
    ADD COLUMN unpaid_cancelled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN unpaid_cancel_reason TEXT;

ALTER TABLE refunds
    ADD COLUMN manual_refunded_by_admin_id UUID,
    ADD COLUMN manual_refunded_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN manual_refund_reason TEXT,
    ADD COLUMN manual_refund_bank_name VARCHAR(100),
    ADD COLUMN manual_refund_account_number VARCHAR(100),
    ADD COLUMN manual_refund_account_holder VARCHAR(100);
