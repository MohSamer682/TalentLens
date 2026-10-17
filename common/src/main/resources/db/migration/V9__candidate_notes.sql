CREATE TABLE candidate_notes (
    id           VARCHAR(36) PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    author_id    VARCHAR(36) NOT NULL REFERENCES users(id),
    content      TEXT NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_candidate_notes_candidate ON candidate_notes(candidate_id, created_at DESC);
