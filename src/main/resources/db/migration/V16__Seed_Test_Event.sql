-- V16__Seed_Test_Event.sql
-- Seeds a default published event and ticket tiers for local testing and verification

-- 1. Create a Default Event if not exists
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

-- 2. Create Ticket Tiers if not exist
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
)
ON CONFLICT (id) DO NOTHING;
