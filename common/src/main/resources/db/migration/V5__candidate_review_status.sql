ALTER TABLE candidates ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE candidates ADD COLUMN reviewed_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE candidates ADD COLUMN reviewed_by VARCHAR(255) REFERENCES users (id);
CREATE INDEX IF NOT EXISTS idx_candidates_session_review ON candidates (session_id, review_status);
