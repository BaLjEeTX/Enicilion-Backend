-- V9__Generalized_Multi_Tenant.sql
-- Migrates the schema to be generalized and multi-tenant

-- 1. Create Organizers Table
CREATE TABLE organizers (
    id UUID PRIMARY KEY,
    business_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    logo_url VARCHAR(512),
    payout_settings JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Alter Events to link to Organizers and support dynamic registration schemas
ALTER TABLE events ADD COLUMN organizer_id UUID REFERENCES organizers(id) ON DELETE SET NULL;
ALTER TABLE events ADD COLUMN registration_schema JSONB;
CREATE INDEX idx_events_organizer_id ON events(organizer_id);

-- 3. Alter Users to support referencing an Organizer (for staff and organizer accounts)
ALTER TABLE users ADD COLUMN organizer_id UUID REFERENCES organizers(id) ON DELETE SET NULL;
CREATE INDEX idx_users_organizer_id ON users(organizer_id);

-- 4. Alter Spectator Tickets to add dynamic registration responses (JSONB)
ALTER TABLE spectator_tickets ADD COLUMN registration_responses JSONB;

-- 5. Drop legacy specialized car/drift-show tables (in correct dependency order)
DROP TABLE IF EXISTS application_photos;
DROP TABLE IF EXISTS application_cars;
DROP TABLE IF EXISTS cars;
DROP TABLE IF EXISTS drifter_applications;

-- 6. Create a generalized Event Applications table
CREATE TABLE event_applications (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    registration_responses JSONB, -- stores custom responses like car details, sizes, etc.
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    admin_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_event_application_event_user UNIQUE (event_id, user_id)
);

CREATE INDEX idx_event_applications_event ON event_applications(event_id);
CREATE INDEX idx_event_applications_user ON event_applications(user_id);
CREATE INDEX idx_event_applications_status ON event_applications(status);
