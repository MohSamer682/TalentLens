ALTER TABLE users ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'HIDDEN'));
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMPTZ;
-- Admin edits and self edits can race; JPA @Version turns the loser into a 409.
ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE user_profiles (
    user_id    VARCHAR(255) PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    phone      VARCHAR(32),
    headline   VARCHAR(120),
    department VARCHAR(80),
    location   VARCHAR(120),
    bio        VARCHAR(1000),
    updated_at TIMESTAMPTZ  NOT NULL,
    updated_by VARCHAR(255) REFERENCES users (id) ON DELETE SET NULL
);

-- Deleting a user nulls actor_user_id; the email keeps the audit trail readable.
ALTER TABLE audit_events ADD COLUMN actor_email VARCHAR(255);
UPDATE audit_events a SET actor_email = u.email FROM users u WHERE a.actor_user_id = u.id;

CREATE INDEX idx_user_profiles_updated_by ON user_profiles (updated_by) WHERE updated_by IS NOT NULL;
