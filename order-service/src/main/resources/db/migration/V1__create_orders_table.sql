CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    total_value NUMERIC(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_orders_external_id UNIQUE (external_id),
    CONSTRAINT ck_orders_total_value_positive CHECK (total_value > 0),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'ERROR')),
    CONSTRAINT ck_orders_attempt_count_non_negative CHECK (attempt_count >= 0)
);

CREATE INDEX idx_orders_status_created_at
    ON orders (status, created_at);
