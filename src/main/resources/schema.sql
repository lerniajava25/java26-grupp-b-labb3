CREATE TABLE IF NOT EXISTS snow_order (
    id VARCHAR(36) PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    customer_contact VARCHAR(200) NOT NULL,
    address VARCHAR(200) NOT NULL,
    notes CLOB NOT NULL,
    boundary_json CLOB NOT NULL,
    status VARCHAR(20) NOT NULL,
    clearer_name VARCHAR(100),
    clearer_contact VARCHAR(200),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS snow_order_photo (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    object_key VARCHAR(100) NOT NULL UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    CONSTRAINT fk_photo_order FOREIGN KEY (order_id) REFERENCES snow_order(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_snow_order_status_created ON snow_order(status, created_at);
CREATE INDEX IF NOT EXISTS idx_snow_order_photo_order ON snow_order_photo(order_id);
