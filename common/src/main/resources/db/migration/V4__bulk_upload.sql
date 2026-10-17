-- Bulk upload: uploads append, duplicates are detected by content hash, and failed resumes keep their reason.
ALTER TABLE candidates ADD COLUMN content_hash VARCHAR(64);
ALTER TABLE candidates ADD COLUMN original_file_name VARCHAR(255);
ALTER TABLE candidates ADD COLUMN failure_reason TEXT;
ALTER TABLE candidates ADD COLUMN created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now();
CREATE UNIQUE INDEX IF NOT EXISTS uq_candidates_session_hash ON candidates (session_id, content_hash) WHERE content_hash IS NOT NULL;
