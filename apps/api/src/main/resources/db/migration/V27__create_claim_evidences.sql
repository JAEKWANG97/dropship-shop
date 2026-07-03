CREATE TABLE claim_evidences (
    id UUID PRIMARY KEY,
    claim_id UUID NOT NULL REFERENCES claims(id),
    file_url VARCHAR(500) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255),
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_claim_evidences_claim_id ON claim_evidences(claim_id);
