-- Baseline: the schema Hibernate ddl-auto=update produced before Flyway was adopted.
-- Databases created that way are marked as V1 by baseline-on-migrate and skip this file.

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(255) PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    role          VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS screening_sessions (
    id                 VARCHAR(255) PRIMARY KEY,
    user_id            VARCHAR(255) NOT NULL REFERENCES users (id),
    title              VARCHAR(255) NOT NULL,
    description        VARCHAR(255),
    status             VARCHAR(255) NOT NULL,
    jd_file_key        VARCHAR(255),
    created_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completed_at       TIMESTAMP(6) WITH TIME ZONE,
    report_file_key    VARCHAR(255),
    last_error_message TEXT
);

CREATE TABLE IF NOT EXISTS scoring_weights (
    id               VARCHAR(255) PRIMARY KEY,
    session_id       VARCHAR(255) NOT NULL UNIQUE REFERENCES screening_sessions (id),
    technical_skills DOUBLE PRECISION,
    experience       DOUBLE PRECISION,
    education        DOUBLE PRECISION,
    soft_skills      DOUBLE PRECISION,
    culture_fit      DOUBLE PRECISION
);

CREATE TABLE IF NOT EXISTS candidates (
    id              VARCHAR(255) PRIMARY KEY,
    session_id      VARCHAR(255) NOT NULL REFERENCES screening_sessions (id),
    name            VARCHAR(255),
    email           VARCHAR(255),
    resume_file_key VARCHAR(255),
    raw_text        TEXT,
    status          VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS candidate_scores (
    id               VARCHAR(255) PRIMARY KEY,
    candidate_id     VARCHAR(255) NOT NULL UNIQUE REFERENCES candidates (id),
    technical_skills DOUBLE PRECISION,
    experience       DOUBLE PRECISION,
    education        DOUBLE PRECISION,
    soft_skills      DOUBLE PRECISION,
    culture_fit      DOUBLE PRECISION,
    composite_score  DOUBLE PRECISION,
    rank             INTEGER,
    justification    TEXT,
    strengths        TEXT,
    gaps             TEXT,
    skills           JSONB
);

CREATE TABLE IF NOT EXISTS batch_jobs (
    id                  VARCHAR(255) PRIMARY KEY,
    session_id          VARCHAR(255) NOT NULL REFERENCES screening_sessions (id),
    spring_batch_job_id VARCHAR(255),
    total               INTEGER,
    processed           INTEGER,
    failed              INTEGER,
    started_at          TIMESTAMP(6) WITH TIME ZONE,
    finished_at         TIMESTAMP(6) WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS audit_event (
    id            VARCHAR(255) PRIMARY KEY,
    occurred_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    actor_user_id VARCHAR(255),
    action        VARCHAR(255) NOT NULL,
    target_type   VARCHAR(255),
    target_id     VARCHAR(255),
    request_id    VARCHAR(255),
    ip            VARCHAR(255),
    outcome       VARCHAR(255),
    details       VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_sessions_user ON screening_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_candidates_session ON candidates (session_id);
CREATE INDEX IF NOT EXISTS idx_batch_jobs_session ON batch_jobs (session_id);
