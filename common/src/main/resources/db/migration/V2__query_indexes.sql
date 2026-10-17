-- Query-path indexes (from feature/architecture-refinement V2, adjusted to this schema).
-- candidate_scores.candidate_id and scoring_weights.session_id are already indexed by their UNIQUE constraint.

-- Dashboard: a user's sessions, newest first
CREATE INDEX IF NOT EXISTS idx_sessions_user_created ON screening_sessions (user_id, created_at DESC);
DROP INDEX IF EXISTS idx_sessions_user;

-- Candidate list filtered by status
CREATE INDEX IF NOT EXISTS idx_candidates_session_status ON candidates (session_id, status);
DROP INDEX IF EXISTS idx_candidates_session;

-- Audit search by actor and time
CREATE INDEX IF NOT EXISTS idx_audit_actor_occurred ON audit_event (actor_user_id, occurred_at DESC);
