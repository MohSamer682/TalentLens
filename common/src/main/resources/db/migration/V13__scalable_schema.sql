-- 1. Resume text out of the candidates row: it was never read, yet loaded with every candidate list query.
CREATE TABLE IF NOT EXISTS candidate_texts (
    candidate_id VARCHAR(255) PRIMARY KEY REFERENCES candidates (id) ON DELETE CASCADE,
    raw_text     TEXT NOT NULL
);
INSERT INTO candidate_texts (candidate_id, raw_text)
    SELECT id, raw_text FROM candidates WHERE raw_text IS NOT NULL
    ON CONFLICT DO NOTHING;
ALTER TABLE candidates DROP COLUMN IF EXISTS raw_text;

-- 2. Session delete is one statement: the database removes the whole aggregate.
--    Constraint names differ between Flyway-built and Hibernate-built databases, so look them up.
DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN
        SELECT c.conrelid::regclass AS tbl, c.conname, a.attname AS col, c.confrelid::regclass AS ref
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
        WHERE c.contype = 'f' AND array_length(c.conkey, 1) = 1
          AND ((c.conrelid = 'candidates'::regclass AND a.attname = 'session_id')
            OR (c.conrelid = 'scoring_weights'::regclass AND a.attname = 'session_id')
            OR (c.conrelid = 'batch_jobs'::regclass AND a.attname = 'session_id')
            OR (c.conrelid = 'candidate_scores'::regclass AND a.attname = 'candidate_id'))
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %s (id) ON DELETE CASCADE',
                       r.tbl, r.conname, r.col, r.ref);
    END LOOP;
END $$;

-- 3. Audit: plural like every other table, actor linked to users, searchable by target and by time.
ALTER TABLE IF EXISTS audit_event RENAME TO audit_events;
-- Until now the column held the login email; keep the actor by mapping it to the user id, then drop only true orphans.
UPDATE audit_events a SET actor_user_id = u.id FROM users u
    WHERE a.actor_user_id IS NOT NULL AND a.actor_user_id NOT IN (SELECT id FROM users)
      AND lower(u.email) = lower(a.actor_user_id);
UPDATE audit_events SET actor_user_id = NULL
    WHERE actor_user_id IS NOT NULL AND actor_user_id NOT IN (SELECT id FROM users);
ALTER TABLE audit_events
    ADD CONSTRAINT fk_audit_events_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_audit_events_target ON audit_events (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_occurred ON audit_events (occurred_at DESC);

-- 4. Closed value sets. A new enum value needs a migration, which is already the rule for role grants.
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN
    ('ADMIN','HR_CHIEF','RECRUITER','TECHNICAL_INTERVIEWER','HR_INTERVIEWER','DEPARTMENT_DIRECTOR','CEO','CHAIRMAN'));
ALTER TABLE role_permissions ADD CONSTRAINT ck_role_permissions_role CHECK (role IN
    ('ADMIN','HR_CHIEF','RECRUITER','TECHNICAL_INTERVIEWER','HR_INTERVIEWER','DEPARTMENT_DIRECTOR','CEO','CHAIRMAN'));
ALTER TABLE role_permissions ADD CONSTRAINT ck_role_permissions_permission CHECK (permission IN
    ('SESSION_RUN','SESSION_VIEW_ALL','CANDIDATE_REVIEW','PIPELINE_MANAGE','OFFER_APPROVE','INTERVIEW_FEEDBACK',
     'ANALYTICS_VIEW','USER_MANAGE','ROLE_MANAGE','AUDIT_VIEW'));
ALTER TABLE screening_sessions ADD CONSTRAINT ck_sessions_status CHECK (status IN ('PENDING','RUNNING','DONE','FAILED'));
ALTER TABLE candidates
    ADD CONSTRAINT ck_candidates_status CHECK (status IN ('PENDING','SCORED','FAILED')),
    ADD CONSTRAINT ck_candidates_review CHECK (review_status IN
        ('NONE','SHORTLISTED','CONTACTED','INTERVIEW_SCHEDULED','INTERVIEWED','OFFER','OFFER_APPROVED','OFFER_SENT',
         'ACCEPTED','DECLINED','JOINING_SET'));
ALTER TABLE candidate_interviews
    ADD CONSTRAINT ck_interviews_kind CHECK (kind IN ('TECHNICAL','HR','MANAGEMENT')),
    ADD CONSTRAINT ck_interviews_status CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED')),
    ADD CONSTRAINT ck_interviews_place CHECK (place_type IN ('HQ','ONLINE','OTHER')),
    ADD CONSTRAINT ck_interviews_recommendation CHECK (recommendation IS NULL OR recommendation IN
        ('STRONG_HIRE','HIRE','NO_HIRE','STRONG_NO_HIRE'));
ALTER TABLE candidate_offers
    ADD CONSTRAINT ck_offers_response CHECK (candidate_response IS NULL OR candidate_response IN ('ACCEPTED','DECLINED'));

-- 5. Foreign keys Postgres does not index on its own.
CREATE INDEX IF NOT EXISTS idx_candidates_reviewed_by ON candidates (reviewed_by) WHERE reviewed_by IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_candidate_interviews_created_by ON candidate_interviews (created_by);
CREATE INDEX IF NOT EXISTS idx_candidate_offers_uploaded_by ON candidate_offers (uploaded_by);
