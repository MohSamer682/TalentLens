-- New permissions for hiring pipeline, offers and interview feedback
INSERT INTO role_permissions (role, permission) VALUES
    ('ADMIN', 'PIPELINE_MANAGE'),
    ('ADMIN', 'OFFER_APPROVE'),
    ('ADMIN', 'INTERVIEW_FEEDBACK'),
    ('RECRUITER', 'PIPELINE_MANAGE'),
    ('HR_CHIEF', 'OFFER_APPROVE'),
    ('TECHNICAL_INTERVIEWER', 'INTERVIEW_FEEDBACK'),
    ('HR_INTERVIEWER', 'INTERVIEW_FEEDBACK')
ON CONFLICT DO NOTHING;

-- Interviews scheduled with candidates
CREATE TABLE candidate_interviews (
    id                   VARCHAR(36) PRIMARY KEY,
    candidate_id         VARCHAR(36) NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    kind                 VARCHAR(32) NOT NULL,
    interviewer_id       VARCHAR(36) NOT NULL REFERENCES users(id),
    scheduled_start      TIMESTAMP WITH TIME ZONE NOT NULL,
    duration_minutes     INT NOT NULL DEFAULT 60,
    location             VARCHAR(500),
    note_to_interviewer  VARCHAR(2000),
    status               VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    recommendation       VARCHAR(32),
    rating               INT,
    feedback_submitted_at TIMESTAMP WITH TIME ZONE,
    feedback_assessment  TEXT,
    created_by           VARCHAR(36) NOT NULL REFERENCES users(id),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_candidate_interviews_candidate ON candidate_interviews(candidate_id, scheduled_start);
CREATE INDEX idx_candidate_interviews_interviewer ON candidate_interviews(interviewer_id, scheduled_start);

-- Candidate offers (PDF uploads and approval)
CREATE TABLE candidate_offers (
    id           VARCHAR(36) PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    file_key     VARCHAR(500) NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    uploaded_by  VARCHAR(36) NOT NULL REFERENCES users(id),
    uploaded_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    approved_by  VARCHAR(36) REFERENCES users(id),
    approved_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_candidate_offers_candidate ON candidate_offers(candidate_id);

-- Stage events timeline for candidate page and audit
CREATE TABLE candidate_stage_events (
    id           VARCHAR(36) PRIMARY KEY,
    candidate_id VARCHAR(36) NOT NULL REFERENCES candidates(id) ON DELETE CASCADE,
    stage        VARCHAR(32) NOT NULL,
    actor_id     VARCHAR(36) REFERENCES users(id),
    actor_name   VARCHAR(255),
    summary      VARCHAR(500) NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_candidate_stage_events_candidate ON candidate_stage_events(candidate_id, created_at DESC);
