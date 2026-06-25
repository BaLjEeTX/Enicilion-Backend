-- V5: Placeholder migration to close the version sequence gap between V4 and V6.
-- V4 introduced influencer/affiliate tables.
-- V6 added Apple/Google Wallet pass columns to spectator_tickets.
-- This migration creates an explicit record in the sequence so Flyway history is clean.

-- Add any missing indexes that were not covered by V4 but are needed
-- before V6 runs its schema changes.
CREATE INDEX IF NOT EXISTS idx_influencer_profiles_user_id ON influencer_profiles(user_id);
CREATE INDEX IF NOT EXISTS idx_influencer_earnings_payment_id ON influencer_earnings_ledger(payment_id);
CREATE INDEX IF NOT EXISTS idx_influencer_payouts_profile_id ON influencer_payouts(influencer_profile_id);
