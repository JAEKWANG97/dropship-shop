ALTER TABLE users
    ADD COLUMN phone_number VARCHAR(30),
    ADD COLUMN phone_verified_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE phone_verification_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    phone_number VARCHAR(30) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    attempt_count INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_phone_verification_codes_user_phone_created
    ON phone_verification_codes(user_id, phone_number, created_at DESC);
