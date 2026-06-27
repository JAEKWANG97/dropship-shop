CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    contact_name VARCHAR(100),
    phone VARCHAR(30),
    email VARCHAR(320),
    memo TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    name VARCHAR(200) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    base_price BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    thumbnail_image_url VARCHAR(1000),
    detail_version INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_products_supplier_id ON products(supplier_id);
CREATE INDEX idx_products_status ON products(status);

CREATE TABLE product_options (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    name VARCHAR(200) NOT NULL,
    additional_price BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_product_options_product_id ON product_options(product_id);

CREATE TABLE product_images (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    type VARCHAR(20) NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    sort_order INTEGER NOT NULL,
    alt_text VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_product_images_product_id ON product_images(product_id);
CREATE UNIQUE INDEX uk_product_images_one_thumbnail
    ON product_images(product_id)
    WHERE type = 'THUMBNAIL';

CREATE TABLE product_detail_blocks (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    type VARCHAR(20) NOT NULL,
    image_url VARCHAR(1000),
    html_content TEXT,
    sort_order INTEGER NOT NULL,
    alt_text VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_product_detail_blocks_product_id ON product_detail_blocks(product_id);

CREATE TABLE product_notices (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    product_info_notice TEXT NOT NULL,
    shipping_info TEXT NOT NULL,
    as_info TEXT NOT NULL,
    return_exchange_info TEXT NOT NULL,
    effective_from TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_product_notices_product_id ON product_notices(product_id);
CREATE UNIQUE INDEX uk_product_notices_product_version ON product_notices(product_id, version);

CREATE TABLE product_change_histories (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    product_option_id UUID REFERENCES product_options(id),
    admin_user_id UUID NOT NULL,
    change_type VARCHAR(30) NOT NULL,
    before_value TEXT,
    after_value TEXT,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_product_change_histories_product_id ON product_change_histories(product_id);
