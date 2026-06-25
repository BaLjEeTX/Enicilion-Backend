-- V13__Add_Email_Verification_Fields.sql
-- Adds columns to users table to support email verification

ALTER TABLE users ADD COLUMN IF NOT EXISTS is_email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verification_token VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verification_expiry TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_users_email_verification_token ON users(email_verification_token);
