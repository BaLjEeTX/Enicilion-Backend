-- V2__Seed_Data.sql
-- Seeds default testing data for the Enicilion platform

-- 1. Create a Default Admin User (Password is 'admin123')
INSERT INTO users (id, full_name, email, whatsapp, instagram, city, password_hash, role, is_banned, referral_code, created_at, updated_at)
VALUES (
    'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
    'System Administrator',
    'protocol@enicilion.com',
    '+919876543210',
    'enicilion_admin',
    'New Delhi',
    '$2a$12$KvCll6PPo3PMHcphNbvexOpb/XA0SI3PqTr.2BpZVaoztr52pceR.', -- BCrypt hash of 'admin123'
    'admin',
    FALSE,
    'ADMINCODE',
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- 2. Create a Default Event
INSERT INTO events (id, name, slug, description, location, event_date, max_drifters, max_spectators, drifter_fee, currency, status, created_by, created_at, updated_at)
VALUES (
    'e1e1e1e1-e1e1-e1e1-e1e1-e1e1e1e1e1e1',
    'Motorscape 2026',
    'motorscape-2026',
    'The premier drift and automotive exhibition showcase of the year.',
    'Buddh International Circuit, Greater Noida',
    '2026-10-15 10:00:00+05:30',
    50,
    5000,
    2500.00,
    'INR',
    'published',
    'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
    NOW(),
    NOW()
) ON CONFLICT (slug) DO NOTHING;

-- 3. Create Ticket Tiers with valid hexadecimal UUIDs (11111111-..., 22222222-..., 33333333-...)
INSERT INTO ticket_tiers (id, event_id, name, price, quantity, description, is_public, created_at)
VALUES 
(
    '11111111-1111-1111-1111-111111111111',
    'e1e1e1e1-e1e1-e1e1-e1e1-e1e1e1e1e1e1',
    'VIP Access',
    1500.00,
    100,
    'Premium seats, pit lane walk access, and complimentary refreshments.',
    TRUE,
    NOW()
),
(
    '22222222-2222-2222-2222-222222222222',
    'e1e1e1e1-e1e1-e1e1-e1e1-e1e1e1e1e1e1',
    'General Admission',
    500.00,
    1000,
    'Grandstand seating and access to the main showcase arena.',
    TRUE,
    NOW()
),
(
    '33333333-3333-3333-3333-333333333333',
    'e1e1e1e1-e1e1-e1e1-e1e1-e1e1e1e1e1e1',
    'Food and Beverage Coupon',
    200.00,
    5000,
    'Excludable Food & Beverage voucher redeemable inside the venue.',
    TRUE,
    NOW()
) ON CONFLICT DO NOTHING;

-- 4. Create a Default Coupon
INSERT INTO coupons (id, code, max_uses, used_count, is_active, created_at, updated_at)
VALUES (
    'c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1',
    'OFFER10',
    100,
    0,
    TRUE,
    NOW(),
    NOW()
) ON CONFLICT (code) DO NOTHING;
