-- Job details a recruiter records when creating a cycle; candidates read them in the portal.
ALTER TABLE screening_sessions ADD COLUMN job_title VARCHAR(160);
UPDATE screening_sessions SET job_title = left(title, 160);
ALTER TABLE screening_sessions ALTER COLUMN job_title SET NOT NULL;

-- Older cycles have no code; the API requires one on create.
ALTER TABLE screening_sessions ADD COLUMN position_code VARCHAR(40)
    CONSTRAINT ck_screening_sessions_position_code CHECK (position_code ~ '^[A-Z0-9][A-Z0-9-]{1,39}$');
ALTER TABLE screening_sessions ADD COLUMN job_description TEXT;

-- Not unique: the same position can be screened in several cycles.
CREATE INDEX idx_screening_sessions_position_code ON screening_sessions (position_code);

CREATE TABLE session_required_skills (
    session_id VARCHAR(255) NOT NULL REFERENCES screening_sessions (id) ON DELETE CASCADE,
    position   INTEGER      NOT NULL CHECK (position BETWEEN 0 AND 29),
    skill      VARCHAR(80)  NOT NULL CHECK (btrim(skill) <> ''),
    PRIMARY KEY (session_id, position)
);

CREATE UNIQUE INDEX uq_session_required_skills_name ON session_required_skills (session_id, lower(skill));
