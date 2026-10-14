-- Seed default accounts only when they do not already exist.
-- Rotate these credentials before production use.

-- 1. admin: admin_pass (ROLE_ADMIN)
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT
    'admin',
    '$2a$10$ySxvN9YT6wooSi3W.jskweLR8KD8rkjNBK1Kmzzc6sNPRgO9zQCY6',
    'admin@arvacore.io',
    'ROLE_ADMIN',
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'admin'
);

-- 2. Gladwin: password123 (ROLE_ADMIN)
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT
    'Gladwin',
    '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi',
    'gladwin@aquaflow.io',
    'ROLE_ADMIN',
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'Gladwin'
);

-- 3. Pranz: password123 (ROLE_OPERATOR)
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT
    'Pranz',
    '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi',
    'pranz@aquaflow.io',
    'ROLE_OPERATOR',
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'Pranz'
);

-- 4. Jenice: password123 (ROLE_SUPERVISOR)
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT
    'Jenice',
    '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi',
    'jenice@aquaflow.io',
    'ROLE_SUPERVISOR',
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'Jenice'
);

-- 5. MrFarmer: password123 (ROLE_USER)
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT
    'MrFarmer',
    '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi',
    'mr.farmer@aquaflow.io',
    'ROLE_USER',
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'MrFarmer'
);

-- 6. farmer_m (alias): password123 (ROLE_USER)
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT
    'farmer_m',
    '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi',
    'farmer_m@aquaflow.io',
    'ROLE_USER',
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'farmer_m'
);

-- 7. MsFarmer: password123 (ROLE_USER)
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT
    'MsFarmer',
    '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi',
    'ms.farmer@aquaflow.io',
    'ROLE_USER',
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'MsFarmer'
);

-- 8. farmer_f (alias): password123 (ROLE_USER)
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT
    'farmer_f',
    '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi',
    'farmer_f@aquaflow.io',
    'ROLE_USER',
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'farmer_f'
);

-- 9. operator default account: password123 (ROLE_OPERATOR)
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT
    'operator',
    '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi',
    'operator@arvacore.io',
    'ROLE_OPERATOR',
    TRUE,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE username = 'operator'
);
