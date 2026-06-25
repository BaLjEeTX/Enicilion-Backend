-- V4__Influencer_Affiliate_System.sql
-- PostgreSQL migration script to support the Influencer Affiliate System

-- 1. Create Influencer Applications table
CREATE TABLE influencer_applications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    social_links TEXT NOT NULL,
    follower_count INT NOT NULL,
    niche_description TEXT NOT NULL,
    payment_details TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_influencer_applications_user_id ON influencer_applications(user_id);
CREATE INDEX idx_influencer_applications_status ON influencer_applications(status);

-- 2. Create Influencer Profiles table
CREATE TABLE influencer_profiles (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    commission_type VARCHAR(50) NOT NULL DEFAULT 'percentage',
    commission_value NUMERIC(10, 2) NOT NULL DEFAULT 10.00,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_influencer_profiles_user_id ON influencer_profiles(user_id);

-- 3. Modify Coupons table to add fields for influencer coupons, custom rates, dates, and event limits
ALTER TABLE coupons ADD COLUMN discount_percentage INT NOT NULL DEFAULT 10;
ALTER TABLE coupons ADD COLUMN valid_from TIMESTAMPTZ;
ALTER TABLE coupons ADD COLUMN valid_until TIMESTAMPTZ;
ALTER TABLE coupons ADD COLUMN is_influencer_coupon BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE coupons ADD COLUMN influencer_profile_id UUID REFERENCES influencer_profiles(id) ON DELETE SET NULL;
ALTER TABLE coupons ADD COLUMN applicable_event_id UUID REFERENCES events(id) ON DELETE SET NULL;

CREATE INDEX idx_coupons_influencer_profile_id ON coupons(influencer_profile_id);
CREATE INDEX idx_coupons_applicable_event_id ON coupons(applicable_event_id);

-- 4. Create Influencer Payouts table
CREATE TABLE influencer_payouts (
    id UUID PRIMARY KEY,
    influencer_profile_id UUID NOT NULL REFERENCES influencer_profiles(id) ON DELETE CASCADE,
    amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_influencer_payouts_profile_id ON influencer_payouts(influencer_profile_id);
CREATE INDEX idx_influencer_payouts_status ON influencer_payouts(status);

-- 5. Create Influencer Earnings Ledger table for real-time tracking
CREATE TABLE influencer_earnings_ledger (
    id UUID PRIMARY KEY,
    influencer_profile_id UUID NOT NULL REFERENCES influencer_profiles(id) ON DELETE CASCADE,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    ticket_id UUID NOT NULL REFERENCES spectator_tickets(id) ON DELETE CASCADE,
    amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    payout_id UUID REFERENCES influencer_payouts(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_influencer_earnings_ledger_profile_id ON influencer_earnings_ledger(influencer_profile_id);
CREATE INDEX idx_influencer_earnings_ledger_payment_id ON influencer_earnings_ledger(payment_id);
CREATE INDEX idx_influencer_earnings_ledger_ticket_id ON influencer_earnings_ledger(ticket_id);
CREATE INDEX idx_influencer_earnings_ledger_status ON influencer_earnings_ledger(status);
CREATE INDEX idx_influencer_earnings_ledger_payout_id ON influencer_earnings_ledger(payout_id);

-- 6. Create Influencer Audit Logs table
CREATE TABLE influencer_audit_logs (
    id UUID PRIMARY KEY,
    action VARCHAR(255) NOT NULL,
    actor_email VARCHAR(255) NOT NULL,
    target_id UUID,
    details TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_influencer_audit_logs_action ON influencer_audit_logs(action);
