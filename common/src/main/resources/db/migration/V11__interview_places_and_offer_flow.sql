-- Additive only: no column or row is dropped. The one UPDATE only labels existing interviews' place.

-- Where each interview happens. Existing rows keep their text: ones with a location become OTHER, the rest HQ.
ALTER TABLE candidate_interviews ADD COLUMN IF NOT EXISTS place_type VARCHAR(16) NOT NULL DEFAULT 'HQ';
UPDATE candidate_interviews SET place_type = 'OTHER' WHERE location IS NOT NULL AND btrim(location) <> '' AND place_type = 'HQ';

-- Which interviews the recruiter chose for a candidate; NULL means planned before this existed.
ALTER TABLE candidates ADD COLUMN IF NOT EXISTS interview_plan VARCHAR(64);

-- Offer approval chain, rejection, and what happens after approval
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS requires_director_approval BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS director_approved_by VARCHAR(36) REFERENCES users(id);
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS director_approved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS rejected_by VARCHAR(36) REFERENCES users(id);
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS rejection_note VARCHAR(1000);
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS sent_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS candidate_response VARCHAR(16);
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS responded_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS joining_date DATE;
ALTER TABLE candidate_offers ADD COLUMN IF NOT EXISTS joining_notes VARCHAR(2000);

-- Top management roles: they interview; the department director also approves offers
INSERT INTO role_permissions (role, permission) VALUES
    ('DEPARTMENT_DIRECTOR', 'INTERVIEW_FEEDBACK'),
    ('DEPARTMENT_DIRECTOR', 'OFFER_APPROVE'),
    ('CEO', 'INTERVIEW_FEEDBACK'),
    ('CHAIRMAN', 'INTERVIEW_FEEDBACK')
ON CONFLICT DO NOTHING;
