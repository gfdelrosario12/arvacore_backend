-- Migration V10: Reconcile Accounts, Enforce Identity Isolation, and Provision Baseline Accounts

-- 1. Reconcile admin's initial password from legacy seed hash (admin_pass) to password123
--    Preserves password if it has already been updated to any other hash.
UPDATE users
SET password = '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi'
WHERE username = 'admin'
  AND password = '$2a$10$ySxvN9YT6wooSi3W.jskweLR8KD8rkjNBK1Kmzzc6sNPRgO9zQCY6';

-- 2. Clean up redundant seed accounts that were seeded as separate user rows in V4
--    farmer_m and farmer_f are supported as login aliases resolving directly to MrFarmer and MsFarmer.
DELETE FROM users
WHERE username = 'farmer_m' AND email = 'farmer_m@aquaflow.io';

DELETE FROM users
WHERE username = 'farmer_f' AND email = 'farmer_f@aquaflow.io';

DELETE FROM users
WHERE username = 'operator' AND email = 'operator@arvacore.io';

-- 3. Idempotently ensure the 6 canonical accounts exist with exact roles and email identifiers
-- admin: ROLE_ADMIN, admin@arvacore.io
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT 'admin', '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi', 'admin@arvacore.io', 'ROLE_ADMIN', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin' OR email = 'admin@arvacore.io');

-- Gladwin: ROLE_ADMIN, gladwin@aquaflow.io
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT 'Gladwin', '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi', 'gladwin@aquaflow.io', 'ROLE_ADMIN', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'Gladwin' OR email = 'gladwin@aquaflow.io');

-- Pranz: ROLE_OPERATOR, pranz@aquaflow.io
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT 'Pranz', '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi', 'pranz@aquaflow.io', 'ROLE_OPERATOR', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'Pranz' OR email = 'pranz@aquaflow.io');

-- Jenice: ROLE_SUPERVISOR, jenice@aquaflow.io
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT 'Jenice', '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi', 'jenice@aquaflow.io', 'ROLE_SUPERVISOR', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'Jenice' OR email = 'jenice@aquaflow.io');

-- MrFarmer: ROLE_USER, mr.farmer@aquaflow.io
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT 'MrFarmer', '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi', 'mr.farmer@aquaflow.io', 'ROLE_USER', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'MrFarmer' OR email = 'mr.farmer@aquaflow.io');

-- MsFarmer: ROLE_USER, ms.farmer@aquaflow.io
INSERT INTO users (username, password, email, role, enabled, created_at)
SELECT 'MsFarmer', '$2a$10$1/4QUm6r38teKATMR2O9UeeZA7Eq8m2ZVAqjcv9RmFCHAqTxEFxKi', 'ms.farmer@aquaflow.io', 'ROLE_USER', TRUE, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'MsFarmer' OR email = 'ms.farmer@aquaflow.io');

