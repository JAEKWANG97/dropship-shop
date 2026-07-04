ALTER TABLE users
    ADD COLUMN referral_code VARCHAR(20),
    ADD COLUMN referred_by_user_id UUID REFERENCES users(id),
    ADD COLUMN referred_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE users
    ADD CONSTRAINT uk_users_referral_code UNIQUE (referral_code);

CREATE INDEX idx_users_referred_by_user_id
    ON users(referred_by_user_id);

CREATE INDEX idx_users_referred_at
    ON users(referred_at DESC);
