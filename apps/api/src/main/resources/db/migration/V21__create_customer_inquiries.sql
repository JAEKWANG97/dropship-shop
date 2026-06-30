CREATE TABLE customer_inquiries (
    id UUID PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL,
    phone VARCHAR(50),
    subject VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_customer_inquiries_created_at
    ON customer_inquiries(created_at DESC);
