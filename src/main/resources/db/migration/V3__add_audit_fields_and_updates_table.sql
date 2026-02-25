-- Add audit fields to submissions
ALTER TABLE submissions ADD COLUMN ip_address  VARCHAR(45);
ALTER TABLE submissions ADD COLUMN user_agent  TEXT;
ALTER TABLE submissions ADD COLUMN submitted_at TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE submissions ADD COLUMN updated_at   TIMESTAMP;

-- Backfill submitted_at from created_at for any existing rows
UPDATE submissions SET submitted_at = created_at WHERE submitted_at IS NULL;

-- Audit log for every email change
CREATE TABLE submission_updates (
    id              BIGSERIAL PRIMARY KEY,
    submission_id   BIGINT       NOT NULL REFERENCES submissions(id),
    email           VARCHAR(255) NOT NULL,
    ip_address      VARCHAR(45),
    user_agent      TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_submission_updates_submission_id ON submission_updates (submission_id);
