CREATE TABLE submissions (
    id          BIGSERIAL PRIMARY KEY,
    data_id     VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uq_data_id UNIQUE (data_id)
);

CREATE INDEX idx_submissions_data_id ON submissions (data_id);
