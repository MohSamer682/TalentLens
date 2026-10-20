-- Roles get a name and description admins can edit. The codes stay fixed: the offer chain and interviewer kinds depend on them.
CREATE TABLE roles (
    code        VARCHAR(40)  PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]{2,39}$'),
    name        VARCHAR(80)  NOT NULL CHECK (btrim(name) <> ''),
    description VARCHAR(300),
    kind        VARCHAR(16)  NOT NULL CHECK (kind IN ('STAFF','CANDIDATE')),
    system      BOOLEAN      NOT NULL DEFAULT true,
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(255) REFERENCES users (id) ON DELETE SET NULL,
    version     BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_roles_name ON roles (lower(name));
CREATE INDEX idx_roles_updated_by ON roles (updated_by) WHERE updated_by IS NOT NULL;

INSERT INTO roles (code, name, description, kind) VALUES
    ('ADMIN', 'Admin', 'Runs the workspace: users, roles and the audit log.', 'STAFF'),
    ('HR_CHIEF', 'HR chief', 'Oversees hiring across recruiters and gives the final offer approval.', 'STAFF'),
    ('RECRUITER', 'Recruiter', 'Runs screening cycles and moves candidates through the pipeline.', 'STAFF'),
    ('TECHNICAL_INTERVIEWER', 'Technical interviewer', 'Holds technical interviews and writes feedback.', 'STAFF'),
    ('HR_INTERVIEWER', 'HR interviewer', 'Holds HR interviews and writes feedback.', 'STAFF'),
    ('DEPARTMENT_DIRECTOR', 'Department director', 'Sits on management interviews and approves offers that need a director.', 'STAFF'),
    ('CEO', 'CEO', 'Sits on management interviews.', 'STAFF'),
    ('CHAIRMAN', 'Chairman', 'Sits on management interviews.', 'STAFF'),
    ('CANDIDATE', 'Candidate', 'A shortlisted applicant following their application in the portal.', 'CANDIDATE');

-- The table replaces the hard-coded list on users. role_permissions keeps its check, so a candidate still can't hold a permission.
ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT fk_users_role FOREIGN KEY (role) REFERENCES roles (code);
ALTER TABLE role_permissions ADD CONSTRAINT fk_role_permissions_role FOREIGN KEY (role) REFERENCES roles (code);
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);

-- Per-role history reads the newest audit events for one target.
DROP INDEX IF EXISTS idx_audit_events_target;
CREATE INDEX idx_audit_events_target ON audit_events (target_type, target_id, occurred_at DESC);
