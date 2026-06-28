ALTER TABLE refunds
    ADD COLUMN reviewed_by_admin_id UUID,
    ADD COLUMN admin_review_reason TEXT,
    ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE;
