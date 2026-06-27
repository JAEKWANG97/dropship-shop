CREATE TABLE users (
    id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(191) NOT NULL,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_provider_provider_user_id UNIQUE (provider, provider_user_id)
);
