-- VARCHAR(255) rejected long job descriptions and audit details with "value too long".
ALTER TABLE screening_sessions ALTER COLUMN description TYPE TEXT;
ALTER TABLE audit_event ALTER COLUMN details TYPE TEXT;
