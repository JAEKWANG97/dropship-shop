CREATE TABLE policy_documents (
    id UUID PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    version VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_policy_documents_type_version UNIQUE (type, version)
);

CREATE INDEX idx_policy_documents_type_status ON policy_documents(type, status);
