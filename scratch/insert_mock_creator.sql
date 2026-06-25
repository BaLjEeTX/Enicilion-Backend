-- SQL Script to insert fake creator/influencer mock data
-- Run this script using: psql -U postgres -d enicilion -f scratch/insert_mock_creator.sql

BEGIN;

-- 1. Insert Fake User (if not exists)
INSERT INTO users (
    id, full_name, email, whatsapp, password_hash, role, is_banned, referral_code, is_email_verified, email_bounced, created_at, updated_at
) VALUES (
    'e0f0e0f0-1234-5678-abcd-000000000001',
    'Speedy Creator',
    'creator@enicilion.com',
    '9999999999',
    '$2a$12$TJQciDBcKyDgkV.hkKRRieqG2alb2qrD0Mhr7ctHUl5/lSGudwYK6', -- WoodLand@786
    'influencer',
    false,
    'SPEEDY10',
    true,
    false,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- If user was already inserted, ensure they have correct role & verification status
UPDATE users
SET role = 'influencer',
    referral_code = 'SPEEDY10',
    is_email_verified = true
WHERE email = 'creator@enicilion.com';

-- Get the user id of the creator
-- (Since we might have skipped insert due to conflict, we query/update using subselects or use the fixed UUID)

-- 2. Insert Influencer Profile
INSERT INTO influencer_profiles (
    id, user_id, commission_type, commission_value, created_at, updated_at
) VALUES (
    'e0f0e0f0-1234-5678-abcd-000000000002',
    'e0f0e0f0-1234-5678-abcd-000000000001',
    'percentage',
    10.00,
    NOW(),
    NOW()
) ON CONFLICT (user_id) DO NOTHING;

-- 3. Insert Influencer Application
INSERT INTO influencer_applications (
    id, user_id, full_name, email, phone, social_links, follower_count, niche_description, payment_details, status, notes, created_at, updated_at
) VALUES (
    'e0f0e0f0-1234-5678-abcd-000000000003',
    'e0f0e0f0-1234-5678-abcd-000000000001',
    'Speedy Creator',
    'creator@enicilion.com',
    '9999999999',
    'instagram.com/speedy_creator',
    50000,
    'Automotive, Motorsports reviews and event highlights.',
    'UPI: speedy@ybl',
    'APPROVED',
    'Auto-approved mock creator for testing.',
    NOW(),
    NOW()
) ON CONFLICT DO NOTHING;

-- 4. Insert Coupon Code
INSERT INTO coupons (
    id, code, max_uses, used_count, is_active, created_at, updated_at, discount_percentage, is_influencer_coupon, influencer_profile_id
) VALUES (
    'e0f0e0f0-1234-5678-abcd-000000000004',
    'SPEEDY10',
    99999,
    15,
    true,
    NOW(),
    NOW(),
    10,
    true,
    'e0f0e0f0-1234-5678-abcd-000000000002'
) ON CONFLICT (code) DO NOTHING;

-- 5. Insert Fake Earnings Ledger (approved/pending) if spectator tickets exist
-- Check if the spectator tickets we queried are present, and insert ledger rows
INSERT INTO influencer_earnings_ledger (
    id, influencer_profile_id, payment_id, ticket_id, amount, status, created_at, updated_at
)
SELECT 
    'e0f0e0f0-1234-5678-abcd-000000000005',
    'e0f0e0f0-1234-5678-abcd-000000000002',
    t.payment_id,
    t.id,
    150.00,
    'approved',
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '2 days'
FROM spectator_tickets t
WHERE t.id = '3e4f50bf-5094-43b0-bd12-99bbfb4690b8'
ON CONFLICT DO NOTHING;

INSERT INTO influencer_earnings_ledger (
    id, influencer_profile_id, payment_id, ticket_id, amount, status, created_at, updated_at
)
SELECT 
    'e0f0e0f0-1234-5678-abcd-000000000006',
    'e0f0e0f0-1234-5678-abcd-000000000002',
    t.payment_id,
    t.id,
    150.00,
    'pending',
    NOW() - INTERVAL '1 day',
    NOW() - INTERVAL '1 day'
FROM spectator_tickets t
WHERE t.id = '0592cc2c-b287-44b8-b12e-a27920db01a0'
ON CONFLICT DO NOTHING;

COMMIT;
