-- Shortlisted candidates get their own sign-in. role_permissions keeps its old check, so a candidate can never hold a permission.
ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN
    ('ADMIN','HR_CHIEF','RECRUITER','TECHNICAL_INTERVIEWER','HR_INTERVIEWER','DEPARTMENT_DIRECTOR','CEO','CHAIRMAN','CANDIDATE'));

ALTER TABLE candidates ADD COLUMN account_user_id VARCHAR(255) REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX idx_candidates_account ON candidates (account_user_id) WHERE account_user_id IS NOT NULL;
-- One person holds at most one application per cycle.
CREATE UNIQUE INDEX uq_candidates_session_account ON candidates (session_id, account_user_id) WHERE account_user_id IS NOT NULL;

CREATE TABLE candidate_invites (
    id           VARCHAR(36)  PRIMARY KEY,
    candidate_id VARCHAR(255) NOT NULL REFERENCES candidates (id) ON DELETE CASCADE,
    token_hash   CHAR(64)     NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    used_at      TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ,
    created_by   VARCHAR(255) NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_candidate_invites_expiry CHECK (expires_at > created_at)
);
CREATE UNIQUE INDEX uq_candidate_invites_token ON candidate_invites (token_hash);
-- One live link per candidate; issuing a new one revokes the old one in the same transaction.
CREATE UNIQUE INDEX uq_candidate_invites_open ON candidate_invites (candidate_id) WHERE used_at IS NULL AND revoked_at IS NULL;
CREATE INDEX idx_candidate_invites_created_by ON candidate_invites (created_by);
