CREATE TABLE user_policy_agreements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    terms_version VARCHAR(50) NOT NULL,
    privacy_version VARCHAR(50) NOT NULL,
    agreed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_policy_agreements_user_versions UNIQUE (user_id, terms_version, privacy_version)
);

CREATE INDEX idx_user_policy_agreements_user_id ON user_policy_agreements(user_id);
CREATE INDEX idx_user_policy_agreements_agreed_at ON user_policy_agreements(agreed_at);
