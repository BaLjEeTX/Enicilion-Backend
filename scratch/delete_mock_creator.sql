-- SQL Script to delete fake creator/influencer mock data
-- Run this script using: psql -U postgres -d enicilion -f scratch/delete_mock_creator.sql

BEGIN;

-- Delete earnings ledger entries
DELETE FROM influencer_earnings_ledger WHERE id IN ('e0f0e0f0-1234-5678-abcd-000000000005', 'e0f0e0f0-1234-5678-abcd-000000000006');

-- Delete coupons
DELETE FROM coupons WHERE id = 'e0f0e0f0-1234-5678-abcd-000000000004';

-- Delete influencer profiles
DELETE FROM influencer_profiles WHERE id = 'e0f0e0f0-1234-5678-abcd-000000000002';

-- Delete influencer applications
DELETE FROM influencer_applications WHERE id = 'e0f0e0f0-1234-5678-abcd-000000000003';

-- Delete user
DELETE FROM users WHERE id = 'e0f0e0f0-1234-5678-abcd-000000000001';

COMMIT;
