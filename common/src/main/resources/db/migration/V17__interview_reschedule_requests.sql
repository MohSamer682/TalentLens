-- A candidate asks to move a scheduled interview and suggests up to three times; HR accepts one or declines.
CREATE TABLE interview_reschedule_requests (
    id              VARCHAR(36)   PRIMARY KEY,
    interview_id    VARCHAR(36)   NOT NULL REFERENCES candidate_interviews (id) ON DELETE CASCADE,
    requested_by    VARCHAR(255)  NOT NULL REFERENCES users (id),
    reason          VARCHAR(1000) NOT NULL CHECK (btrim(reason) <> ''),
    status          VARCHAR(16)   NOT NULL CHECK (status IN ('PENDING','ACCEPTED','DECLINED','WITHDRAWN')),
    resolution_note VARCHAR(1000),
    resolved_by     VARCHAR(255)  REFERENCES users (id) ON DELETE SET NULL,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_reschedule_resolved CHECK ((status = 'PENDING') = (resolved_at IS NULL))
);
-- The real guard against a double submit: one open request per interview.
CREATE UNIQUE INDEX uq_reschedule_pending_per_interview ON interview_reschedule_requests (interview_id) WHERE status = 'PENDING';
-- HR's waiting requests and the pipeline badge.
CREATE INDEX idx_reschedule_pending_inbox ON interview_reschedule_requests (created_at) WHERE status = 'PENDING';
CREATE INDEX idx_reschedule_requested_by ON interview_reschedule_requests (requested_by);
CREATE INDEX idx_reschedule_resolved_by ON interview_reschedule_requests (resolved_by) WHERE resolved_by IS NOT NULL;

CREATE TABLE interview_reschedule_slots (
    request_id     VARCHAR(36) NOT NULL REFERENCES interview_reschedule_requests (id) ON DELETE CASCADE,
    slot_no        INTEGER     NOT NULL CHECK (slot_no BETWEEN 1 AND 3),
    proposed_start TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (request_id, slot_no)
);
