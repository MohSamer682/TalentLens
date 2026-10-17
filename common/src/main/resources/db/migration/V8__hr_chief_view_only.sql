-- The HR chief oversees sessions across the company and does not run them by default.
DELETE FROM role_permissions WHERE role = 'HR_CHIEF' AND permission = 'SESSION_RUN';
