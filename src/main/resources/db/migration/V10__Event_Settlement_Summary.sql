-- V10__Event_Settlement_Summary.sql
-- Creates the event summaries (settlements) table for post-event financial auditing

CREATE TABLE event_summaries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    gross_revenue DECIMAL(12, 2) NOT NULL,
    net_revenue DECIMAL(12, 2) NOT NULL,
    platform_fee DECIMAL(12, 2) NOT NULL,
    commission DECIMAL(12, 2) NOT NULL,
    tickets_sold INTEGER NOT NULL,
    tickets_checked_in INTEGER NOT NULL,
    settled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_event_summaries_event UNIQUE (event_id)
);

CREATE INDEX idx_event_summaries_event_id ON event_summaries(event_id);
