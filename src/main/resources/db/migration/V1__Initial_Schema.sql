-- V1__Initial_Schema.sql
-- PostgreSQL initial schema migration for Enicilion

-- 1. Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    whatsapp VARCHAR(30) UNIQUE,
    instagram VARCHAR(100),
    city VARCHAR(100),
    password_hash TEXT NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'user',
    is_banned BOOLEAN NOT NULL DEFAULT FALSE,
    referral_code VARCHAR(20) UNIQUE,
    referred_by VARCHAR(20),
    email_bounced BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- 2. Events Table
CREATE TABLE events (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    location VARCHAR(255) NOT NULL,
    event_date TIMESTAMPTZ NOT NULL,
    applications_open_at TIMESTAMPTZ,
    applications_close_at TIMESTAMPTZ,
    tickets_open_at TIMESTAMPTZ,
    max_drifters INT,
    max_spectators INT,
    drifter_fee NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(50) NOT NULL DEFAULT 'draft',
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_events_slug ON events(slug);
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_event_date ON events(event_date);

-- 3. Ticket Tiers Table
CREATE TABLE ticket_tiers (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    quantity INT,
    description TEXT,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_tiers_is_public ON ticket_tiers(is_public);

-- 4. Payments Table
CREATE TABLE payments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    reference_id UUID,
    reference_type VARCHAR(50),
    amount NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    provider VARCHAR(50) NOT NULL,
    provider_tx_id VARCHAR(255),
    provider_session VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    metadata JSONB,
    paid_at TIMESTAMPTZ,
    refunded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_idempotency_key ON payments(idempotency_key);
CREATE INDEX idx_payments_provider_tx_id ON payments(provider_tx_id);

-- 5. Spectator Tickets Table
CREATE TABLE spectator_tickets (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    user_id UUID NOT NULL REFERENCES users(id),
    tier_id UUID REFERENCES ticket_tiers(id),
    payment_id UUID REFERENCES payments(id),
    ticket_code VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'booked',
    booked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    checked_in_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    discount_applied INT NOT NULL DEFAULT 0,
    referral_code_used VARCHAR(20)
);

CREATE INDEX idx_spectator_tickets_event_id ON spectator_tickets(event_id);
CREATE INDEX idx_spectator_tickets_user_id ON spectator_tickets(user_id);
CREATE INDEX idx_spectator_tickets_payment_id ON spectator_tickets(payment_id);
CREATE INDEX idx_spectator_tickets_status ON spectator_tickets(status);
CREATE INDEX idx_spectator_tickets_ticket_code ON spectator_tickets(ticket_code);

-- 6. Coupons Table
CREATE TABLE coupons (
    id UUID PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    max_uses INT NOT NULL DEFAULT 1,
    used_count INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    reserved_payment_id UUID,
    reserved_at TIMESTAMPTZ,
    used_payment_id UUID,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coupons_is_active ON coupons(is_active);
CREATE INDEX idx_coupons_reserved_payment_id ON coupons(reserved_payment_id);
CREATE INDEX idx_coupons_used_payment_id ON coupons(used_payment_id);
CREATE INDEX idx_coupons_reserved_at ON coupons(reserved_at);

-- 7. Cars Table
CREATE TABLE cars (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    make VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    year SMALLINT NOT NULL,
    color VARCHAR(50),
    license_plate VARCHAR(30) UNIQUE,
    vin VARCHAR(17) UNIQUE,
    engine_spec VARCHAR(255),
    modifications TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cars_owner_id ON cars(owner_id);
CREATE INDEX idx_cars_status ON cars(status);

-- 8. Drifter Applications Table
CREATE TABLE drifter_applications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    user_id UUID NOT NULL REFERENCES users(id),
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    waitlist_position INT,
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    admin_notes TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_drifter_application_event_user UNIQUE (event_id, user_id)
);

CREATE INDEX idx_drifter_apps_event ON drifter_applications(event_id);
CREATE INDEX idx_drifter_apps_user ON drifter_applications(user_id);
CREATE INDEX idx_drifter_apps_status ON drifter_applications(status);

-- 9. Application Cars Table
CREATE TABLE application_cars (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES drifter_applications(id) ON DELETE CASCADE,
    car_id UUID NOT NULL REFERENCES cars(id) ON DELETE CASCADE,
    engine_spec_override VARCHAR(255),
    modifications_override TEXT,
    sort_order SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_application_car UNIQUE (application_id, car_id)
);

-- 10. Application Photos Table
CREATE TABLE application_photos (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES drifter_applications(id) ON DELETE CASCADE,
    original_name VARCHAR(255) NOT NULL,
    storage_path TEXT NOT NULL,
    width INT,
    height INT,
    size_bytes INT,
    mime_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    sort_order SMALLINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 11. Support Tickets Table
CREATE TABLE support_tickets (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    category VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    contact_name VARCHAR(255),
    contact_phone VARCHAR(30),
    contact_email VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'open',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_support_tickets_user_id ON support_tickets(user_id);
CREATE INDEX idx_support_tickets_created_at ON support_tickets(created_at DESC);

-- 12. Checkin Events Table
CREATE TABLE checkin_events (
    id UUID PRIMARY KEY,
    ticket_id UUID REFERENCES spectator_tickets(id),
    ticket_code VARCHAR(64) NOT NULL,
    action VARCHAR(30) NOT NULL,
    gate VARCHAR(50),
    operator_id TEXT,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_checkin_events_ticket_code ON checkin_events(ticket_code);

-- 13. POS Sales Table
CREATE TABLE pos_sales (
    id UUID PRIMARY KEY,
    tier_id UUID REFERENCES ticket_tiers(id),
    buyer_name TEXT,
    buyer_phone VARCHAR(30),
    buyer_email VARCHAR(255),
    amount NUMERIC(10, 2),
    pay_method VARCHAR(30),
    ticket_code VARCHAR(64) UNIQUE,
    issued_by TEXT,
    issued_at TIMESTAMPTZ,
    voided_at TIMESTAMPTZ,
    void_reason TEXT,
    reprint_count INT DEFAULT 0,
    last_reprinted_at TIMESTAMPTZ
);

-- 14. Ticket Notes Table
CREATE TABLE ticket_notes (
    id UUID PRIMARY KEY,
    ticket_id UUID REFERENCES spectator_tickets(id),
    ticket_code VARCHAR(64),
    note TEXT,
    created_by TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 15. Ticket Transfers Table
CREATE TABLE ticket_transfers (
    id UUID PRIMARY KEY,
    ticket_id UUID REFERENCES spectator_tickets(id),
    from_user_id UUID REFERENCES users(id),
    to_user_id UUID REFERENCES users(id),
    reason TEXT,
    created_by TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 16. Audit Logs Table
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    admin_hash TEXT,
    action TEXT,
    payload JSONB,
    sensitive BOOLEAN DEFAULT FALSE,
    status_code INT,
    ip TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 17. Event Config Store Table
CREATE TABLE event_config_store (
    id INT PRIMARY KEY,
    name VARCHAR(255),
    event_date TIMESTAMPTZ,
    venue VARCHAR(255),
    capacity INT,
    metadata JSONB,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT check_id CHECK (id = 1)
);

-- 18. Referral Codes Ext Table
CREATE TABLE referral_codes_ext (
    id UUID PRIMARY KEY,
    name VARCHAR(255),
    code VARCHAR(30) UNIQUE,
    discount INT,
    discount_percent INT,
    max_uses INT,
    used_count INT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    reserved_payment_id UUID,
    reserved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 19. Failed WA Queue Table
CREATE TABLE failed_wa_queue (
    id UUID PRIMARY KEY,
    phone VARCHAR(30),
    message TEXT,
    reason TEXT,
    attempts INT DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    status VARCHAR(30) DEFAULT 'pending',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
