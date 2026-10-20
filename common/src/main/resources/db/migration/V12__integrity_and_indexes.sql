-- Integrity and index hardening. Fails loudly if existing rows break a rule, instead of hiding bad data.

-- The same address in a different case registered as a second account (lookups already ignore case).
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower ON users (lower(email));

-- Scores and weights are percentages; rating matches the 1-5 the API accepts.
ALTER TABLE candidate_scores
    ADD CONSTRAINT ck_scores_range CHECK (
        coalesce(technical_skills, 0) BETWEEN 0 AND 100 AND coalesce(experience, 0) BETWEEN 0 AND 100
        AND coalesce(education, 0) BETWEEN 0 AND 100 AND coalesce(soft_skills, 0) BETWEEN 0 AND 100
        AND coalesce(culture_fit, 0) BETWEEN 0 AND 100 AND coalesce(composite_score, 0) BETWEEN 0 AND 100);
ALTER TABLE scoring_weights
    ADD CONSTRAINT ck_weights_nonneg CHECK (
        coalesce(technical_skills, 0) >= 0 AND coalesce(experience, 0) >= 0 AND coalesce(education, 0) >= 0
        AND coalesce(soft_skills, 0) >= 0 AND coalesce(culture_fit, 0) >= 0);
ALTER TABLE candidate_interviews
    ADD CONSTRAINT ck_interview_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    ADD CONSTRAINT ck_interview_duration CHECK (duration_minutes > 0);

-- Code reads one offer per candidate (Optional); make the database say so.
DROP INDEX IF EXISTS idx_candidate_offers_candidate;
CREATE UNIQUE INDEX IF NOT EXISTS uq_candidate_offers_candidate ON candidate_offers (candidate_id);

-- Spring Batch is not used; the column was never read.
ALTER TABLE batch_jobs DROP COLUMN IF EXISTS spring_batch_job_id;

-- Postgres does not index foreign keys: these back the pipeline board and "my work" lookups.
CREATE INDEX IF NOT EXISTS idx_candidates_review_status ON candidates (review_status) WHERE review_status <> 'NONE';
CREATE INDEX IF NOT EXISTS idx_candidate_notes_author ON candidate_notes (author_id);
CREATE INDEX IF NOT EXISTS idx_batch_jobs_session_started ON batch_jobs (session_id, started_at DESC);

-- Counters are always written together; nulls only forced null checks in the code.
UPDATE batch_jobs SET total = coalesce(total, 0), processed = coalesce(processed, 0), failed = coalesce(failed, 0);
ALTER TABLE batch_jobs
    ALTER COLUMN total SET DEFAULT 0, ALTER COLUMN total SET NOT NULL,
    ALTER COLUMN processed SET DEFAULT 0, ALTER COLUMN processed SET NOT NULL,
    ALTER COLUMN failed SET DEFAULT 0, ALTER COLUMN failed SET NOT NULL;
