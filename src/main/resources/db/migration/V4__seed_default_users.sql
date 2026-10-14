-- Seed default accounts only when they do not already exist.
-- Rotate these credentials before production use.

INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT
    'admin',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
    'admin@arvacore.io',
    'ROLE_ADMIN',
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'admin'
);

INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT
    'operator',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
    'operator@arvacore.io',
    'ROLE_OPERATOR',
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'operator'
);
